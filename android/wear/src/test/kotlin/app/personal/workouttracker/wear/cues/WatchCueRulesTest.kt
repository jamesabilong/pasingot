package app.personal.workouttracker.wear.cues

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCueRulesTest {
    @Test fun `voice stays off until opt in and every category can be disabled`() {
        val events = WatchCueKind.entries.map { kind -> WatchCueEvent("session", 1, 0, 1, kind) }
        assertTrue(events.none(WatchCuePreferences()::allows))
        val enabled = WatchCuePreferences(voiceEnabled = true)
        assertTrue(events.all(enabled::allows))
        assertFalse(enabled.copy(startBriefing = false).allows(events[0]))
        assertFalse(enabled.copy(restAnnouncements = false).allows(events[1]))
        assertFalse(enabled.copy(countdown = false).allows(events[2]))
        assertFalse(enabled.copy(completion = false).allows(events[4]))
        assertEquals(enabled, Json.decodeFromString<WatchCuePreferences>(Json.encodeToString(enabled)))
        assertFalse(Json.decodeFromString<WatchCuePreferences>(
            """{"voiceEnabled":false}""",
        ).voicePromptResolved)
    }

    @Test fun `scripts sanitize user text and cap the spoken brief`() {
        val brief = WatchCueScripts.briefing("Squat\n\u0000drop", 3, "10 reps", "12 kilograms")
        assertEquals("Squat drop. 3 sets of 10 reps at 12 kilograms.", brief)
        assertEquals("Plank. One set of 30 seconds.", WatchCueScripts.briefing("Plank", 1, "30 seconds"))
        assertTrue(WatchCueScripts.briefing("x".repeat(500), 99, "y".repeat(500)).length <= 140)
        assertEquals("Starting in five seconds.", WatchCueScripts.FIVE_SECONDS)
        assertEquals("Workout complete. Great work.", WatchCueScripts.WORKOUT_SUCCESS)
    }

    @Test fun `short rest omits cues that would overlap final five`() {
        assertNull(WatchCueScripts.rest(0, "Squat", "10 reps"))
        assertNull(WatchCueScripts.rest(5, "Squat", "10 reps"))
        assertNull(WatchCueScripts.rest(6, "Very long exercise name", "10 reps"))
        assertEquals("Next: Squat.", WatchCueScripts.rest(10, "Squat", "10 reps"))
        assertEquals("Rest for 1 minute. Up next: Squat, 10 reps.", WatchCueScripts.rest(60, "Squat", "10 reps"))
        assertEquals("Go. Set 2.", WatchCueScripts.go(null, 2))
    }

    @Test fun `terminal cue preempts warning and success keys survive recreation`() {
        val warning = WatchCueEvent("session", 8, 2, 1, WatchCueKind.FIVE_SECONDS, 10_000)
        val success = WatchCueEvent("session", 9, 2, 1, WatchCueKind.WORKOUT_SUCCESS)
        assertEquals(success, selectWatchCue(warning, success))
        assertEquals(success, selectWatchCue(success, warning))
        val first = WatchCueLedger().record(success)!!
        val restored = Json.decodeFromString<WatchCueLedger>(Json.encodeToString(first))
        assertNull(restored.record(success))
        assertTrue(restored.record(success.copy(revision = 10)) != null)
    }
}
