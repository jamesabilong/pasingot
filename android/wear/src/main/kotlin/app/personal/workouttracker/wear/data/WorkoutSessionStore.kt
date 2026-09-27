package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WorkoutSessionEvent
import kotlinx.serialization.Serializable
import java.util.UUID

enum class SessionOutcomeActionType { SET_COMPLETED, EXERCISE_SKIPPED }

data class SessionOutcomeAction(val type: SessionOutcomeActionType, val exerciseIndex: Int)

/** Persisted with the transition so a crash cannot separate progress from history. */
@Serializable
data class WorkoutSessionEffects(
    val id: String = UUID.randomUUID().toString(),
    val log: LogEntry? = null,
    val event: WorkoutSessionEvent? = null,
)

/** Storage operations needed by the session, independent of Android DataStore. */
interface WorkoutSessionStore {
    suspend fun getEntry(entryId: String): DownloadedWorkoutEntry?

    /** Atomically compare the complete entry, write progress, and journal any history. */
    suspend fun commitSession(
        expected: DownloadedWorkoutEntry,
        updated: DownloadedWorkoutEntry,
        effects: WorkoutSessionEffects? = null,
        action: SessionOutcomeAction? = null,
    ): Boolean

    /** Replays durable history with its original payload/identity, then clears it. */
    suspend fun flushPendingEffects(sender: LogSender)
}
