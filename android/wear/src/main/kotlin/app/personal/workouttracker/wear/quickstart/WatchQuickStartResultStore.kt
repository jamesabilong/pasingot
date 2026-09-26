package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.validateWorkoutCompletionSummary
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface QuickStartResultPersistence {
    suspend fun read(): String?
    /** Atomically replace the record and await durable storage. */
    suspend fun write(raw: String?)
}

@Serializable
data class FinalQuickStartResult(
    val requestId: String,
    val resultId: String,
    val outcomeRevision: Long,
    val phoneNodeId: String,
    val summary: WorkoutCompletionSummary,
)

/** The importing phone must echo the exact result identity and outcome revision. */
@Serializable
data class QuickStartResultReceipt(
    val requestId: String,
    val resultId: String,
    val outcomeRevision: Long,
    val phoneNodeId: String,
    val receivedAtMillis: Long,
)

data class ConfirmedQuickStartResult(
    val result: FinalQuickStartResult,
    val receipt: QuickStartResultReceipt,
)

sealed interface SaveQuickStartResult {
    data class Saved(val result: FinalQuickStartResult) : SaveQuickStartResult
    data class Existing(val result: FinalQuickStartResult) : SaveQuickStartResult
    data class Conflict(val result: FinalQuickStartResult) : SaveQuickStartResult
    data class AlreadyAcknowledged(val receipt: QuickStartResultReceipt) : SaveQuickStartResult
}

sealed interface AcceptQuickStartResultReceipt {
    data class Recorded(val confirmed: ConfirmedQuickStartResult) : AcceptQuickStartResultReceipt
    data class Existing(val confirmed: ConfirmedQuickStartResult) : AcceptQuickStartResultReceipt
    data object Missing : AcceptQuickStartResultReceipt
    data object Mismatch : AcceptQuickStartResultReceipt
    data class AlreadyAcknowledged(val receipt: QuickStartResultReceipt) : AcceptQuickStartResultReceipt
}

sealed interface CompactQuickStartResult {
    data object Compacted : CompactQuickStartResult
    data object AlreadyCompacted : CompactQuickStartResult
    data object MissingReceipt : CompactQuickStartResult
    data object Mismatch : CompactQuickStartResult
}

private const val RESULT_STORE_SCHEMA = 1
private val resultJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class PersistedQuickStartResult(
    val schemaVersion: Int,
    val finalResult: FinalQuickStartResult? = null,
    val receipt: QuickStartResultReceipt? = null,
)

/**
 * One durable final completion result plus its phone receipt. Transport
 * acceptance never changes this record. Compaction retains a small receipt
 * tombstone so a replayed completion cannot recreate an acknowledged result.
 * An ended-before-completion result shape remains for a later contract slice.
 */
class WatchQuickStartResultStore(private val persistence: QuickStartResultPersistence) {
    private companion object {
        val processMutex = Mutex()
    }

    suspend fun pendingResult(): FinalQuickStartResult? = processMutex.withLock {
        load()?.takeIf { it.receipt == null }?.finalResult
    }

    suspend fun confirmed(): ConfirmedQuickStartResult? = processMutex.withLock {
        val state = load() ?: return@withLock null
        val result = state.finalResult ?: return@withLock null
        val receipt = state.receipt ?: return@withLock null
        ConfirmedQuickStartResult(result, receipt)
    }

    suspend fun save(result: FinalQuickStartResult): SaveQuickStartResult = processMutex.withLock {
        require(result.isValid()) { "Invalid final Quick Start result" }
        val state = load()
        state?.finalResult?.let { existing ->
            return@withLock if (existing == result) SaveQuickStartResult.Existing(existing)
            else SaveQuickStartResult.Conflict(existing)
        }
        state?.receipt?.let { receipt ->
            if (receipt.requestId == result.requestId || receipt.resultId == result.resultId) {
                return@withLock SaveQuickStartResult.AlreadyAcknowledged(receipt)
            }
        }
        persist(PersistedQuickStartResult(RESULT_STORE_SCHEMA, finalResult = result))
        SaveQuickStartResult.Saved(result)
    }

