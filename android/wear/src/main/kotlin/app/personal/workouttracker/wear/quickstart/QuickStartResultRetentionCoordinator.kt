package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.isValidQuickStartResultForWire
import app.personal.workouttracker.wear.session.ClearAcknowledgedOutcomeResult
import app.personal.workouttracker.wear.session.FreezeWorkoutOutcomeResult
import app.personal.workouttracker.wear.session.WorkoutOutcomeStore
import app.personal.workouttracker.wear.session.toProgressSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SaveCompletedQuickStartResult {
    data class Stored(val result: SaveQuickStartResult) : SaveCompletedQuickStartResult
    data object MissingPackage : SaveCompletedQuickStartResult
    data object NotStarting : SaveCompletedQuickStartResult
    data object OutcomeMismatch : SaveCompletedQuickStartResult
    data class FinalizedConflict(val result: FinalQuickStartResult) : SaveCompletedQuickStartResult
}

enum class AcknowledgeQuickStartCompletionResult {
    PRUNED,
    ALREADY_PRUNED,
    MISSING_RESULT,
    RECEIPT_MISMATCH,
    PACKAGE_MISMATCH,
    OUTCOME_MISMATCH,
}

/**
 * The result remains durable until the importing phone's exact receipt is
 * saved. Cleanup is replayable after failure at any individual store write:
 * result receipt, outcome removal, result compaction, package tombstone.
 * Keep the STARTING package until cleanup finishes so a new offer cannot
 * displace the package needed to recover an interrupted cleanup.
 */
class QuickStartResultRetentionCoordinator(
    private val packages: QuickStartPackageStore,
    private val outcomes: WorkoutOutcomeStore,
    private val results: WatchQuickStartResultStore,
) {
    private companion object {
        val processMutex = Mutex()
    }

    suspend fun saveCompleted(
        result: FinalQuickStartResult,
        nowEpochMillis: Long,
    ): SaveCompletedQuickStartResult {
        require(result.summary != null && result.endedSummary == null) { "Expected a completed result" }
        return saveFinal(result, nowEpochMillis)
    }

    suspend fun saveFinal(
        result: FinalQuickStartResult,
        nowEpochMillis: Long,
    ): SaveCompletedQuickStartResult = processMutex.withLock {
        require(isValidQuickStartResultForWire(result)) { "Invalid final Quick Start wire result" }
        results.existingFor(result)?.let { existing ->
            if (existing !is SaveQuickStartResult.Existing) {
                return@withLock SaveCompletedQuickStartResult.Stored(existing)
            }
        }
        val sessionPackage = packages.current(nowEpochMillis)
            ?: return@withLock SaveCompletedQuickStartResult.MissingPackage
        if (sessionPackage.request.requestId != result.requestId) {
            return@withLock SaveCompletedQuickStartResult.MissingPackage
        }
        if (sessionPackage.state != QuickStartPackageState.STARTING) {
            return@withLock SaveCompletedQuickStartResult.NotStarting
        }
        if (sessionPackage.sourcePhoneNodeId != null &&
            sessionPackage.sourcePhoneNodeId != result.phoneNodeId) {
            return@withLock SaveCompletedQuickStartResult.OutcomeMismatch
        }
        val state = outcomes.current() ?: return@withLock SaveCompletedQuickStartResult.OutcomeMismatch
        val snapshot = result.snapshot
        if (
            result.phoneNodeId == sessionPackage.request.targetNodeId ||
            snapshot.sessionId != result.requestId ||
            (sessionPackage.request.title != null && sessionPackage.request.title != snapshot.title) ||
            sessionPackage.request.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.sets) } !=
                snapshot.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.plannedSets) } ||
            state.sessionId != snapshot.sessionId || state.lastAppliedRevision != result.outcomeRevision ||
            state.toProgressSnapshot(snapshot.elapsedActiveSeconds, snapshot.estimatedDurationSeconds) != snapshot
        ) return@withLock SaveCompletedQuickStartResult.OutcomeMismatch
        when (val frozen = outcomes.freeze(result)) {
            is FreezeWorkoutOutcomeResult.Frozen, is FreezeWorkoutOutcomeResult.Existing ->
                SaveCompletedQuickStartResult.Stored(results.save(result))
            is FreezeWorkoutOutcomeResult.Conflict -> SaveCompletedQuickStartResult.FinalizedConflict(frozen.result)
            FreezeWorkoutOutcomeResult.OutcomeMismatch -> SaveCompletedQuickStartResult.OutcomeMismatch
        }
    }

    /** Recovery uses the saved IDs/timestamp instead of constructing another terminal result. */
    suspend fun resumeFinalization(nowEpochMillis: Long): SaveCompletedQuickStartResult? =
        outcomes.frozenResult()?.let { saveFinal(it, nowEpochMillis) }

    /** The stored receipt already passed sender validation; no reconnect is needed for cleanup. */
    suspend fun resumeAcknowledgedCleanup(): AcknowledgeQuickStartCompletionResult? =
        results.storedReceipt()?.let { acknowledgeAndPrune(it, it.phoneNodeId) }

    /** Typed entry point for local recovery and fixtures; native delivery uses the payload entry point. */
    internal suspend fun acknowledgeAndPrune(
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): AcknowledgeQuickStartCompletionResult = processMutex.withLock {
        prune(results.acceptReceipt(receipt, observedPhoneNodeId))
    }

    suspend fun acknowledgePayloadAndPrune(
        payload: String,
        path: String,
        observedPhoneNodeId: String,
        localWatchNodeId: String,
    ): AcknowledgeQuickStartCompletionResult = processMutex.withLock {
        prune(results.acceptReceiptPayload(payload, path, observedPhoneNodeId, localWatchNodeId))
    }

    private suspend fun prune(result: AcceptQuickStartResultReceipt): AcknowledgeQuickStartCompletionResult {
        val acknowledged = when (result) {
            is AcceptQuickStartResultReceipt.Recorded -> result.confirmed
            is AcceptQuickStartResultReceipt.Existing -> result.confirmed
            is AcceptQuickStartResultReceipt.AlreadyAcknowledged ->
                null
            AcceptQuickStartResultReceipt.Missing ->
                return AcknowledgeQuickStartCompletionResult.MISSING_RESULT
            AcceptQuickStartResultReceipt.Mismatch ->
                return AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH
        }
        val receipt = acknowledged?.receipt ?: (result as AcceptQuickStartResultReceipt.AlreadyAcknowledged).receipt
        if (acknowledged != null) {
            if (outcomes.clearAcknowledged(acknowledged) == ClearAcknowledgedOutcomeResult.MISMATCH) {
                return AcknowledgeQuickStartCompletionResult.OUTCOME_MISMATCH
            }
            when (results.compact(acknowledged)) {
                CompactQuickStartResult.Compacted, CompactQuickStartResult.AlreadyCompacted -> Unit
                CompactQuickStartResult.MissingReceipt, CompactQuickStartResult.Mismatch ->
                    return AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH
            }
        }
        return when (packages.releaseAcknowledged(receipt)) {
            is ReleaseAcknowledgedQuickStartResult.Released -> AcknowledgeQuickStartCompletionResult.PRUNED
            is ReleaseAcknowledgedQuickStartResult.AlreadyReleased -> AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED
            ReleaseAcknowledgedQuickStartResult.Missing,
            ReleaseAcknowledgedQuickStartResult.Mismatch,
            ReleaseAcknowledgedQuickStartResult.NotStarting ->
                AcknowledgeQuickStartCompletionResult.PACKAGE_MISMATCH
        }
    }
}
