package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.shared.quickstart.isValidQuickStartResultForWire
import app.personal.workouttracker.shared.quickstart.isValidQuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.receiptMatchesQuickStartResult
import app.personal.workouttracker.shared.quickstart.validateQuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.WorkoutEndedSummary
import app.personal.workouttracker.wear.session.WorkoutExerciseOutcomePlan
import app.personal.workouttracker.wear.session.WorkoutOutcomeState
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransition
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionResultCode
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import app.personal.workouttracker.wear.session.newWorkoutOutcomeState
import app.personal.workouttracker.wear.session.reduceWorkoutOutcome
import app.personal.workouttracker.wear.session.toCompletionSummaryOrNull
import app.personal.workouttracker.wear.session.toProgressSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface QuickStartRuntimePersistence {
    suspend fun read(): String?

    /** Replace the whole record atomically and return only after durable persistence. */
    suspend fun write(raw: String)
}

/** Session engine input/output and outcomes always move in the same durable write. */
@Serializable
data class QuickStartRuntimeState(
    val sessionPackage: WatchSessionPackage,
    val session: SessionState,
    val outcomes: WorkoutOutcomeState,
    val runtimeRevision: Long,
    val startedAcknowledgement: QuickStartAcknowledgement,
    val finalResult: FinalQuickStartResult? = null,
)

/** The engine supplies its next SessionState; this store does not run a second engine. */
data class QuickStartRuntimeAction(
    val type: WorkoutOutcomeTransitionType,
    val exerciseIndex: Int,
)

sealed interface InitializeQuickStartRuntimeResult {
    data class Initialized(val state: QuickStartRuntimeState) : InitializeQuickStartRuntimeResult
    data class Existing(val state: QuickStartRuntimeState) : InitializeQuickStartRuntimeResult
    data class Conflict(val state: QuickStartRuntimeState) : InitializeQuickStartRuntimeResult
    data class AlreadyAcknowledged(val receipt: QuickStartResultReceipt) : InitializeQuickStartRuntimeResult
    data class Invalid(val reason: String) : InitializeQuickStartRuntimeResult
}

sealed interface ApplyQuickStartRuntimeResult {
    data class Applied(val state: QuickStartRuntimeState) : ApplyQuickStartRuntimeResult
    data class Unchanged(val state: QuickStartRuntimeState) : ApplyQuickStartRuntimeResult
    data class Stale(val state: QuickStartRuntimeState) : ApplyQuickStartRuntimeResult
    data class Finalized(val state: QuickStartRuntimeState) : ApplyQuickStartRuntimeResult
    data class Invalid(val reason: String) : ApplyQuickStartRuntimeResult
    data object Missing : ApplyQuickStartRuntimeResult
}

enum class ClearQuickStartRuntimeResult { CLEARED, ALREADY_CLEARED, MISSING, MISMATCH }

@Serializable
private data class PersistedQuickStartRuntime(
    // Required: absent/future versions must not discard an unsynced result.
    val schemaVersion: Int,
    val runtime: QuickStartRuntimeState? = null,
    val clearedReceipt: QuickStartResultReceipt? = null,
)

/**
 * One transient runtime, independent of the date-keyed downloads. Never caches
 * state: a cancelled call or lost response is reconciled by the next read.
 * The start gate owns READY -> STARTING; callers must pass its returned package.
 * ACK transport and result transport run only after this store returns.
 */