    suspend fun acceptReceipt(
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): AcceptQuickStartResultReceipt = processMutex.withLock {
        val state = load() ?: return@withLock AcceptQuickStartResultReceipt.Missing
        val result = state.finalResult ?: return@withLock if (
            state.receipt == receipt && receipt.isValid() && observedPhoneNodeId == receipt.phoneNodeId
        ) {
            AcceptQuickStartResultReceipt.AlreadyAcknowledged(receipt)
        } else {
            AcceptQuickStartResultReceipt.Missing
        }
        if (!receipt.matches(result, observedPhoneNodeId)) {
            return@withLock AcceptQuickStartResultReceipt.Mismatch
        }
        val confirmed = ConfirmedQuickStartResult(result, receipt)
        val prior = state.receipt
        if (prior != null) {
            return@withLock if (prior == receipt) AcceptQuickStartResultReceipt.Existing(confirmed)
            else AcceptQuickStartResultReceipt.Mismatch
        }
        persist(state.copy(receipt = receipt))
        AcceptQuickStartResultReceipt.Recorded(confirmed)
    }

    suspend fun compact(confirmed: ConfirmedQuickStartResult): CompactQuickStartResult =
        processMutex.withLock {
            val state = load() ?: return@withLock CompactQuickStartResult.MissingReceipt
            if (state.receipt != confirmed.receipt) return@withLock CompactQuickStartResult.Mismatch
            if (state.finalResult == null) return@withLock CompactQuickStartResult.AlreadyCompacted
            if (state.finalResult != confirmed.result) return@withLock CompactQuickStartResult.Mismatch
            persist(PersistedQuickStartResult(RESULT_STORE_SCHEMA, receipt = confirmed.receipt))
            CompactQuickStartResult.Compacted
        }

    private suspend fun load(): PersistedQuickStartResult? {
        val raw = persistence.read() ?: return null
        val state = try {
            resultJson.decodeFromString<PersistedQuickStartResult>(raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (
            state == null || state.schemaVersion != RESULT_STORE_SCHEMA ||
            (state.finalResult == null && state.receipt == null) ||
            (state.finalResult != null && !state.finalResult.isValid()) ||
            (state.receipt != null && !state.receipt.isValid()) ||
            (state.finalResult != null && state.receipt != null &&
                !state.receipt.matches(state.finalResult, state.receipt.phoneNodeId))
        ) {
            // A future/unknown schema or damaged bytes may still contain an
            // unsynced result. Keep the raw record for diagnosis/recovery.
            throw IllegalStateException("Stored Quick Start result is unreadable")
        }
        return state
    }

    private suspend fun persist(state: PersistedQuickStartResult) {
        persistence.write(resultJson.encodeToString(state))
    }
}

private fun FinalQuickStartResult.isValid(): Boolean =
    requestId.isNotBlank() && requestId.length <= 128 &&
        resultId.isNotBlank() && resultId.length <= 128 &&
        phoneNodeId.isNotBlank() && phoneNodeId.length <= 256 &&
        listOf(requestId, resultId, phoneNodeId).none { value -> value.any(Char::isISOControl) } &&
        outcomeRevision >= 0 &&
        outcomeRevision == summary.snapshot.exercises.sumOf {
            it.completedSets.toLong() + if (it.status == ExerciseOutcomeStatus.SKIPPED) 1L else 0L
        } &&
        validateWorkoutCompletionSummary(summary) == null

private fun QuickStartResultReceipt.isValid(): Boolean =
    requestId.isNotBlank() && resultId.isNotBlank() && phoneNodeId.isNotBlank() &&
        requestId.length <= 128 && resultId.length <= 128 && phoneNodeId.length <= 256 &&
        listOf(requestId, resultId, phoneNodeId).none { value -> value.any(Char::isISOControl) } &&
        outcomeRevision >= 0 && receivedAtMillis >= 0

private fun QuickStartResultReceipt.matches(
    result: FinalQuickStartResult,
    observedPhoneNodeId: String,
): Boolean = isValid() &&
    requestId == result.requestId && resultId == result.resultId &&
    outcomeRevision == result.outcomeRevision && phoneNodeId == result.phoneNodeId &&
    observedPhoneNodeId == result.phoneNodeId
