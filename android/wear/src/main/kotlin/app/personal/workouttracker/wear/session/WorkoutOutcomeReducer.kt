package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseOutcome
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.WorkoutProgressSnapshot
import app.personal.workouttracker.shared.session.summarizeExerciseProgress
import app.personal.workouttracker.shared.session.validateWorkoutCompletionSummary
import app.personal.workouttracker.shared.session.validateWorkoutProgressSnapshot

data class WorkoutExerciseOutcomePlan(
    val itemId: String,
    val exerciseId: String,
    val exerciseName: String,
    val plannedSets: Int,
)

/**
 * Pure progress state for one watch workout.
 *
 * The next persistence adapter must save [exercises] and [lastAppliedRevision]
 * atomically. Saving either field alone would allow a replayed transition to
 * double-count a set after process recreation.
 */
data class WorkoutOutcomeState(
    val sessionId: String,
    val title: String? = null,
    val exercises: List<ExerciseOutcome>,
    val lastAppliedRevision: Long = 0,
)

enum class WorkoutOutcomeTransitionType {
    SET_COMPLETED,
    EXERCISE_SKIPPED,
}

/** Revisions are contiguous and session-local; rejected transitions consume no revision. */
data class WorkoutOutcomeTransition(
    val revision: Long,
    val itemId: String,
    val type: WorkoutOutcomeTransitionType,
)

enum class WorkoutOutcomeTransitionResultCode {
    APPLIED,
    DUPLICATE,
    REVISION_GAP,
    UNKNOWN_ITEM,
    ALREADY_RESOLVED,
}

data class WorkoutOutcomeTransitionResult(
    val state: WorkoutOutcomeState,
    val code: WorkoutOutcomeTransitionResultCode,
)

fun newWorkoutOutcomeState(
    sessionId: String,
    title: String?,
    exercises: List<WorkoutExerciseOutcomePlan>,
): WorkoutOutcomeState = WorkoutOutcomeState(
    sessionId = sessionId,
    title = title,
    exercises = exercises.map { exercise ->
        ExerciseOutcome(
            itemId = exercise.itemId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.exerciseName,
            status = ExerciseOutcomeStatus.PENDING,
            completedSets = 0,
            plannedSets = exercise.plannedSets,
        )
    },
).also { state ->
    state.toProgressSnapshot(elapsedActiveSeconds = 0)
}

/**
 * Applies exactly one ordered transition without I/O. Callers may safely retry
 * the last persisted revision; duplicates return the unchanged state.
 */
fun reduceWorkoutOutcome(
    state: WorkoutOutcomeState,
    transition: WorkoutOutcomeTransition,
): WorkoutOutcomeTransitionResult {
    if (transition.revision <= state.lastAppliedRevision) {
        return WorkoutOutcomeTransitionResult(state, WorkoutOutcomeTransitionResultCode.DUPLICATE)
    }
    if (transition.revision != state.lastAppliedRevision + 1) {
        return WorkoutOutcomeTransitionResult(state, WorkoutOutcomeTransitionResultCode.REVISION_GAP)
    }

    val index = state.exercises.indexOfFirst { it.itemId == transition.itemId }
    if (index < 0) {
        return WorkoutOutcomeTransitionResult(state, WorkoutOutcomeTransitionResultCode.UNKNOWN_ITEM)
    }
    val current = state.exercises[index]
    if (current.status != ExerciseOutcomeStatus.PENDING) {
        return WorkoutOutcomeTransitionResult(state, WorkoutOutcomeTransitionResultCode.ALREADY_RESOLVED)
    }

    val updated = when (transition.type) {
        WorkoutOutcomeTransitionType.SET_COMPLETED -> {
            val completedSets = current.completedSets + 1
            current.copy(
                completedSets = completedSets,
                status = if (completedSets == current.plannedSets) {
                    ExerciseOutcomeStatus.COMPLETED
                } else {
                    ExerciseOutcomeStatus.PENDING
                },
            )
        }

        WorkoutOutcomeTransitionType.EXERCISE_SKIPPED ->
            current.copy(status = ExerciseOutcomeStatus.SKIPPED)
    }

    val exercises = state.exercises.toMutableList().apply { this[index] = updated }
    return WorkoutOutcomeTransitionResult(
        state = state.copy(
            exercises = exercises,
            lastAppliedRevision = transition.revision,
        ),
        code = WorkoutOutcomeTransitionResultCode.APPLIED,
    )
}

fun WorkoutOutcomeState.toProgressSnapshot(
    elapsedActiveSeconds: Int,
    estimatedDurationSeconds: Int? = null,
): WorkoutProgressSnapshot = WorkoutProgressSnapshot(
    sessionId = sessionId,
    title = title,
    progress = summarizeExerciseProgress(exercises),
    completedSets = exercises.sumOf { it.completedSets },
    plannedSets = exercises.sumOf { it.plannedSets },
    elapsedActiveSeconds = elapsedActiveSeconds,
    estimatedDurationSeconds = estimatedDurationSeconds,
    exercises = exercises,
).also { snapshot ->
    require(validateWorkoutProgressSnapshot(snapshot) == null) {
        "Workout outcome state produced an invalid progress snapshot"
    }
}

/** Returns a final summary only after every planned exercise has been resolved. */
fun WorkoutOutcomeState.toCompletionSummaryOrNull(
    completedAtEpochMillis: Long,
    elapsedActiveSeconds: Int,
    estimatedDurationSeconds: Int? = null,
): WorkoutCompletionSummary? {
    val snapshot = toProgressSnapshot(elapsedActiveSeconds, estimatedDurationSeconds)
    if (snapshot.progress.pending != 0) return null

    return WorkoutCompletionSummary(
        completedAtEpochMillis = completedAtEpochMillis,
        snapshot = snapshot,
    ).takeIf { validateWorkoutCompletionSummary(it) == null }
}
