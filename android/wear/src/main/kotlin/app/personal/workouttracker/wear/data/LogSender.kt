package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.shared.WatchSessionSnapshot

/**
 * Thin seam between the session screen (Prompt 4) and log delivery
 * (Prompt 8's LogSyncManager, built in a later phase). Complete Set/Skip
 * call this; Reset never does (see WorkoutRepository.resetEntry, which has
 * no [LogSender] dependency at all — structurally impossible to log a
 * reset by accident).
 */
interface LogSender {
    /** [status] should be [LogStatus.DONE] or [LogStatus.SKIPPED]. */
    suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?)

    /** Sends a workout-level session event, such as completed or manually ended. */
    suspend fun sendSessionEvent(event: WorkoutSessionEvent) = Unit

    /** Publishes live progress only when the session changes, never on timer ticks. */
    suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) = Unit
}
