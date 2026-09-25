package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseProgressCounts

data class WorkoutProgressText(
    val compact: String,
    val accessibility: String,
)

/**
 * Compact watch progress for the future success UI. It remains derived so the
 * visible completed/total label and TalkBack text cannot drift from persisted
 * completed, skipped, and pending counts.
 */
fun ExerciseProgressCounts.toWorkoutProgressText(): WorkoutProgressText =
    WorkoutProgressText(
        compact = "$completed/$total",
        accessibility = "$completed of $total exercises completed",
    )
