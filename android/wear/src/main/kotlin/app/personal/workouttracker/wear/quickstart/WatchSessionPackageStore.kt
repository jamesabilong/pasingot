package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QUICK_START_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartValidationIssue
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface QuickStartPackagePersistence {
    suspend fun read(): String?
    suspend fun write(raw: String?)
}

interface QuickStartPackageStore {
    suspend fun current(nowEpochMillis: Long): WatchSessionPackage?
    suspend fun accept(request: QuickStartRequest, receivedAtMillis: Long): AcceptQuickStartResult
    suspend fun markStarting(
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): MarkQuickStartStartingResult
}

sealed interface AcceptQuickStartResult {
    data class Accepted(val sessionPackage: WatchSessionPackage) : AcceptQuickStartResult
    data class Duplicate(val sessionPackage: WatchSessionPackage) : AcceptQuickStartResult
    data class RejectedPending(val existing: WatchSessionPackage) : AcceptQuickStartResult
    data class RejectedInvalid(val issue: QuickStartValidationIssue) : AcceptQuickStartResult
}

sealed interface MarkQuickStartStartingResult {
    data class MarkedStarting(val sessionPackage: WatchSessionPackage) : MarkQuickStartStartingResult
    data class AlreadyStarting(val sessionPackage: WatchSessionPackage) : MarkQuickStartStartingResult
    data object Missing : MarkQuickStartStartingResult
    data object Expired : MarkQuickStartStartingResult
}

@Serializable
private data class PersistedQuickStartPackage(
    val schemaVersion: Int = QUICK_START_SCHEMA_VERSION,
    val sessionPackage: WatchSessionPackage,
)

/**
 * Owns the watch's single transient Quick Start package. All read-modify-write
 * operations are serialized and persisted before success is returned.
 */
class WatchSessionPackageStore(
    private val persistence: QuickStartPackagePersistence,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : QuickStartPackageStore {
    private companion object {
        /** Services and activities may construct separate store instances. */
        val processMutex = Mutex()
    }

    override suspend fun current(nowEpochMillis: Long): WatchSessionPackage? = processMutex.withLock {
        loadCurrent(nowEpochMillis)
    }

    override suspend fun accept(
        request: QuickStartRequest,
        receivedAtMillis: Long,
    ): AcceptQuickStartResult = processMutex.withLock {
        val validated = validateQuickStartRequest(request, receivedAtMillis)
        if (validated is QuickStartValidationResult.Invalid) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(validated.issue)
        }
        val incoming = (validated as QuickStartValidationResult.Valid).sessionPackage
        val existing = loadCurrent(receivedAtMillis)
        if (existing != null) {
            return@withLock if (existing.request == incoming.request) {
                AcceptQuickStartResult.Duplicate(existing)
            } else {
                AcceptQuickStartResult.RejectedPending(existing)
            }
        }

        persist(incoming)
        AcceptQuickStartResult.Accepted(incoming)
    }

    override suspend fun markStarting(
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): MarkQuickStartStartingResult = processMutex.withLock {
        val current = loadDecoded() ?: return@withLock MarkQuickStartStartingResult.Missing
        if (current.request.requestId != requestId || current.request.revision != revision) {
            return@withLock MarkQuickStartStartingResult.Missing
        }
        if (
            current.state == QuickStartPackageState.READY &&
            nowEpochMillis > current.expiresLocallyAtMillis
        ) {
            persistence.write(null)
            return@withLock MarkQuickStartStartingResult.Expired
        }
        if (current.state == QuickStartPackageState.STARTING) {
            return@withLock MarkQuickStartStartingResult.AlreadyStarting(current)
        }

        val starting = current.copy(state = QuickStartPackageState.STARTING)
        persist(starting)
        MarkQuickStartStartingResult.MarkedStarting(starting)
    }

    private suspend fun loadCurrent(nowEpochMillis: Long): WatchSessionPackage? {
        val current = loadDecoded() ?: return null
        if (
            current.state == QuickStartPackageState.READY &&
            nowEpochMillis > current.expiresLocallyAtMillis
        ) {
            persistence.write(null)
            return null
        }
        return current
    }

    private suspend fun loadDecoded(): WatchSessionPackage? {
        val raw = persistence.read() ?: return null
        val state = try {
            json.decodeFromString<PersistedQuickStartPackage>(raw)
        } catch (_: Exception) {
            persistence.write(null)
            return null
        }
        if (
            state.schemaVersion != QUICK_START_SCHEMA_VERSION ||
            state.sessionPackage.request.schemaVersion != QUICK_START_SCHEMA_VERSION
        ) {
            persistence.write(null)
            return null
        }
        return state.sessionPackage
    }

    private suspend fun persist(sessionPackage: WatchSessionPackage) {
        persistence.write(json.encodeToString(PersistedQuickStartPackage(sessionPackage = sessionPackage)))
    }
}
