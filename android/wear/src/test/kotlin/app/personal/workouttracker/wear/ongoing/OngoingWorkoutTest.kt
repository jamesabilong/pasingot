package app.personal.workouttracker.wear.ongoing

import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import org.junit.Assert.*
import org.junit.Test

class OngoingWorkoutTest {
    private fun session(status: String) = SessionState("workout", 0, 1, status)

    @Test fun `unfinished states retain return target`() {
        for (kind in listOf("session", "quick-start")) {
            for (status in listOf(SessionStatus.ACTIVE, SessionStatus.RESTING, SessionStatus.PAUSED)) {
                val target = ongoingWorkout(kind, "workout", "Workout", session(status))
                assertEquals(kind, target?.kind)
                assertEquals("workout", target?.id)
                assertEquals(status, target?.status)
            }
        }
    }

    @Test fun `terminal and unsupported states clear target`() {
        for (status in listOf(SessionStatus.COMPLETED, SessionStatus.ENDED, "ready", "unknown"))
            assertNull(ongoingWorkout("session", "workout", "Workout", session(status)))
    }

    @Test fun `missing invalid and future data do not publish`() {
        assertNull(ongoingWorkout("session", "workout", "Workout", null))
        assertNull(ongoingWorkout("settings", "workout", "Workout", session("active")))
        assertNull(ongoingWorkout("session", " ", "Workout", session("active")))
        assertNull(ongoingWorkout("session", "x".repeat(257), "Workout", session("active")))
        assertNull(ongoingWorkout("session", "workout", "Workout", session("active").copy(schemaVersion = 999)))
    }

    @Test fun `conflicts do not choose a different workout`() {
        val legacy = requireNotNull(ongoingWorkout("session", "legacy", "Workout", session("active")))
        val quick = requireNotNull(ongoingWorkout("quick-start", "quick", "Workout", session("paused")))
        assertNull(singleOngoingWorkout(emptyList()))
        assertEquals(legacy, singleOngoingWorkout(listOf(legacy)))
        assertNull(singleOngoingWorkout(listOf(legacy, quick)))
    }
}
