package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
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
    suspend fun initialize(
        sessionId: String,
        title: String?,
        exercises: List<WorkoutExerciseOutcomePlan>,
    ): InitializeWorkoutOutcomeResult
    suspend fun apply(
        sessionId: String,
        transition: WorkoutOutcomeTransition,
    ): ApplyWorkoutOutcomeResult
}

sealed interface InitializeWorkoutOutcomeResult {
    data class Initialized(val state: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
    data class Existing(val state: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
    data class Conflict(val existing: WorkoutOutcomeState) : InitializeWorkoutOutcomeResult
}

sealed interface ApplyWorkoutOutcomeResult {
    data class Reduced(val result: WorkoutOutcomeTransitionResult) : ApplyWorkoutOutcomeResult
    data object Missing : ApplyWorkoutOutcomeResult
    data object SessionMismatch : ApplyWorkoutOutcomeResult
}

private const val OUTCOME_STATE_SCHEMA_VERSION = 1

@Serializable
private data class PersistedWorkoutOutcome(
    // Required on disk: a missing version must not silently select a schema.
    val schemaVersion: Int,
    val state: WorkoutOutcomeState,
)

/**
 * Retains one workout's ordered outcomes and revision, independently of the
 * date-keyed repository and Quick Start offers. No state is cached: failed or
 * cancelled writes are reconciled from storage on the next call.
 *
 * Completed outcomes remain retained. Replacement/removal after durable result
 * acknowledgement belongs to the later session/result integration, not a new
 * initialize call. This store does not imply a queued result or phone receipt.
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

    override suspend fun initialize(
        sessionId: String,
        title: String?,
        exercises: List<WorkoutExerciseOutcomePlan>,
    ): InitializeWorkoutOutcomeResult = processMutex.withLock {
        // Validate before touching storage, including before corrupt-state cleanup.
        val initial = newWorkoutOutcomeState(sessionId, title, exercises)
        val existing = loadState()
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
        val state = loadState() ?: return@withLock ApplyWorkoutOutcomeResult.Missing
        if (state.sessionId != sessionId) return@withLock ApplyWorkoutOutcomeResult.SessionMismatch
        val result = reduceWorkoutOutcome(state, transition)
        if (result.code == WorkoutOutcomeTransitionResultCode.APPLIED) {
            persist(result.state)
        }
        ApplyWorkoutOutcomeResult.Reduced(result)
    }

    private suspend fun loadState(): WorkoutOutcomeState? {
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
            persisted == null || persisted.schemaVersion != OUTCOME_STATE_SCHEMA_VERSION ||
            !persisted.state.isValidPersistedOutcome()
        ) {
            persistence.write(null)
            return null
        }
        return persisted.state
    }

    private suspend fun persist(state: WorkoutOutcomeState) {
        persistence.write(json.encodeToString(PersistedWorkoutOutcome(OUTCOME_STATE_SCHEMA_VERSION, state)))
    }
}

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