class QuickStartRuntimeStore(private val persistence: QuickStartRuntimePersistence) {
    private companion object {
        const val SCHEMA_VERSION = 1
        val processMutex = Mutex()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    suspend fun current(): QuickStartRuntimeState? = processMutex.withLock { load()?.runtime }

    suspend fun initialize(
        sessionPackage: WatchSessionPackage,
        initialSession: SessionState,
        nowEpochMillis: Long,
    ): InitializeQuickStartRuntimeResult = processMutex.withLock {
        if (!sessionPackage.isValidStartingPackage() || nowEpochMillis < 0) {
            return@withLock InitializeQuickStartRuntimeResult.Invalid("Invalid starting package")
        }
        val record = load()
        record?.runtime?.let { existing ->
            return@withLock if (existing.sessionPackage == sessionPackage) {
                // Recovery keeps the original state and immutable Started receipt.
                InitializeQuickStartRuntimeResult.Existing(existing)
            } else InitializeQuickStartRuntimeResult.Conflict(existing)
        }
        record?.clearedReceipt?.takeIf { it.requestId == sessionPackage.request.requestId }?.let {
            return@withLock InitializeQuickStartRuntimeResult.AlreadyAcknowledged(it)
        }
        val request = sessionPackage.request
        val outcomes = newWorkoutOutcomeState(request.requestId, request.title, request.exercises.map {
            WorkoutExerciseOutcomePlan(it.itemId, it.exerciseId, it.exerciseName, it.sets)
        })
        if (initialSession.status != SessionStatus.ACTIVE || initialSession.exerciseIndex != 0 ||
            initialSession.currentSet != 1 || initialSession.accumulatedElapsedMillis != 0L ||
            initialSession.elapsedStartedAtEpochMillis != nowEpochMillis ||
            !initialSession.matches(outcomes)
        ) return@withLock InitializeQuickStartRuntimeResult.Invalid("Invalid initial session")
        val state = QuickStartRuntimeState(
            sessionPackage = sessionPackage,
            session = initialSession,
            outcomes = outcomes,
            runtimeRevision = 0,
            startedAcknowledgement = QuickStartAcknowledgement(
                requestId = request.requestId,
                revision = request.revision + 1,
                targetNodeId = request.targetNodeId,
                status = QuickStartStatus.STARTED,
                watchUpdatedAtMillis = nowEpochMillis,
            ),
        )
        persist(PersistedQuickStartRuntime(SCHEMA_VERSION, runtime = state))
        InitializeQuickStartRuntimeResult.Initialized(state)
    }

    /**
     * Compare-and-set includes a revision because pause/resume can return to an
     * equal SessionState. Stale replays never consume another completed set.
     * An accepted terminal transition freezes its result in this same write.
     */
    suspend fun transition(
        requestId: String,
        expectedRevision: Long,
        expectedSession: SessionState,
        nextSession: SessionState,
        action: QuickStartRuntimeAction? = null,
        nowEpochMillis: Long,
    ): ApplyQuickStartRuntimeResult = processMutex.withLock {
        val record = load() ?: return@withLock ApplyQuickStartRuntimeResult.Missing
        val current = record.runtime ?: return@withLock ApplyQuickStartRuntimeResult.Missing
        if (requestId != current.sessionPackage.request.requestId ||
            expectedRevision != current.runtimeRevision || expectedSession != current.session
        ) return@withLock ApplyQuickStartRuntimeResult.Stale(current)
        if (current.finalResult != null) return@withLock ApplyQuickStartRuntimeResult.Finalized(current)
        if (nowEpochMillis < 0 || current.runtimeRevision == Long.MAX_VALUE ||
            nextSession.accumulatedElapsedMillis < current.session.accumulatedElapsedMillis
        ) return@withLock ApplyQuickStartRuntimeResult.Invalid("Invalid session transition")

        var outcomes = current.outcomes
        if (action != null) {
            if (current.session.status != SessionStatus.ACTIVE ||
                action.exerciseIndex != current.session.exerciseIndex
            ) return@withLock ApplyQuickStartRuntimeResult.Invalid("Action does not match active exercise")
            val exercise = outcomes.exercises.getOrNull(action.exerciseIndex)
                ?: return@withLock ApplyQuickStartRuntimeResult.Invalid("Unknown exercise")
            val reduced = reduceWorkoutOutcome(outcomes, WorkoutOutcomeTransition(
                outcomes.lastAppliedRevision + 1, exercise.itemId, action.type,
            ))
            if (reduced.code != WorkoutOutcomeTransitionResultCode.APPLIED) {
                return@withLock ApplyQuickStartRuntimeResult.Invalid("Outcome transition refused")
            }
            outcomes = reduced.state
        }
        if (!nextSession.matches(outcomes) ||
            (action == null && (nextSession.exerciseIndex != current.session.exerciseIndex ||
                nextSession.currentSet != current.session.currentSet))
        ) return@withLock ApplyQuickStartRuntimeResult.Invalid("Session and outcomes disagree")
        if (nextSession == current.session && outcomes == current.outcomes) {
            return@withLock ApplyQuickStartRuntimeResult.Unchanged(current)
        }
        val finalResult = when (nextSession.status) {
            SessionStatus.COMPLETED, SessionStatus.ENDED -> createFinalResult(
                current.sessionPackage, nextSession, outcomes, nowEpochMillis,
            )
            else -> null
        }
        val updated = current.copy(
            session = nextSession,
            outcomes = outcomes,
            runtimeRevision = current.runtimeRevision + 1,
            finalResult = finalResult,
        )
        persist(record.copy(runtime = updated))
        ApplyQuickStartRuntimeResult.Applied(updated)
    }

    /**
     * Call after the importing phone's exact receipt is durably retained.
     * A compact tombstone rejects stale initialization without erasing another
     * session. Result compaction and package release follow this operation.
     */
    suspend fun clearAcknowledged(
        receipt: QuickStartResultReceipt,
        observedPhoneNodeId: String,
    ): ClearQuickStartRuntimeResult = processMutex.withLock {
        val record = load() ?: return@withLock ClearQuickStartRuntimeResult.MISSING
        if (record.clearedReceipt == receipt && observedPhoneNodeId == receipt.phoneNodeId) {
            return@withLock ClearQuickStartRuntimeResult.ALREADY_CLEARED
        }
        val finalResult = record.runtime?.finalResult
            ?: return@withLock ClearQuickStartRuntimeResult.MISMATCH
        if (!receiptMatchesQuickStartResult(receipt, finalResult, observedPhoneNodeId)) {
            return@withLock ClearQuickStartRuntimeResult.MISMATCH
        }
        persist(PersistedQuickStartRuntime(SCHEMA_VERSION, clearedReceipt = receipt))
        ClearQuickStartRuntimeResult.CLEARED
    }

    private suspend fun load(): PersistedQuickStartRuntime? {
        val raw = persistence.read() ?: return null
        val record = try { json.decodeFromString<PersistedQuickStartRuntime>(raw) }
        catch (_: SerializationException) { null }
        catch (_: IllegalArgumentException) { null }
        if (record == null || record.schemaVersion != SCHEMA_VERSION ||
            ((record.runtime == null) == (record.clearedReceipt == null)) ||
            (record.runtime != null && !record.runtime.isValid()) ||
            (record.clearedReceipt != null && !isValidQuickStartResultReceipt(record.clearedReceipt))
        ) throw IllegalStateException("Stored Quick Start runtime is unreadable")
        return record
    }

    private suspend fun persist(record: PersistedQuickStartRuntime) {
        persistence.write(json.encodeToString(record))
    }
}

private fun WatchSessionPackage.isValidStartingPackage(): Boolean {
    val validated = validateQuickStartRequest(request, receivedAtMillis)
    val source = sourcePhoneNodeId ?: return false
    return state == QuickStartPackageState.STARTING && receivedAtMillis >= 0 &&
        validated is QuickStartValidationResult.Valid &&
        validated.sessionPackage.expiresLocallyAtMillis == expiresLocallyAtMillis &&
        source.isNotBlank() && source.length <= 256 && source.none(Char::isISOControl) &&
        source != request.targetNodeId && request.revision < Long.MAX_VALUE
}

/** Validate plan/order and cursor invariants without calculating engine transitions. */
private fun SessionState.matches(outcomes: WorkoutOutcomeState): Boolean {
    val elapsedStartedAt = elapsedStartedAtEpochMillis
    val restUntil = restUntilEpochMillis
    val pausedRest = pausedRestRemainingSeconds
    if (schemaVersion != CURRENT_SCHEMA_VERSION || workoutEntryId != outcomes.sessionId ||
        exerciseIndex !in outcomes.exercises.indices || accumulatedElapsedMillis < 0 ||
        (elapsedStartedAt != null && elapsedStartedAt < 0) ||
        currentSet !in 1..outcomes.exercises[exerciseIndex].plannedSets
    ) return false
    when (status) {
        SessionStatus.ACTIVE -> if (restUntilEpochMillis != null || pausedRestRemainingSeconds != null ||
            elapsedStartedAtEpochMillis == null) return false
        SessionStatus.RESTING -> if (restUntil == null || restUntil < 0 ||
            pausedRestRemainingSeconds != null || elapsedStartedAtEpochMillis == null) return false
        SessionStatus.PAUSED -> if (restUntilEpochMillis != null || elapsedStartedAtEpochMillis != null ||
            (pausedRest != null && pausedRest <= 0)) return false
        SessionStatus.COMPLETED, SessionStatus.ENDED -> if (restUntilEpochMillis != null ||
            pausedRestRemainingSeconds != null || elapsedStartedAtEpochMillis != null) return false
        else -> return false
    }
    val firstPending = outcomes.exercises.indexOfFirst { it.status == ExerciseOutcomeStatus.PENDING }
    if (status == SessionStatus.COMPLETED) {
        return firstPending == -1 && exerciseIndex == outcomes.exercises.lastIndex
    }
    return firstPending == exerciseIndex &&
        currentSet == outcomes.exercises[exerciseIndex].completedSets + 1 &&
        outcomes.exercises.drop(exerciseIndex + 1).all {
            it.status == ExerciseOutcomeStatus.PENDING && it.completedSets == 0
        }
}

private fun QuickStartRuntimeState.isValid(): Boolean {
    if (!sessionPackage.isValidStartingPackage() || runtimeRevision < 0 ||
        runtimeRevision < outcomes.lastAppliedRevision || !session.matches(outcomes)
    ) return false
    val request = sessionPackage.request
    if (outcomes.sessionId != request.requestId || outcomes.title != request.title ||
        request.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.sets) } !=
        outcomes.exercises.map { listOf(it.itemId, it.exerciseId, it.exerciseName, it.plannedSets) } ||
        outcomes.lastAppliedRevision != outcomes.exercises.sumOf {
            it.completedSets.toLong() + if (it.status == ExerciseOutcomeStatus.SKIPPED) 1L else 0L
        }
    ) return false
    try { outcomes.toProgressSnapshot(0) } catch (_: IllegalArgumentException) { return false }
    if (startedAcknowledgement.status != QuickStartStatus.STARTED ||
        startedAcknowledgement.revision != request.revision + 1 ||
        validateQuickStartAcknowledgement(startedAcknowledgement, request.requestId, request.targetNodeId) != null
    ) return false
    val terminal = session.status == SessionStatus.COMPLETED || session.status == SessionStatus.ENDED
    if (terminal != (finalResult != null)) return false
    return finalResult == null || (isValidQuickStartResultForWire(finalResult) &&
        finalResult == createFinalResult(sessionPackage, session, outcomes,
            finalResult.summary?.completedAtEpochMillis ?: requireNotNull(finalResult.endedSummary).endedAtEpochMillis))
}

private fun createFinalResult(
    sessionPackage: WatchSessionPackage,
    session: SessionState,
    outcomes: WorkoutOutcomeState,
    nowEpochMillis: Long,
): FinalQuickStartResult {
    // Ceiling conversion cannot overflow Long or wrap a very long workout to negative Int.
    val elapsedSeconds = (session.accumulatedElapsedMillis / 1_000L +
        if (session.accumulatedElapsedMillis % 1_000L > 0) 1L else 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return FinalQuickStartResult(
        requestId = sessionPackage.request.requestId,
        resultId = "runtime-${sessionPackage.request.requestId}",
        outcomeRevision = outcomes.lastAppliedRevision,
        phoneNodeId = requireNotNull(sessionPackage.sourcePhoneNodeId),
        summary = if (session.status == SessionStatus.COMPLETED)
            requireNotNull(outcomes.toCompletionSummaryOrNull(nowEpochMillis, elapsedSeconds)) else null,
        endedSummary = if (session.status == SessionStatus.ENDED)
            WorkoutEndedSummary(endedAtEpochMillis = nowEpochMillis,
                snapshot = outcomes.toProgressSnapshot(elapsedSeconds)) else null,
    )
}
