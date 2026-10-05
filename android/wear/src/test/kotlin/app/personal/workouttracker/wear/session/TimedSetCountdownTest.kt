package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TimedSetCountdownTest {
    @Test fun `explicit durations support seconds minutes fractions and compounds`() {
        for ((text, millis) in mapOf("30 sec" to 30_000L, "2 min" to 120_000L,
            "1.5 minutes" to 90_000L, "1 min 30 sec" to 90_000L, " 45S " to 45_000L))
            assertEquals(text, millis, timedSetDurationMillis(text))
        for (text in listOf("8", "8 reps", "30-45 sec", "30 sec per side", "0 sec", "999999 min", "30:00", ""))
            assertNull(text, timedSetDurationMillis(text))
    }

    @Test fun `old sessions decode with absent timer fields`() {
        val restored = Json.decodeFromString<SessionState>(
            """{"workoutEntryId":"hold","exerciseIndex":0,"currentSet":1,"status":"active"}""")
        assertNull(restored.timedSetDeadlineEpochMillis)
        assertNull(restored.pausedTimedSetRemainingMillis)
    }

    @Test fun `pause and resume retain exact subsecond remainder and next set resets`() {
        val entry = DownloadedWorkoutEntry("hold", "2026-10-05", "Hold",
            listOf(WorkoutExercise("Plank", "30 sec", 2, 10)))
        val initial = SessionState("hold", 0, 1, SessionStatus.ACTIVE)
        val started = updateTimedSetCountdown(entry, null, initial, 1_000)
        val paused = updateTimedSetCountdown(entry, started, started.copy(status = SessionStatus.PAUSED), 2_250)
        assertEquals(28_750L, paused.pausedTimedSetRemainingMillis)
        assertNull(paused.timedSetDeadlineEpochMillis)
        val resumed = updateTimedSetCountdown(entry, paused, paused.copy(status = SessionStatus.ACTIVE), 80_000)
        assertEquals(108_750L, resumed.timedSetDeadlineEpochMillis)
        val next = updateTimedSetCountdown(entry, resumed, resumed.copy(currentSet = 2), 90_000)
        assertEquals(120_000L, next.timedSetDeadlineEpochMillis)
        val resting = updateTimedSetCountdown(entry, next, next.copy(status = SessionStatus.RESTING), 100_000)
        assertNull(resting.timedSetDeadlineEpochMillis)
        assertNull(resting.pausedTimedSetRemainingMillis)
    }
}
