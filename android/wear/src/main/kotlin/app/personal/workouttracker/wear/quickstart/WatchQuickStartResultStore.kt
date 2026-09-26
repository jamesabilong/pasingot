package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartResultDecodeResult
import app.personal.workouttracker.shared.quickstart.decodeQuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.isValidFinalQuickStartResult
import app.personal.workouttracker.shared.quickstart.isValidQuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.receiptMatchesQuickStartResult
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

typealias FinalQuickStartResult = app.personal.workouttracker.shared.quickstart.FinalQuickStartResult
typealias QuickStartResultReceipt = app.personal.workouttracker.shared.quickstart.QuickStartResultReceipt

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
 * One durable final completed or ended result plus its phone receipt. Transport
 * acceptance never changes this record. Compaction retains a small receipt
 * tombstone so a replayed completion cannot recreate an acknowledged result.
 */
class WatchQuickStartResultStore(private val persistence: QuickStartResultPersistence) {
    private companion object {
        val processMutex = Mutex()
    }

    suspend fun pendingResult(): FinalQuickStartResult? = processMutex.withLock {
        load()?.takeIf { it.receipt == null }?.finalResult
    }

    suspend fun storedReceipt(): QuickStartResultReceipt? = processMutex.withLock { load()?.receipt }

    suspend fun confirmed(): ConfirmedQuickStartResult? = processMutex.withLock {
        val state = load() ?: return@withLock null
        val result = state.finalResult ?: return@withLock null
        val receipt = state.receipt ?: return@withLock null
        ConfirmedQuickStartResult(result, receipt)
    }

    /** Read before freezing outcomes; legacy saved results may not yet have a freeze marker. */
    suspend fun existingFor(result: FinalQuickStartResult): SaveQuickStartResult? = processMutex.withLock {
        existingFor(load(), result)
    }

    suspend fun save(result: FinalQuickStartResult): SaveQuickStartResult = processMutex.withLock {
        require(result.isValid()) { "Invalid final Quick Start result" }
        val state = load()
        existingFor(state, result)?.let { return@withLock it }
        persist(PersistedQuickStartResult(RESULT_STORE_SCHEMA, finalResult = result))
        SaveQuickStartResult.Saved(result)
    }

    private fun existingFor(state: PersistedQuickStartResult?, result: FinalQuickStartResult): SaveQuickStartResult? {
        state?.finalResult?.let { existing ->
            return if (existing == result) SaveQuickStartResult.Existing(existing)
            else SaveQuickStartResult.Conflict(existing)
        }
        state?.receipt?.let { receipt ->
            if (receipt.requestId == result.requestId || receipt.resultId == result.resultId) {
                return SaveQuickStartResult.AlreadyAcknowledged(receipt)
            }
        }
        return null
    }

    /** For typed receipts already validated by the caller; transport uses acceptReceiptPayload. */
    internal suspend fun acceptReceipt(
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): AcceptQuickStartResultReceipt = processMutex.withLock {
        val state = load() ?: return@withLock AcceptQuickStartResultReceipt.Missing
        acceptReceipt(state, receipt, observedPhoneNodeId)
    }

    /** Native callers supply the observed Data Layer node and path, never values from the payload. */
    suspend fun acceptReceiptPayload(
        payload: String,
        path: String,
        observedPhoneNodeId: String,
        localWatchNodeId: String,
    ): AcceptQuickStartResultReceipt = processMutex.withLock {
        val state = load() ?: return@withLock AcceptQuickStartResultReceipt.Missing
        val decoded = if (state.finalResult != null) {
            decodeQuickStartResultReceipt(payload, path, observedPhoneNodeId, state.finalResult, localWatchNodeId)
        } else {
            decodeQuickStartResultReceipt(payload, path, observedPhoneNodeId, requireNotNull(state.receipt), localWatchNodeId)
        }
        when (decoded) {
            is QuickStartResultDecodeResult.Accepted -> acceptReceipt(state, decoded.value, observedPhoneNodeId)
            is QuickStartResultDecodeResult.Rejected -> AcceptQuickStartResultReceipt.Mismatch
        }
    }

    private suspend fun acceptReceipt(
        state: PersistedQuickStartResult,
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): AcceptQuickStartResultReceipt {
        val result = state.finalResult ?: return if (
            state.receipt == receipt && receipt.isValid() && observedPhoneNodeId == receipt.phoneNodeId
        ) {
            AcceptQuickStartResultReceipt.AlreadyAcknowledged(receipt)
        } else {
            AcceptQuickStartResultReceipt.Missing
        }
        if (!receipt.matches(result, observedPhoneNodeId)) {
            return AcceptQuickStartResultReceipt.Mismatch
        }
        val confirmed = ConfirmedQuickStartResult(result, receipt)
        val prior = state.receipt
        if (prior != null) {
            return if (prior == receipt) AcceptQuickStartResultReceipt.Existing(confirmed)
            else AcceptQuickStartResultReceipt.Mismatch
        }
        persist(state.copy(receipt = receipt))
        return AcceptQuickStartResultReceipt.Recorded(confirmed)
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

internal fun FinalQuickStartResult.isValid(): Boolean = isValidFinalQuickStartResult(this)

private fun QuickStartResultReceipt.isValid(): Boolean = isValidQuickStartResultReceipt(this)

private fun QuickStartResultReceipt.matches(result: FinalQuickStartResult, observedPhoneNodeId: String): Boolean =
    receiptMatchesQuickStartResult(this, result, observedPhoneNodeId)
