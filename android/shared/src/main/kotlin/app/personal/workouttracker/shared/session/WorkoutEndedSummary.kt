package app.personal.workouttracker.shared.session

import kotlinx.serialization.Serializable

/** A frozen snapshot of a workout ended before every exercise was resolved. */
@Serializable
data class WorkoutEndedSummary(
    val schemaVersion: Int = WORKOUT_PROGRESS_SCHEMA_VERSION,
    val endedAtEpochMillis: Long,
    val snapshot: WorkoutProgressSnapshot,
)

fun validateWorkoutEndedSummary(
    summary: WorkoutEndedSummary,
): WorkoutSummaryValidationIssue? {
    if (summary.schemaVersion != WORKOUT_PROGRESS_SCHEMA_VERSION) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.UNSUPPORTED_SCHEMA,
            field = "schemaVersion",
        )
    }
    validateWorkoutProgressSnapshot(summary.snapshot)?.let { return it }
    if (summary.snapshot.progress.pending == 0) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.NO_PENDING_END,
            field = "progress.pending",
        )
    }
    if (summary.endedAtEpochMillis < 0) {
        return WorkoutSummaryValidationIssue(
            WorkoutSummaryValidationCode.INVALID_COMPLETION_TIME,
            field = "endedAtEpochMillis",
        )
    }
    return null
}
