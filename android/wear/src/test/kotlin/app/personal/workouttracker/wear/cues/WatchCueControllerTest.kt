package app.personal.workouttracker.wear.cues

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCueControllerTest {
    @Test fun `voice is disabled until opt in but haptics remain available`() = runTest {
        val persistence = MemoryCuePersistence()
        val store = WatchCueStore(persistence)
        val output = FakeCueOutput()
        val controller = WatchCueController(store, output)
        val event = event(WatchCueKind.BRIEFING)

        assertEquals(WatchCueResult.HAPTIC_ONLY, controller.emit(event, "Briefing"))
        assertTrue(output.spoken.isEmpty())
        assertEquals(listOf(WatchCueKind.BRIEFING), output.haptics)

        store.setPreferences(WatchCuePreferences(voiceEnabled = true))
        val next = event.copy(revision = event.revision + 1)
        assertEquals(WatchCueResult.SPOKEN, controller.emit(next, "Briefing"))
        assertEquals(listOf("Briefing"), output.spoken)
        assertEquals(listOf(WatchCueKind.BRIEFING, WatchCueKind.BRIEFING), output.haptics)
    }

    @Test fun `ledger is durable before output and suppresses recovery replay`() = runTest {
        val persistence = MemoryCuePersistence()
        val event = event(WatchCueKind.WORKOUT_SUCCESS)
        val firstStore = WatchCueStore(persistence)
        firstStore.setPreferences(WatchCuePreferences(voiceEnabled = true))
        val failing = FakeCueOutput(speakSucceeds = false)

        assertEquals(
            WatchCueResult.HAPTIC_ONLY,
            WatchCueController(firstStore, failing).emit(event, WatchCueScripts.WORKOUT_SUCCESS),
        )
        assertEquals(WatchCueResult.DUPLICATE, WatchCueController(
            WatchCueStore(persistence),
            FakeCueOutput(),
        ).emit(event, WatchCueScripts.WORKOUT_SUCCESS))
    }

    @Test fun `TalkBack suppresses TTS while preserving the haptic`() = runTest {
        val store = enabledStore()
        val output = FakeCueOutput(talkBackEnabled = true)
        val result = WatchCueController(store, output).emit(event(WatchCueKind.GO), "Go.")

        assertEquals(WatchCueResult.HAPTIC_ONLY, result)
        assertTrue(output.spoken.isEmpty())
        assertEquals(listOf(WatchCueKind.GO), output.haptics)
    }

    @Test fun `lower priority cue is refused and explicit cancellation stops active speech`() = runTest {
        val store = enabledStore()
        val output = BlockingCueOutput()
        val controller = WatchCueController(store, output)
        val warning = async { controller.emit(event(WatchCueKind.FIVE_SECONDS), "Five") }
        output.started.await()

        assertEquals(
            WatchCueResult.LOWER_PRIORITY,
            controller.emit(event(WatchCueKind.REST).copy(revision = 8), "Rest"),
        )
        controller.cancel(WatchCueCancellation.START_NOW)
        output.release.complete(Unit)
        warning.await()
        assertTrue(WatchCueCancellation.START_NOW in output.cancellations)
    }

    @Test fun `stalled TTS times out to haptic fallback`() = runTest {
        val output = NeverCompletesCueOutput()
        val result = WatchCueController(enabledStore(), output).emit(event(WatchCueKind.GO), "Go")

        assertEquals(WatchCueResult.HAPTIC_ONLY, result)
        assertEquals(listOf(WatchCueKind.GO), output.haptics)
    }

    @Test fun `acknowledged session clears only its own persisted ledger`() = runTest {
        val persistence = MemoryCuePersistence()
        val store = WatchCueStore(persistence)
        store.setPreferences(WatchCuePreferences(voiceEnabled = true))
        val first = event(WatchCueKind.EXERCISE_SUCCESS)
        assertEquals(WatchCueResult.SPOKEN, WatchCueController(store, FakeCueOutput()).emit(first, "Done"))

        store.clearAcknowledgedSession("another")
        assertEquals(WatchCueResult.DUPLICATE, WatchCueController(store, FakeCueOutput()).emit(first, "Done"))
        store.clearAcknowledgedSession(first.sessionId)
        assertEquals(WatchCueResult.SPOKEN, WatchCueController(store, FakeCueOutput()).emit(first, "Done"))
    }

    private suspend fun enabledStore(): WatchCueStore = WatchCueStore(MemoryCuePersistence()).also {
        it.setPreferences(WatchCuePreferences(voiceEnabled = true))
    }

    private fun event(kind: WatchCueKind) = WatchCueEvent("session", 7, 0, 0, kind)
}

private class MemoryCuePersistence(var value: String? = null) : WatchCuePersistence {
    override suspend fun read(): String? = value
    override suspend fun write(value: String) { this.value = value }
}

private open class FakeCueOutput(
    override val talkBackEnabled: Boolean = false,
    private val speakSucceeds: Boolean = true,
) : WatchCueOutput {
    val spoken = mutableListOf<String>()
    val haptics = mutableListOf<WatchCueKind>()
    val cancellations = mutableListOf<WatchCueCancellation>()
    var closed = false

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        spoken += text
        return speakSucceeds
    }
    override fun haptic(kind: WatchCueKind) { haptics += kind }
    override fun cancel(reason: WatchCueCancellation) { cancellations += reason }
    override fun close() { closed = true }
}

private class BlockingCueOutput : FakeCueOutput() {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        started.complete(Unit)
        release.await()
        return true
    }
}

private class NeverCompletesCueOutput : FakeCueOutput() {
    override suspend fun speak(utteranceId: String, text: String): Boolean =
        CompletableDeferred<Boolean>().await()
}
