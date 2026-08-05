package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.LogStatus

/**
 * Thin seam between the session screen (Prompt 4) and log delivery
 * (Prompt 8's LogSyncManager, built in a later phase). Complete Set/Skip
 * call this; Reset never does (see WorkoutRepository.resetEntry, which has
 * no [LogSender] dependency at all — structurally impossible to log a
 * reset by accident).
 */
fun interface LogSender {
    /** [status] should be [LogStatus.DONE] or [LogStatus.SKIPPED]. */
    suspend fun send(exercise: String, status: String)
}
