package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.wear.quickstart.ConfirmedQuickStartResult
import app.personal.workouttracker.wear.quickstart.FinalQuickStartResult
import app.personal.workouttracker.wear.quickstart.isValid
import app.personal.workouttracker.wear.quickstart.QuickStartResultReceipt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface WorkoutOutcomePersistence {
    suspend fun read(): String?

    /** Atomically replace the complete record; return only after durable persistence. */
    suspend fun write(raw: String?)
}

interface WorkoutOutcomeStore {
    suspend fun current(): WorkoutOutcomeState?
    suspend fun frozenResult(): FinalQuickStartResult?
    suspend fun freeze(result: FinalQuickStartResult): FreezeWorkoutOutcomeResult
    suspend fun initialize(
        sessionId: String,
        title: String?,
        exercises: List<WorkoutExerciseOutcomePlan>,
    ): InitializeWorkoutOutcomeResult
    suspend fun apply(
        sessionId: String,
        transition: WorkoutOutcomeTransition,
    ): ApplyWorkoutOutcomeResult
    suspend fun clearAcknowledged(confirmed: ConfirmedQuickStartResult): ClearAcknowledgedOutcomeResult
}

enum class ClearAcknowledgedOutcomeResult { CLEARED, ALREADY_CLEARED, MISMATCH }

sealed interface FreezeWorkoutOutcomeResult {
    data class Frozen(val result: FinalQuickStartResult) : FreezeWorkoutOutcomeResult
    data class Existing(val result: FinalQuickStartResult) : FreezeWorkoutOutcomeResult
    data class Conflict(val result: FinalQuickStartResult) : FreezeWorkoutOutcomeResult
    data object OutcomeMismatch : FreezeWorkoutOutcomeResult
}

