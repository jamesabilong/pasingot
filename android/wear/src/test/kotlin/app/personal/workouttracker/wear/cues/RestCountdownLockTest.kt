package app.personal.workouttracker.wear.cues

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCountdownLockTest {
    private val initial = RestCountdownLock("rest-1", 10_000)

    @Test fun `extension wins at six seconds and warning moves to new deadline`() {
        val extended = (initial.extend(4_000, 10) as RestCountdownChange.Changed).state
        assertEquals(20_000L, extended.deadlineEpochMillis)
        assertTrue(extended.tick(14_999) is RestCountdownChange.Refused)
        val warning = extended.tick(15_000) as RestCountdownChange.Changed
        assertTrue(warning.crossedFiveSeconds)
        assertTrue(warning.state.finalCountdownStarted)
        assertFalse((warning.state.tick(15_001) as RestCountdownChange.Refused).state.finished)
    }

    @Test fun `threshold wins at five seconds and extensions leave deadline unchanged`() {
        assertTrue(initial.extend(5_000, 5) is RestCountdownChange.Refused)
        val locked = (initial.tick(5_000) as RestCountdownChange.Changed).state
        assertTrue(locked.extend(4_000, 30) is RestCountdownChange.Refused)
        assertEquals(10_000L, locked.deadlineEpochMillis)
        assertTrue(locked.tick(5_001) is RestCountdownChange.Refused)
    }

    @Test fun `pause recovery retains lock and start now terminates once`() {
        val locked = (initial.tick(5_000) as RestCountdownChange.Changed).state
        val stored = Json.decodeFromString<RestCountdownLock>(Json.encodeToString(locked.pause(6_000)))
        assertTrue(stored.finalCountdownStarted)
        val resumed = stored.resume(100_000)
        assertTrue(resumed.extend(100_000, 10) is RestCountdownChange.Refused)
        assertTrue(resumed.tick(104_000) is RestCountdownChange.Changed)
        val started = resumed.startNow()
        assertTrue(started.finished)
        assertTrue(started.tick(104_000) is RestCountdownChange.Refused)
    }

    @Test fun `new rest interval starts unlocked`() {
        val old = (initial.tick(5_000) as RestCountdownChange.Changed).state
        assertTrue(old.finalCountdownStarted)
        val next = RestCountdownLock("rest-2", 30_000)
        assertTrue(next.extend(20_000, 5) is RestCountdownChange.Changed)
    }

    @Test fun `pausing after an unseen threshold never replays warning on return`() {
        val resumed = initial.pause(5_500).resume(100_000)
        assertTrue(resumed.finalCountdownStarted)
        assertTrue(resumed.tick(100_000) is RestCountdownChange.Refused)
        assertTrue(resumed.extend(100_000, 5) is RestCountdownChange.Refused)
    }
}
