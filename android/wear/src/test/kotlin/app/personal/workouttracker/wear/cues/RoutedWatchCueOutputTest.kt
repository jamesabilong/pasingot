package app.personal.workouttracker.wear.cues

import app.personal.workouttracker.shared.voice.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoutedWatchCueOutputTest {
    private class Local : WatchCueOutput {
        override val talkBackEnabled = false
        var spoken = 0
        override suspend fun speak(utteranceId: String, text: String): Boolean { spoken++; return true }
        override fun haptic(kind: WatchCueKind) = Unit
        override fun cancel(reason: WatchCueCancellation) = Unit
        override fun close() = Unit
    }
    private class Transport : PhoneVoiceCueTransport {
        var nodeResult: suspend () -> String? = { "phone" }
        var speechResult: suspend () -> PhoneVoiceCueResponse? = { PhoneVoiceCueResponse(PhoneVoiceStatus.SPOKEN) }
        var probe = PhoneVoiceCueResponse(PhoneVoiceStatus.READY, "ticket")
        var canceled = 0
        override suspend fun node() = nodeResult()
        override suspend fun exchange(node: String, request: PhoneVoiceCueRequest) =
            if (request.operation == PhoneVoiceOperation.PROBE) probe else speechResult()
        override fun cancel(node: String, request: PhoneVoiceCueRequest) { canceled++ }
    }
    @Test fun unavailablePhoneFallsBackButUnknownSpeechResultDoesNotReplay() = runTest {
        val local = Local(); val transport = Transport()
        val output = RoutedWatchCueOutput("session", local, transport)
        transport.probe = PhoneVoiceCueResponse(PhoneVoiceStatus.DECLINED)
        assertTrue(output.speak("session|1", "Rest")); assertEquals(1, local.spoken)
        transport.probe = PhoneVoiceCueResponse(PhoneVoiceStatus.READY, "ticket")
        transport.speechResult = { null }
        assertFalse(output.speak("session|2", "Go")); assertEquals(1, local.spoken)
    }
    @Test fun cancellationWhileFindingNodeNeverStartsLateLocalSpeech() = runTest {
        val local = Local(); val transport = Transport(); val node = CompletableDeferred<String?>()
        transport.nodeResult = { node.await() }
        val output = RoutedWatchCueOutput("session", local, transport)
        val speech = async { output.speak("session|1", "Obsolete") }; runCurrent()
        output.cancel(WatchCueCancellation.PAUSE); node.complete(null)
        assertFalse(speech.await()); assertEquals(0, local.spoken)
    }
    @Test fun delayedDeclineAfterCancellationCannotFallBackOrStopNewCue() = runTest {
        val local = Local(); val transport = Transport(); val reply = CompletableDeferred<PhoneVoiceCueResponse?>()
        transport.speechResult = { reply.await() }
        val output = RoutedWatchCueOutput("session", local, transport)
        val old = async { output.speak("session|1", "Old rest") }; runCurrent()
        output.cancel(WatchCueCancellation.START_NOW)
        transport.speechResult = { PhoneVoiceCueResponse(PhoneVoiceStatus.SPOKEN) }
        assertTrue(output.speak("session|2", "Go"))
        reply.complete(PhoneVoiceCueResponse(PhoneVoiceStatus.DECLINED))
        assertFalse(old.await()); assertEquals(0, local.spoken); assertEquals(1, transport.canceled)
    }
    @Test fun disconnectedDiscoveryStillAllowsStandaloneSpeech() = runTest {
        val local = Local(); val transport = Transport()
        transport.nodeResult = { throw IllegalStateException("disconnected") }
        assertTrue(RoutedWatchCueOutput("session", local, transport).speak("session|1", "Go"))
        assertEquals(1, local.spoken)
    }
}
