package app.personal.workouttracker.shared.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val WORKOUT_PROGRESS_SCHEMA_VERSION: Int = 1

@Serializable
enum class ExerciseOutcomeStatus {
    @SerialName("completed")
    COMPLETED,

    @SerialName("skipped")
    SKIPPED,

    @SerialName("pending")
    PENDING,
}

@Serializable
data class ExerciseOutcome(
    val itemId: String,
    val exerciseId: String,
    val exerciseName: String,
    val status: ExerciseOutcomeStatus,
    val completedSets: Int,
    val plannedSets: Int,
)

@Serializable
data class ExerciseProgressCounts(
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val pending: Int,
)

@Serializable
data class WorkoutProgressSnapshot(
    val schemaVersion: Int = WORKOUT_PROGRESS_SCHEMA_VERSION,
    val sessionId: String,
    val title: String? = null,
    val progress: ExerciseProgressCounts,
    val completedSets: Int,
    val plannedSets: Int,
    val elapsedActiveSeconds: Int,
    val estimatedDurationSeconds: Int? = null,
    val exercises: List<ExerciseOutcome>,
)

@Serializable
data class WorkoutCompletionSummary(
    val schemaVersion: Int = WORKOUT_PROGRESS_SCHEMA_VERSION,
    val completedAtEpochMillis: Long,
    val snapshot: WorkoutProgressSnapshot,
)

enum class WorkoutSummaryValidationCode {
    UNSUPPORTED_SCHEMA,
    INVALID_SESSION_ID,
    INVALID_TITLE,
    INVALID_EXERCISE_COUNT,
    INVALID_EXERCISE,
    DUPLICATE_ITEM_ID,
    INVALID_PROGRESS_COUNTS,
    INVALID_SET_COUNTS,
    INVALID_DURATION,
    PENDING_COMPLETION,
    NO_PENDING_END,
    INVALID_COMPLETION_TIME,
}

data class WorkoutSummaryValidationIssue(
    val code: WorkoutSummaryValidationCode,
    val itemIndex: Int? = null,
    val field: String? = null,
)

fun summarizeExerciseProgress(exercises: List<ExerciseOutcome>): ExerciseProgressCounts =
    ExerciseProgressCounts(
        total = exercises.size,
        completed = exercises.count { it.status == ExerciseOutcomeStatus.COMPLETED },
        skipped = exercises.count { it.status == ExerciseOutcomeStatus.SKIPPED },
        pending = exercises.count { it.status == ExerciseOutcomeStatus.PENDING },
    )

fun validateWorkoutProgressSnapshot(
    snapshot: WorkoutProgressSnapshot,
): WorkoutSummaryValidationIssue? {
    if (snapshot.schemaVersion != WORKOUT_PROGRESS_SCHEMA_VERSION) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.UNSUPPORTED_SCHEMA,
            field = "schemaVersion",
        )
    }
    if (!snapshot.sessionId.isBoundedText(128)) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_SESSION_ID,
            field = "sessionId",
        )
    }
    if (snapshot.title != null && !snapshot.title.isBoundedText(80)) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_TITLE,
            field = "title",
        )
    }
    if (snapshot.exercises.isEmpty() || snapshot.exercises.size > 100) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_EXERCISE_COUNT,
            field = "exercises",
        )
    }

    val itemIds = mutableSetOf<String>()
    snapshot.exercises.forEachIndexed { index, exercise ->
        if (
            !exercise.itemId.isBoundedText(128) ||
            !exercise.exerciseId.isBoundedText(128) ||
            !exercise.exerciseName.isBoundedText(120) ||
            exercise.plannedSets !in 1..99 ||
            exercise.completedSets !in 0..exercise.plannedSets ||
            (exercise.status == ExerciseOutcomeStatus.COMPLETED &&
                exercise.completedSets != exercise.plannedSets) ||
            (exercise.status != ExerciseOutcomeStatus.COMPLETED &&
                exercise.completedSets == exercise.plannedSets)
        ) {
            return WorkoutSummaryValidationIssue(
                WorkoutSummaryValidationCode.INVALID_EXERCISE,
                itemIndex = index,
            )
        }
        if (!itemIds.add(exercise.itemId)) {
            return WorkoutSummaryValidationIssue(
                WorkoutSummaryValidationCode.DUPLICATE_ITEM_ID,
                itemIndex = index,
                field = "itemId",
            )
        }
    }

    if (snapshot.progress != summarizeExerciseProgress(snapshot.exercises)) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_PROGRESS_COUNTS,
            field = "progress",
        )
    }
    val completedSets = snapshot.exercises.sumOf { it.completedSets }
    val plannedSets = snapshot.exercises.sumOf { it.plannedSets }
    if (snapshot.completedSets != completedSets || snapshot.plannedSets != plannedSets) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_SET_COUNTS,
            field = "completedSets",
        )
    }
    if (
        snapshot.elapsedActiveSeconds < 0 ||
        (snapshot.estimatedDurationSeconds != null && snapshot.estimatedDurationSeconds < 0)
    ) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_DURATION,
            field = "elapsedActiveSeconds",
        )
    }
    return null
}

fun validateWorkoutCompletionSummary(
    summary: WorkoutCompletionSummary,
): WorkoutSummaryValidationIssue? {
    if (summary.schemaVersion != WORKOUT_PROGRESS_SCHEMA_VERSION) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.UNSUPPORTED_SCHEMA,
            field = "schemaVersion",
        )
    }
    validateWorkoutProgressSnapshot(summary.snapshot)?.let { return it }
    if (summary.snapshot.progress.pending != 0) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.PENDING_COMPLETION,
            field = "progress.pending",
        )
    }
    if (summary.completedAtEpochMillis < 0) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_COMPLETION_TIME,
            field = "completedAtEpochMillis",
        )
    }
    return null
}

private fun String.isBoundedText(maxLength: Int): Boolean =
    isNotBlank() && length <= maxLength && none { it.isISOControl() }
