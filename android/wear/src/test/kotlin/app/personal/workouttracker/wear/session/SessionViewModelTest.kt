package app.personal.workouttracker.wear.session

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.SessionEventType
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WatchSessionSnapshot
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val repository = FakeSessionStore()
    private val sender = RecordingLogSender()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun runSessionTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { viewModels.clear() }
    }

    private fun createSession(): SessionViewModel = SessionViewModel(
        repository.entry.id,
        repository,
        sender,
        nowEpochMillis = { 1_000_000L + dispatcher.scheduler.currentTime },
    ).also { viewModels.put("session", it) }

    @Test fun `starting immediately persists and publishes active session`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()

        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(1, sender.snapshots.size)
        assertEquals(SessionStatus.ACTIVE, sender.snapshots.single().status)
        assertTrue(sender.logs.isEmpty())
        assertTrue(sender.events.isEmpty())
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test fun `missing workout does not create or tick a phantom session`() = runSessionTest {
        repository.available = false
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(null, viewModel.uiState.value.session)
        assertEquals(0, viewModel.uiState.value.elapsedSeconds)
        assertEquals(0, repository.sessionWrites)
        assertTrue(sender.snapshots.isEmpty())
    }

    @Test fun `hidden screen stops ticking and catches elapsed time on return without sending each second`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.elapsedSeconds)

        viewModel.onScreenVisibilityChanged(false)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.elapsedSeconds)
        assertEquals(1, repository.sessionWrites)
        assertEquals(1, sender.snapshots.size)

        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(62, viewModel.uiState.value.elapsedSeconds)
        assertEquals(1, sender.snapshots.size)
    }

    @Test fun `hidden rest does no work and resolves its deadline when screen returns`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        assertEquals(30, viewModel.uiState.value.restRemainingSeconds)

        viewModel.onScreenVisibilityChanged(false)
        viewModel.saveOnExitIfActive()
        advanceTimeBy(90_000)
        runCurrent()
        assertEquals(SessionStatus.RESTING, viewModel.uiState.value.session?.status)
        assertEquals(30, viewModel.uiState.value.restRemainingSeconds)
        assertEquals(2, repository.sessionWrites)
        assertEquals(2, sender.snapshots.size)

        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, viewModel.uiState.value.session?.status)
        assertEquals(2, viewModel.uiState.value.session?.currentSet)
        assertEquals(0, viewModel.uiState.value.restRemainingSeconds)
        assertEquals(90, viewModel.uiState.value.elapsedSeconds)
        assertEquals(3, sender.snapshots.size)
    }

    @Test fun `closing active workout pauses once and leaves no elapsed ticker`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.onScreenVisibilityChanged(false)
        viewModel.saveOnExitIfActive()
        viewModel.saveOnExitIfActive()
        runCurrent()
        advanceTimeBy(60_000)
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()

        assertEquals(SessionStatus.PAUSED, viewModel.uiState.value.session?.status)
        assertEquals(5, viewModel.uiState.value.elapsedSeconds)
        assertEquals(2, repository.sessionWrites)
        assertEquals(listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED), sender.snapshots.map { it.status })
    }

    @Test fun `pausing rest freezes remaining time until explicit resume`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        advanceTimeBy(10_000)
        viewModel.onPause()
        runCurrent()
        viewModel.onScreenVisibilityChanged(false)
        advanceTimeBy(60_000)
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(20, viewModel.uiState.value.restRemainingSeconds)

        viewModel.onResume()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(15, viewModel.uiState.value.restRemainingSeconds)
        assertEquals(SessionStatus.RESTING, viewModel.uiState.value.session?.status)
    }

    @Test fun `final skip publishes completion once and preserves skipped exercise log`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        viewModel.onSkip()
        viewModel.onSkip()
        runCurrent()

        assertEquals(SessionStatus.COMPLETED, viewModel.uiState.value.session?.status)
        assertEquals(listOf(LogStatus.SKIPPED), sender.logs)
        assertEquals(SessionEventType.COMPLETED, sender.events.single().eventType)
        assertEquals(listOf(SessionStatus.ACTIVE, SessionStatus.COMPLETED), sender.snapshots.map { it.status })
    }

    private class FakeSessionStore : WorkoutSessionStore {
        var available = true
        var entry = DownloadedWorkoutEntry(
            id = "2026-09-10",
            date = "2026-09-10",
            label = "Today",
            exercises = listOf(WorkoutExercise("Squat", "10", sets = 2, rest = 30)),
        )
        var sessionWrites = 0

        override suspend fun getEntry(entryId: String) = entry.takeIf { available && it.id == entryId }

        override suspend fun updateSessionState(entryId: String, newState: SessionState) {
            entry = entry.copy(sessionState = newState)
            sessionWrites += 1
        }

        override suspend fun updateExercise(entryId: String, exerciseIndex: Int, newExercise: WorkoutExercise) {
            entry = entry.copy(exercises = entry.exercises.toMutableList().apply { this[exerciseIndex] = newExercise })
        }
    }

    private class RecordingLogSender : LogSender {
        val logs = mutableListOf<String>()
        val snapshots = mutableListOf<WatchSessionSnapshot>()
        val events = mutableListOf<WorkoutSessionEvent>()

        override suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?) {
            logs += status
        }

        override suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) { snapshots += snapshot }

        override suspend fun sendSessionEvent(event: WorkoutSessionEvent) { events += event }
    }
}
