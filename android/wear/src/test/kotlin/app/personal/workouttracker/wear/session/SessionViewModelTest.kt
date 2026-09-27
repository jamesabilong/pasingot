package app.personal.workouttracker.wear.session

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.SessionEventType
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WatchSessionSnapshot
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutSessionStore
import app.personal.workouttracker.wear.data.WorkoutSessionEffects
import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.wear.quickstart.GlobalSessionStartGate
import app.personal.workouttracker.wear.quickstart.LegacySessionSnapshotSource
import app.personal.workouttracker.wear.quickstart.QuickStartPackagePersistence
import app.personal.workouttracker.wear.quickstart.WatchSessionPackageStore
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

    private fun createSession(gate: GlobalSessionStartGate = defaultGate()): SessionViewModel = SessionViewModel(
        repository.entry.id,
        repository,
        sender,
        nowEpochMillis = { 1_000_000L + dispatcher.scheduler.currentTime },
        legacyStartGate = gate,
    ).also { viewModels.put("session", it) }

    private fun defaultGate(): GlobalSessionStartGate {
        val persistence = object : QuickStartPackagePersistence {
            override suspend fun read(): String? = null
            override suspend fun write(raw: String?) = Unit
        }
        return GlobalSessionStartGate(LegacySessionSnapshotSource { listOf(repository.entry) },
            WatchSessionPackageStore(persistence))
    }

    @Test fun `ready Quick Start blocks downloaded workout before session write`() = runSessionTest {
        val persistence = object : QuickStartPackagePersistence {
            var raw: String? = null
            override suspend fun read(): String? = raw
            override suspend fun write(raw: String?) { this.raw = raw }
        }
        val packages = WatchSessionPackageStore(persistence)
        val now = 1_000_000L
        packages.accept(QuickStartRequest(
            "123e4567-e89b-12d3-a456-426614174000", createdAtMillis = now,
            expiresAtMillis = now + QUICK_START_TTL_MILLIS, targetNodeId = "watch-node",
            source = QuickStartSource.SINGLE,
            exercises = listOf(QuickStartExercise("item", "squat", "Squat", 3, "10 reps", 30)),
        ), now)
        val gate = GlobalSessionStartGate(LegacySessionSnapshotSource { listOf(repository.entry) }, packages)

        val viewModel = createSession(gate)
        runCurrent()

        assertEquals("Quick Start is ready on watch", viewModel.uiState.value.blockedReason)
        assertEquals(0, repository.sessionWrites)
        assertTrue(sender.snapshots.isEmpty())
    }

    @Test fun `admitted downloaded workout persists before publishing snapshot`() = runSessionTest {
        val persistence = object : QuickStartPackagePersistence {
            override suspend fun read(): String? = null
            override suspend fun write(raw: String?) = Unit
        }
        val gate = GlobalSessionStartGate(LegacySessionSnapshotSource { listOf(repository.entry) },
            WatchSessionPackageStore(persistence))
        val orderedSender = object : LogSender by sender {
            override suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) {
                assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
                sender.sendSessionSnapshot(snapshot)
            }
        }
        val viewModel = SessionViewModel(repository.entry.id, repository, orderedSender,
            nowEpochMillis = { 1_000_000L }, legacyStartGate = gate)
        viewModels.put("session", viewModel)

        runCurrent()

        assertEquals(1, repository.sessionWrites)
        assertEquals(SessionStatus.ACTIVE, viewModel.uiState.value.session?.status)
        assertEquals(1, sender.snapshots.size)
    }

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

    @Test fun `failed transition leaves progress and history unchanged`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        val before = viewModel.uiState.value.session
        repository.failWrites = true

        viewModel.onSkip()
        runCurrent()

        assertEquals(before, viewModel.uiState.value.session)
        assertEquals(before, repository.entry.sessionState)
        assertTrue(sender.logs.isEmpty())
        assertTrue(sender.events.isEmpty())
        assertEquals(1, sender.snapshots.size)
        assertEquals("Disk unavailable", viewModel.uiState.value.error)
    }

    @Test fun `double completion cannot consume two zero-rest sets`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Squat", "10", sets = 3, rest = 0)))
        val viewModel = createSession()
        runCurrent()

        viewModel.onCompleteSet()
        viewModel.onCompleteSet()
        runCurrent()

        assertEquals(2, viewModel.uiState.value.session?.currentSet)
        assertEquals(2, repository.sessionWrites)
        assertTrue(sender.logs.isEmpty())
    }

    @Test fun `stale view cannot restore deleted workout or send its completion`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        repository.available = false

        viewModel.onSkip()
        runCurrent()

        assertEquals(SessionStatus.ACTIVE, viewModel.uiState.value.session?.status)
        assertTrue(sender.logs.isEmpty())
        assertTrue(sender.events.isEmpty())
        assertEquals(1, repository.sessionWrites)
        assertTrue(viewModel.uiState.value.error != null)
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
        var failWrites = false
        val pendingEffects = mutableListOf<WorkoutSessionEffects>()

        override suspend fun getEntry(entryId: String) = entry.takeIf { available && it.id == entryId }

        override suspend fun commitSession(expected: DownloadedWorkoutEntry,
            updated: DownloadedWorkoutEntry, effects: WorkoutSessionEffects?, action: SessionOutcomeAction?): Boolean {
            if (failWrites) error("Disk unavailable")
            if (!available || entry != expected) return false
            entry = updated
            sessionWrites += 1
            effects?.let { pendingEffects += it }
            return true
        }

        override suspend fun flushPendingEffects(sender: LogSender) {
            while (pendingEffects.isNotEmpty()) {
                val effect = pendingEffects.first()
                effect.log?.let { sender.sendEntry(it) }
                effect.event?.let { sender.sendSessionEvent(it) }
                pendingEffects.remove(effect)
            }
        }
    }

    private class RecordingLogSender : LogSender {
        val logs = mutableListOf<String>()
        val snapshots = mutableListOf<WatchSessionSnapshot>()
        val events = mutableListOf<WorkoutSessionEvent>()

        override suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?) {
            logs += status
        }

        override suspend fun sendEntry(entry: LogEntry) { logs += entry.status }

        override suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) { snapshots += snapshot }

        override suspend fun sendSessionEvent(event: WorkoutSessionEvent) { events += event }
    }
}
