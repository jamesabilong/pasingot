package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.wear.session.ClearAcknowledgedOutcomeResult
import app.personal.workouttracker.wear.session.WorkoutOutcomeStore
import app.personal.workouttracker.wear.session.toProgressSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SaveCompletedQuickStartResult {
    data class Stored(val result: SaveQuickStartResult) : SaveCompletedQuickStartResult
    data object MissingPackage : SaveCompletedQuickStartResult
    data object NotStarting : SaveCompletedQuickStartResult
    data object OutcomeMismatch : SaveCompletedQuickStartResult
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
    ): SaveCompletedQuickStartResult = processMutex.withLock {
        val sessionPackage = packages.current(nowEpochMillis)
            ?: return@withLock SaveCompletedQuickStartResult.MissingPackage
        if (sessionPackage.request.requestId != result.requestId) {
            return@withLock SaveCompletedQuickStartResult.MissingPackage
        }
        if (sessionPackage.state != QuickStartPackageState.STARTING) {
            return@withLock SaveCompletedQuickStartResult.NotStarting
        }
        val state = outcomes.current() ?: return@withLock SaveCompletedQuickStartResult.OutcomeMismatch
        val snapshot = result.summary.snapshot
        if (
            snapshot.sessionId != result.requestId ||
            sessionPackage.request.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.sets) } !=
                snapshot.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.plannedSets) } ||
            state.sessionId != snapshot.sessionId || state.lastAppliedRevision != result.outcomeRevision ||
            state.toProgressSnapshot(snapshot.elapsedActiveSeconds, snapshot.estimatedDurationSeconds) != snapshot
        ) return@withLock SaveCompletedQuickStartResult.OutcomeMismatch
        SaveCompletedQuickStartResult.Stored(results.save(result))
    }

    suspend fun acknowledgeAndPrune(
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): AcknowledgeQuickStartCompletionResult = processMutex.withLock {
        val acknowledged = when (val result = results.acceptReceipt(receipt, observedPhoneNodeId)) {
            is AcceptQuickStartResultReceipt.Recorded -> result.confirmed
            is AcceptQuickStartResultReceipt.Existing -> result.confirmed
            is AcceptQuickStartResultReceipt.AlreadyAcknowledged ->
                null
            AcceptQuickStartResultReceipt.Missing ->
                return@withLock AcknowledgeQuickStartCompletionResult.MISSING_RESULT
            AcceptQuickStartResultReceipt.Mismatch ->
                return@withLock AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH
        }
        if (acknowledged != null) {
            if (outcomes.clearAcknowledged(acknowledged) == ClearAcknowledgedOutcomeResult.MISMATCH) {
                return@withLock AcknowledgeQuickStartCompletionResult.OUTCOME_MISMATCH
            }
            when (results.compact(acknowledged)) {
                CompactQuickStartResult.Compacted, CompactQuickStartResult.AlreadyCompacted -> Unit
                CompactQuickStartResult.MissingReceipt, CompactQuickStartResult.Mismatch ->
                    return@withLock AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH
            }
        }
        when (packages.releaseAcknowledged(receipt)) {
            is ReleaseAcknowledgedQuickStartResult.Released -> AcknowledgeQuickStartCompletionResult.PRUNED
            is ReleaseAcknowledgedQuickStartResult.AlreadyReleased -> AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED
            ReleaseAcknowledgedQuickStartResult.Missing,
            ReleaseAcknowledgedQuickStartResult.Mismatch,
            ReleaseAcknowledgedQuickStartResult.NotStarting ->
                AcknowledgeQuickStartCompletionResult.PACKAGE_MISMATCH
        }
    }
}
