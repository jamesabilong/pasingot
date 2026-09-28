package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.wear.cues.WatchCueCancellation
import app.personal.workouttracker.wear.cues.WatchCueController
import app.personal.workouttracker.wear.cues.WatchCueKind
import app.personal.workouttracker.wear.cues.WatchCueOutput
import app.personal.workouttracker.wear.cues.WatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCuePreferences
import app.personal.workouttracker.wear.cues.WatchCueStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickStartCountdownCuesTest {
    @Test fun `countdown emits exact briefing warning and Go scripts once`() = runTest {
        val store = WatchCueStore(CountdownCueMemoryPersistence())
        store.setPreferences(WatchCuePreferences(voiceEnabled = true))
        val output = RecordingCountdownCueOutput()
        val cues = ControllerQuickStartCountdownCues(WatchCueController(store, output))
        val sessionPackage = runtimePackage()

        cues.begin(sessionPackage, 123_000)
        cues.go(sessionPackage, 123_000)

        assertEquals(
            listOf("Squat. 2 sets of 10.", "Starting in five seconds.", "Go."),
            output.spoken,
        )
        assertEquals(
            listOf(WatchCueKind.BRIEFING, WatchCueKind.FIVE_SECONDS, WatchCueKind.GO),
            output.haptics,
        )
        assertEquals(3, output.ids.toSet().size)

        cues.begin(sessionPackage, 123_000)
        cues.go(sessionPackage, 123_000)
        assertEquals(3, output.spoken.size)
    }
}

private class CountdownCueMemoryPersistence : WatchCuePersistence {
    private var raw: String? = null
    override suspend fun read(): String? = raw
    override suspend fun write(value: String) { raw = value }
}

private class RecordingCountdownCueOutput : WatchCueOutput {
    override val talkBackEnabled: Boolean = false
    val ids = mutableListOf<String>()
    val spoken = mutableListOf<String>()
    val haptics = mutableListOf<WatchCueKind>()

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        ids += utteranceId
        spoken += text
        return true
    }
    override fun haptic(kind: WatchCueKind) { haptics += kind }
    override fun cancel(reason: WatchCueCancellation) = Unit
    override fun close() = Unit
}
