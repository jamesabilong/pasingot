package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSessionCommitTest {
    private val session = SessionState("entry", 0, 1, SessionStatus.ACTIVE)
    private val entry = DownloadedWorkoutEntry("entry", "2026-09-27", "Workout",
        listOf(WorkoutExercise("Squat", "10", sets = 1, rest = 0)), sessionState = session)
    private val effect = WorkoutSessionEffects("stable-transition",
        log = LogEntry(exercise = "Squat", status = LogStatus.DONE, timestamp = "2026-09-27T00:00:00Z"))

    @Test fun `terminal progress and exact history survive one serialized commit`() {
        val next = entry.copy(sessionState = session.copy(status = SessionStatus.COMPLETED))
        val committed = commitWorkoutSession(WorkoutStoreState(entries = listOf(entry)), entry, next, effect)!!
        val restored = Json.decodeFromString<WorkoutStoreState>(Json.encodeToString(committed))
        assertEquals(SessionStatus.COMPLETED, restored.entries.single().sessionState?.status)
        assertEquals(effect, restored.pendingSessionEffects.single())
        assertNull(commitWorkoutSession(restored, entry, next, effect))
    }

    @Test fun `reset deleted and newer session states reject a stale commit without history`() {
        val next = entry.copy(sessionState = session.copy(status = SessionStatus.COMPLETED))
        assertNull(commitWorkoutSession(WorkoutStoreState(), entry, next, effect))
        assertNull(commitWorkoutSession(WorkoutStoreState(entries = listOf(entry.copy(sessionState = null))), entry, next, effect))
        assertNull(commitWorkoutSession(WorkoutStoreState(entries = listOf(entry.copy(
            sessionState = session.copy(status = SessionStatus.PAUSED)))), entry, next, effect))
    }
}
