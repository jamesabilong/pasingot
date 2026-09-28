package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
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

class SessionCueEmitterTest {
    @Test fun `rest warning and new exercise Go use exact scripts`() = runTest {
        val store = WatchCueStore(SessionCueMemoryPersistence())
        store.setPreferences(WatchCuePreferences(voiceEnabled = true))
        val output = RecordingSessionCueOutput()
        val emitter = ControllerSessionCueEmitter(WatchCueController(store, output))
        val entry = DownloadedWorkoutEntry(
            id = "session",
            date = "2026-09-28",
            label = "Workout",
            exercises = listOf(
                WorkoutExercise("Squat", "10 reps", 1, 60),
                WorkoutExercise("Hold", "30 sec", 1, 0),
            ),
        )
        val resting = SessionState(
            workoutEntryId = entry.id,
            exerciseIndex = 1,
            currentSet = 1,
            status = SessionStatus.RESTING,
            restUntilEpochMillis = 60_000,
            restIntervalId = "rest-1",
            restFinalCountdownStarted = true,
            elapsedStartedAtEpochMillis = 1,
        )

        emitter.restStarted(entry, resting, 60, previousExerciseIndex = 0)
        emitter.fiveSeconds(entry, resting)
        emitter.go(entry, resting.copy(
            status = SessionStatus.ACTIVE,
            restUntilEpochMillis = null,
            restIntervalId = null,
            restFinalCountdownStarted = false,
        ), 60_000)

        assertEquals(
            listOf(
                "Rest for 1 minute. Up next: Hold, 30 sec.",
                "Starting in five seconds.",
                "Go. Hold.",
            ),
            output.spoken,
        )
        assertEquals(
            listOf(WatchCueKind.REST, WatchCueKind.FIVE_SECONDS, WatchCueKind.GO),
            output.haptics,
        )
        assertEquals(listOf(WatchCueCancellation.START_NOW), output.cancellations)
        emitter.close()
        assertEquals(true, output.closed)
    }
}

private class SessionCueMemoryPersistence : WatchCuePersistence {
    private var raw: String? = null
    override suspend fun read(): String? = raw
    override suspend fun write(value: String) { raw = value }
}

private class RecordingSessionCueOutput : WatchCueOutput {
    override val talkBackEnabled = false
    val spoken = mutableListOf<String>()
    val haptics = mutableListOf<WatchCueKind>()
    val cancellations = mutableListOf<WatchCueCancellation>()
    var closed = false
    override suspend fun speak(utteranceId: String, text: String): Boolean {
        spoken += text
        return true
    }
    override fun haptic(kind: WatchCueKind) { haptics += kind }
    override fun cancel(reason: WatchCueCancellation) { cancellations += reason }
    override fun close() { closed = true }
}
