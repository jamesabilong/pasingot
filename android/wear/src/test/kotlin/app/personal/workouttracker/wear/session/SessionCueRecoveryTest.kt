package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.wear.cues.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCueRecoveryTest {
    @Test fun `resumed rest is interrupted by Go and recovered ledger prevents either replay`() = runTest {
        val disk = object : WatchCuePersistence {
            var raw: String? = null
            override suspend fun read() = raw
            override suspend fun write(value: String) { raw = value }
        }
        val store = WatchCueStore(disk)
        store.setPreferences(WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true))
        val output = PendingSpeechOutput()
        val emitter = ControllerSessionCueEmitter(WatchCueController(store, output))
        val entry = DownloadedWorkoutEntry("recovered-rest", "2026-10-05", "Workout",
            listOf(WorkoutExercise("Squat", "8", 2, 20)))
        val rest = SessionState(workoutEntryId = entry.id, exerciseIndex = 0, currentSet = 2,
            status = SessionStatus.RESTING, restUntilEpochMillis = 120_000,
            restIntervalId = "retained-rest")
        val active = rest.copy(status = SessionStatus.ACTIVE, restUntilEpochMillis = null,
            restIntervalId = null, restFinalCountdownStarted = false)
        val resting = async { emitter.restStarted(entry, rest, 20, 0) }
        runCurrent()
        val going = async { emitter.go(entry, active, 120_000) }
        runCurrent()
        assertEquals(listOf("Rest for 20 seconds.", "Go. Set 2."), output.scripts)
        assertEquals(listOf(WatchCueCancellation.START_NOW), output.cancellations)
        val keys = store.state().ledger.deliveredKeys
        assertEquals(listOf("recovered-rest|120000|0|2|REST|120000",
            "recovered-rest|120000|0|2|GO|120000"), keys)
        output.pending.getValue(keys[0]).complete(false)
        resting.await()
        assertFalse(going.isCompleted)
        // Old timer callbacks carrying current ACTIVE state have no deadline to announce.
        emitter.fiveSeconds(entry, active)
        emitter.restStarted(entry, active, 20, 0)
        assertEquals(keys, store.state().ledger.deliveredKeys)
        assertEquals(listOf(WatchCueKind.REST, WatchCueKind.GO), output.haptics)
        output.pending.getValue(keys[1]).complete(true)
        going.await()
        emitter.close()

        val replayOutput = PendingSpeechOutput()
        val recovered = ControllerSessionCueEmitter(WatchCueController(WatchCueStore(disk), replayOutput))
        recovered.restStarted(entry, rest, 20, 0)
        recovered.go(entry, active, 120_000)
        assertTrue(replayOutput.scripts.isEmpty())
        assertTrue(replayOutput.haptics.isEmpty())
        assertEquals(keys, WatchCueStore(disk).state().ledger.deliveredKeys)
        assertEquals(WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true),
            WatchCueStore(disk).state().preferences)
        recovered.close()
    }
}

private class PendingSpeechOutput : WatchCueOutput {
    override val talkBackEnabled = false
    val scripts = mutableListOf<String>()
    val haptics = mutableListOf<WatchCueKind>()
    val cancellations = mutableListOf<WatchCueCancellation>()
    val pending = linkedMapOf<String, CompletableDeferred<Boolean>>()
    override suspend fun speak(utteranceId: String, text: String): Boolean {
        scripts += text
        return CompletableDeferred<Boolean>().also { pending[utteranceId] = it }.await()
    }
    override fun haptic(kind: WatchCueKind) { haptics += kind }
    override fun cancel(reason: WatchCueCancellation) { cancellations += reason }
    override fun close() = Unit
}