sealed interface InitializeWorkoutOutcomeResult {
    data class Initialized(val state: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
    data class Existing(val state: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
    data class Conflict(val existing: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
    data class AlreadyAcknowledged(val receipt: QuickStartResultReceipt) : InitializeWorkoutOutcomeResult
}

sealed interface ApplyWorkoutOutcomeResult {
    data class Reduced(val result: WorkoutOutcomeTransitionResult) : ApplyWorkoutOutcomeResult
    data object Missing : ApplyWorkoutOutcomeResult
    data object SessionMismatch : ApplyWorkoutOutcomeResult
    data class Finalized(val result: FinalQuickStartResult) : ApplyWorkoutOutcomeResult
}

private const val OUTCOME_STATE_SCHEMA_VERSION = 2

@Serializable
private data class PersistedWorkoutOutcome(
    // Required on disk: a missing version must not silently select a schema.
    val schemaVersion: Int,
    val state: WorkoutOutcomeState? = null,
    val clearedReceipt: QuickStartResultReceipt? = null,
    val clearedSessionId: String? = null,
    val frozenResult: FinalQuickStartResult? = null,
)

/**
 * Retains one workout's ordered outcomes and revision, independently of the
 * date-keyed repository and Quick Start offers. No state is cached: failed or
 * cancelled writes are reconciled from storage on the next call.
 *
 * Outcomes remain retained until an exact durable result receipt permits
 * removal. A small receipt tombstone makes cleanup retryable even after a new
 * session initializes; retry must never clear that newer session.
 */
class WatchWorkoutOutcomeStore(
    private val persistence: WorkoutOutcomePersistence,
) : WorkoutOutcomeStore {
    private companion object {
        // Activity and service instances share the read/reduce/write gate.
        val processMutex = Mutex()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    override suspend fun current(): WorkoutOutcomeState? = processMutex.withLock {
        loadState()
    }

    override suspend fun frozenResult(): FinalQuickStartResult? = processMutex.withLock {
        loadRecord()?.frozenResult
    }

    /** Persist exact terminal metadata with the outcomes before enqueueing a final result. */
    override suspend fun freeze(result: FinalQuickStartResult): FreezeWorkoutOutcomeResult = processMutex.withLock {
        require(result.isValid()) { "Invalid final Quick Start result" }
        val record = loadRecord() ?: return@withLock FreezeWorkoutOutcomeResult.OutcomeMismatch
        record.frozenResult?.let { existing ->
            return@withLock if (existing == result) FreezeWorkoutOutcomeResult.Existing(existing)
            else FreezeWorkoutOutcomeResult.Conflict(existing)
        }
        val state = record.state ?: return@withLock FreezeWorkoutOutcomeResult.OutcomeMismatch
        if (!state.matches(result)) return@withLock FreezeWorkoutOutcomeResult.OutcomeMismatch
        persistence.write(json.encodeToString(record.copy(
            schemaVersion = OUTCOME_STATE_SCHEMA_VERSION, frozenResult = result,
        )))
        FreezeWorkoutOutcomeResult.Frozen(result)
    }

    override suspend fun initialize(
        sessionId: String,
        title: String?,
        exercises: List<WorkoutExerciseOutcomePlan>,
    ): InitializeWorkoutOutcomeResult = processMutex.withLock {
        // Validate before touching storage; unreadable records fail closed.
        val initial = newWorkoutOutcomeState(sessionId, title, exercises)
        val record = loadRecord()
        if (record?.clearedSessionId == sessionId && record.clearedReceipt != null) {
            return@withLock InitializeWorkoutOutcomeResult.AlreadyAcknowledged(record.clearedReceipt)
        }
        val existing = record?.state
        if (existing != null) {
            val existingPlan = existing.exercises.map {
                WorkoutExerciseOutcomePlan(it.itemId, it.exerciseId, it.exerciseName, it.plannedSets)
            }
            return@withLock if (
                existing.sessionId == initial.sessionId && existing.title == initial.title &&
                existingPlan == exercises
            ) {
                InitializeWorkoutOutcomeResult.Existing(existing)
            } else {
                InitializeWorkoutOutcomeResult.Conflict(existing)
            }
        }
        persist(initial)
        InitializeWorkoutOutcomeResult.Initialized(initial)
    }

    override suspend fun apply(
        sessionId: String,
        transition: WorkoutOutcomeTransition,
    ): ApplyWorkoutOutcomeResult = processMutex.withLock {
        val record = loadRecord()
        val state = record?.state ?: return@withLock ApplyWorkoutOutcomeResult.Missing
        if (state.sessionId != sessionId) return@withLock ApplyWorkoutOutcomeResult.SessionMismatch
        record.frozenResult?.let { return@withLock ApplyWorkoutOutcomeResult.Finalized(it) }
        val result = reduceWorkoutOutcome(state, transition)
        if (result.code == WorkoutOutcomeTransitionResultCode.APPLIED) {
            persist(result.state)
        }
        ApplyWorkoutOutcomeResult.Reduced(result)
    }

    override suspend fun clearAcknowledged(
        confirmed: ConfirmedQuickStartResult,
    ): ClearAcknowledgedOutcomeResult = processMutex.withLock {
        val persisted = loadRecord()
        if (persisted?.clearedReceipt == confirmed.receipt) {
            return@withLock ClearAcknowledgedOutcomeResult.ALREADY_CLEARED
        }
        val state = persisted?.state ?: return@withLock ClearAcknowledgedOutcomeResult.ALREADY_CLEARED
        val result = confirmed.result
        if (!state.matches(result) || (persisted.frozenResult != null && persisted.frozenResult != result)) {
            return@withLock ClearAcknowledgedOutcomeResult.MISMATCH
        }
        persistence.write(json.encodeToString(PersistedWorkoutOutcome(
            OUTCOME_STATE_SCHEMA_VERSION, clearedReceipt = confirmed.receipt, clearedSessionId = state.sessionId,
        )))
        ClearAcknowledgedOutcomeResult.CLEARED
    }

    private suspend fun loadState(): WorkoutOutcomeState? = loadRecord()?.state

    private suspend fun loadRecord(): PersistedWorkoutOutcome? {
        // I/O failures and coroutine cancellation must propagate, never erase progress.
        val raw = persistence.read() ?: return null
        val persisted = try {
            json.decodeFromString<PersistedWorkoutOutcome>(raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (
            persisted == null || persisted.schemaVersion !in 1..OUTCOME_STATE_SCHEMA_VERSION ||
            (persisted.state == null && persisted.clearedReceipt == null) ||
            ((persisted.clearedReceipt == null) != (persisted.clearedSessionId == null)) ||
            (persisted.state != null && !persisted.state.isValidPersistedOutcome())
        ) {
            // Even undecodable bytes may hold the only copy of frozen final metadata.
            throw IllegalStateException("Stored workout outcomes are unreadable")
        }
        if (persisted.frozenResult != null &&
            (!persisted.frozenResult.isValid() || persisted.state?.matches(persisted.frozenResult) != true)
        ) throw IllegalStateException("Stored final outcomes are inconsistent")
        return persisted
    }

    private suspend fun persist(state: WorkoutOutcomeState) {
        val prior = loadRecord()
        persistence.write(json.encodeToString(PersistedWorkoutOutcome(
            OUTCOME_STATE_SCHEMA_VERSION, state, prior?.clearedReceipt, prior?.clearedSessionId, prior?.frozenResult,
        )))
    }
}

private fun WorkoutOutcomeState.matches(result: FinalQuickStartResult): Boolean =
    sessionId == result.snapshot.sessionId && lastAppliedRevision == result.outcomeRevision &&
        toProgressSnapshot(result.snapshot.elapsedActiveSeconds, result.snapshot.estimatedDurationSeconds) == result.snapshot

private fun WorkoutOutcomeState.isValidPersistedOutcome(): Boolean {
    try {
        toProgressSnapshot(elapsedActiveSeconds = 0)
    } catch (_: IllegalArgumentException) {
        return false
    }
    // Each accepted set adds one revision; a skip adds one more. This detects
    // torn/corrupt revisions even when the individual outcomes look valid.
    val appliedTransitions = exercises.sumOf { exercise ->
        exercise.completedSets.toLong() + if (exercise.status == ExerciseOutcomeStatus.SKIPPED) 1L else 0L
    }
    return lastAppliedRevision == appliedTransitions
}
