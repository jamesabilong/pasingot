package app.personal.workouttracker.quickstart

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QUICK_START_CLOCK_SKEW_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.reconcileQuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.validateQuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.quickStartPhoneDataStore by preferencesDataStore(name = "quick_start_phone")
private val recordKey = stringPreferencesKey("requests_json")
private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class PhoneQuickStartRecord(
    val request: QuickStartRequest,
    val transportAcceptedAtMillis: Long? = null,
    val acknowledgement: QuickStartAcknowledgement? = null,
)

@Serializable
private data class PhoneQuickStartRecords(val schemaVersion: Int = 1,
    val records: List<PhoneQuickStartRecord> = emptyList())

interface QuickStartPhonePersistence {
    suspend fun read(): String?
    suspend fun write(raw: String)
}

class DataStoreQuickStartPhonePersistence(context: Context) : QuickStartPhonePersistence {
    private val store = context.applicationContext.quickStartPhoneDataStore
    override suspend fun read(): String? = store.data.first()[recordKey]
    override suspend fun write(raw: String) {
        store.edit { it[recordKey] = raw }
    }
}

enum class PhoneAcknowledgementResult { RECORDED, DUPLICATE, UNKNOWN_REQUEST, WRONG_NODE, INVALID, STALE }

/**
 * Keep original requests for future result import, including when a newer offer
 * starts. No unacknowledged record is evicted to make room for another send.
 */
class QuickStartPhoneStore(private val persistence: QuickStartPhonePersistence) {
    private companion object { val processMutex = Mutex(); const val MAX_REQUESTS = 64 }

    suspend fun current(requestId: String): PhoneQuickStartRecord? = processMutex.withLock {
        load().records.firstOrNull { it.request.requestId == requestId }
    }

    /** Last created offer, including its durable watch state after process death. */
    suspend fun latest(): PhoneQuickStartRecord? = processMutex.withLock {
        load().records.lastOrNull()
    }

    suspend fun saveRequest(request: QuickStartRequest, nowEpochMillis: Long): PhoneQuickStartRecord =
        processMutex.withLock {
            require(validateQuickStartRequest(request, nowEpochMillis) is QuickStartValidationResult.Valid)
            val state = load()
            state.records.firstOrNull { it.request.requestId == request.requestId }?.let { existing ->
                require(existing.request == request) { "Request ID already belongs to a different offer" }
                return@withLock existing
            }
            val liveOffer = state.records.any { record ->
                nowEpochMillis <= record.request.expiresAtMillis + QUICK_START_CLOCK_SKEW_MILLIS &&
                    (record.acknowledgement?.status == QuickStartStatus.READY ||
                        (record.transportAcceptedAtMillis != null && record.acknowledgement == null))
            }
            require(!liveOffer) { "A Quick Start request is already pending on the watch" }
            require(state.records.size < MAX_REQUESTS) { "Quick Start request history is full" }
            val record = PhoneQuickStartRecord(request)
            persist(state.copy(records = state.records + record))
            record
        }

    suspend fun markTransportAccepted(requestId: String, acceptedAtMillis: Long): PhoneQuickStartRecord =
        processMutex.withLock {
            require(acceptedAtMillis >= 0)
            val state = load()
            val index = state.records.indexOfFirst { it.request.requestId == requestId }
            require(index >= 0) { "Unknown Quick Start request" }
            val current = state.records[index]
            if (current.transportAcceptedAtMillis != null) return@withLock current
            val updated = current.copy(transportAcceptedAtMillis = acceptedAtMillis)
            persist(state.copy(records = state.records.toMutableList().apply { this[index] = updated }))
            updated
        }

    suspend fun acceptAcknowledgement(
        payload: String,
        path: String,
        observedWatchNodeId: String,
    ): PhoneAcknowledgementResult = processMutex.withLock {
        if (payload.length > 8_192) return@withLock PhoneAcknowledgementResult.INVALID
        val acknowledgement = try {
            json.decodeFromString<QuickStartAcknowledgement>(payload)
        } catch (_: SerializationException) {
            return@withLock PhoneAcknowledgementResult.INVALID
        } catch (_: IllegalArgumentException) {
            return@withLock PhoneAcknowledgementResult.INVALID
        }
        val state = load()
        val index = state.records.indexOfFirst { it.request.requestId == acknowledgement.requestId }
        if (index < 0) return@withLock PhoneAcknowledgementResult.UNKNOWN_REQUEST
        val current = state.records[index]
        if (observedWatchNodeId != current.request.targetNodeId ||
            acknowledgement.targetNodeId != observedWatchNodeId) return@withLock PhoneAcknowledgementResult.WRONG_NODE
        if (path != QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + acknowledgement.requestId ||
            validateQuickStartAcknowledgement(acknowledgement,
                current.request.requestId, current.request.targetNodeId) != null) {
            return@withLock PhoneAcknowledgementResult.INVALID
        }
        val prior = current.acknowledgement
        if (prior == acknowledgement) return@withLock PhoneAcknowledgementResult.DUPLICATE
        if (prior != null && reconcileQuickStartAcknowledgement(prior, acknowledgement) != acknowledgement) {
            return@withLock PhoneAcknowledgementResult.STALE
        }
        if (prior?.status == QuickStartStatus.STARTED && acknowledgement.status != QuickStartStatus.STARTED) {
            return@withLock PhoneAcknowledgementResult.STALE
        }
        val updated = current.copy(acknowledgement = acknowledgement)
        persist(state.copy(records = state.records.toMutableList().apply { this[index] = updated }))
        PhoneAcknowledgementResult.RECORDED
    }

    private suspend fun load(): PhoneQuickStartRecords {
        val raw = persistence.read() ?: return PhoneQuickStartRecords()
        val value = try { json.decodeFromString<PhoneQuickStartRecords>(raw) }
        catch (_: SerializationException) { throw IllegalStateException("Unreadable Quick Start phone requests") }
        catch (_: IllegalArgumentException) { throw IllegalStateException("Unreadable Quick Start phone requests") }
        if (value.schemaVersion != 1 || value.records.size > MAX_REQUESTS ||
            value.records.map { it.request.requestId }.distinct().size != value.records.size ||
            value.records.any { record ->
                validateQuickStartRequest(record.request, record.request.createdAtMillis) !is QuickStartValidationResult.Valid ||
                    (record.transportAcceptedAtMillis != null && record.transportAcceptedAtMillis < 0) ||
                    (record.acknowledgement != null &&
                        validateQuickStartAcknowledgement(record.acknowledgement,
                            record.request.requestId, record.request.targetNodeId) != null)
            }
        ) throw IllegalStateException("Invalid Quick Start phone requests")
        return value
    }

    private suspend fun persist(value: PhoneQuickStartRecords) {
        persistence.write(json.encodeToString(value))
    }
}
