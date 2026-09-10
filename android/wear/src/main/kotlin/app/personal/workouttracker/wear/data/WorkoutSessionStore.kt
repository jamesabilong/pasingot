package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.WorkoutExercise

/** Storage operations needed by the session, independent of Android DataStore. */
interface WorkoutSessionStore {
    suspend fun getEntry(entryId: String): DownloadedWorkoutEntry?
    suspend fun updateSessionState(entryId: String, newState: SessionState)
    suspend fun updateExercise(entryId: String, exerciseIndex: Int, newExercise: WorkoutExercise)
}
