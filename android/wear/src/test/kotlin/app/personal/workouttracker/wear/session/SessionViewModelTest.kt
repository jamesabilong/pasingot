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
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.wear.quickstart.GlobalSessionStartGate
import app.personal.workouttracker.wear.quickstart.LegacySessionSnapshotSource
import app.personal.workouttracker.wear.quickstart.QuickStartPackagePersistence
import app.personal.workouttracker.wear.quickstart.WatchSessionPackageStore
import app.personal.workouttracker.wear.cues.WatchCueCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val repository = FakeSessionStore()
    private val sender = RecordingLogSender()
    private val cues = RecordingSessionCueEmitter()
    private var restSequence = 0

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
        cueEmitter = cues,
        newRestIntervalId = { "rest-${++restSequence}" },
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
        val resting = repository.entry.sessionState

        viewModel.onScreenVisibilityChanged(false)
        viewModel.saveOnExitIfActive()
        advanceTimeBy(90_000)
        runCurrent()
        assertEquals(SessionStatus.RESTING, viewModel.uiState.value.session?.status)
        assertEquals(30, viewModel.uiState.value.restRemainingSeconds)
        assertEquals(2, repository.sessionWrites)
        assertEquals(2, sender.snapshots.size)
        assertEquals(resting, repository.entry.sessionState)
        assertEquals(listOf("rest:30", "cancel:NAVIGATION"), cues.events)

        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, viewModel.uiState.value.session?.status)
        assertEquals(2, viewModel.uiState.value.session?.currentSet)
        assertEquals(0, viewModel.uiState.value.restRemainingSeconds)
        assertEquals(90, viewModel.uiState.value.elapsedSeconds)
        assertEquals(3, sender.snapshots.size)
        assertEquals(listOf("rest:30", "cancel:NAVIGATION", "cancel:START_NOW", "go"), cues.events)
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(1, cues.events.count { it == "go" })
        assertFalse(cues.events.contains("five"))
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

    @Test fun `rest extension is accepted at six seconds and locked at five`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()

        advanceTimeBy(24_000)
        runCurrent()
        assertEquals(6, viewModel.uiState.value.restRemainingSeconds)
        assertTrue(viewModel.uiState.value.canExtendRest)
        viewModel.onAddRestSeconds(10)
        runCurrent()
        val extendedDeadline = viewModel.uiState.value.session?.restUntilEpochMillis
        assertEquals(16, viewModel.uiState.value.restRemainingSeconds)

        advanceTimeBy(11_000)
        runCurrent()
        assertEquals(5, viewModel.uiState.value.restRemainingSeconds)
        assertTrue(viewModel.uiState.value.session?.restFinalCountdownStarted == true)
        assertFalse(viewModel.uiState.value.canExtendRest)
        val writesAtLock = repository.sessionWrites
        viewModel.onAddRestSeconds(30)
        runCurrent()
        assertEquals(extendedDeadline, viewModel.uiState.value.session?.restUntilEpochMillis)
        assertEquals(writesAtLock, repository.sessionWrites)
    }

    @Test fun `rest warning and Go follow durable lock and deadline transitions`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        assertEquals(listOf("rest:30"), cues.events)
        assertTrue(repository.entry.sessionState?.restIntervalId != null)

        advanceTimeBy(25_000)
        runCurrent()
        assertTrue(repository.entry.sessionState?.restFinalCountdownStarted == true)
        assertEquals(listOf("rest:30", "five"), cues.events)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(listOf("rest:30", "five", "cancel:START_NOW", "go"), cues.events)
        assertEquals(null, repository.entry.sessionState?.restIntervalId)
    }

    @Test fun `threshold wins when extension tap and visible timer arrive together`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        val originalDeadline = repository.entry.sessionState?.restUntilEpochMillis

        viewModel.onScreenVisibilityChanged(false)
        runCurrent()
        advanceTimeBy(25_000)
        viewModel.onAddRestSeconds(30)
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()

        assertEquals(originalDeadline, repository.entry.sessionState?.restUntilEpochMillis)
        assertTrue(repository.entry.sessionState?.restFinalCountdownStarted == true)
        assertFalse(viewModel.uiState.value.canExtendRest)
        assertEquals(1, cues.events.count { it == "five" })
    }

    @Test fun `Start now cancels final warning and finishes one rest`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        advanceTimeBy(25_000)
        runCurrent()

        viewModel.onStartNow()
        viewModel.onStartNow()
        runCurrent()

        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(listOf("rest:30", "five", "cancel:START_NOW", "go"), cues.events)
    }

    @Test fun `recreated paused rest exits once and abandoned deadline cannot mutate active state`() = runSessionTest {
        val original = createSession()
        original.onScreenVisibilityChanged(true)
        runCurrent()
        original.onCompleteSet()
        runCurrent()
        advanceTimeBy(10_000)
        original.onPause()
        runCurrent()
        val frozen = repository.entry.sessionState!!
        assertEquals(20, frozen.pausedRestRemainingSeconds)
        viewModels.clear()
        runCurrent()
        cues.events.clear()
        val frozenWrites = repository.sessionWrites
        advanceTimeBy(60_000)
        val recovered = createSession()
        recovered.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(frozen, repository.entry.sessionState)
        // Legacy start-gate admission re-persists the identical recovered state.
        assertEquals(frozenWrites + 1, repository.sessionWrites)
        assertTrue(cues.events.isEmpty())

        recovered.onResume()
        runCurrent()
        val resumed = repository.entry.sessionState!!
        assertEquals(1_000_000L + dispatcher.scheduler.currentTime + 20_000,
            resumed.restUntilEpochMillis)
        assertEquals(listOf("rest:20"), cues.events)
        val resumedWrites = repository.sessionWrites
        recovered.onStartNow()
        recovered.onStartNow()
        runCurrent()
        val active = resumed.copy(status = SessionStatus.ACTIVE, restUntilEpochMillis = null,
            pausedRestRemainingSeconds = null, restIntervalId = null, restFinalCountdownStarted = false)
        assertEquals(active, repository.entry.sessionState)
        assertEquals(resumedWrites + 1, repository.sessionWrites)
        assertEquals(listOf("rest:20", "cancel:START_NOW", "go"), cues.events)
        advanceTimeBy(21_500)
        runCurrent()
        assertEquals(active, repository.entry.sessionState)
        assertEquals(resumedWrites + 1, repository.sessionWrites)
        assertEquals(listOf("rest:20", "cancel:START_NOW", "go"), cues.events)

        viewModels.clear()
        runCurrent()
        cues.events.clear()
        val reopened = createSession()
        reopened.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(active, repository.entry.sessionState)
        assertEquals(resumedWrites + 2, repository.sessionWrites)
        assertTrue(cues.events.isEmpty())
    }

    @Test fun `failed early exit emits no Go and retry cancels exactly one rest`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        val resting = repository.entry.sessionState
        val writes = repository.sessionWrites
        cues.events.clear()
        repository.failWrites = true
        viewModel.onStartNow()
        runCurrent()
        assertEquals(resting, repository.entry.sessionState)
        assertEquals(resting, viewModel.uiState.value.session)
        assertEquals(writes, repository.sessionWrites)
        assertTrue(cues.events.isEmpty())
        repository.failWrites = false
        viewModel.onStartNow()
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(writes + 1, repository.sessionWrites)
        assertEquals(listOf("cancel:START_NOW", "go"), cues.events)
        advanceTimeBy(31_500)
        runCurrent()
        assertEquals(writes + 1, repository.sessionWrites)
        assertEquals(listOf("cancel:START_NOW", "go"), cues.events)
    }

    @Test fun `timed sets auto complete once enter rest and reset next countdown`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "10 sec", 2, 6)))
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(10_000L, viewModel.uiState.value.timedSetRemainingMillis)
        val writes = repository.sessionWrites
        advanceTimeBy(9_750)
        runCurrent()
        assertEquals(250L, viewModel.uiState.value.timedSetRemainingMillis)
        assertEquals(writes, repository.sessionWrites)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(SessionStatus.RESTING, repository.entry.sessionState?.status)
        assertEquals(2, repository.entry.sessionState?.currentSet)
        assertEquals(1, repository.entry.sessionState?.progress?.completedSets?.single())
        assertEquals(writes + 1, repository.sessionWrites)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(10_000L, viewModel.uiState.value.timedSetRemainingMillis)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(SessionStatus.COMPLETED, repository.entry.sessionState?.status)
        assertEquals(listOf(LogStatus.DONE), sender.logs)
        assertEquals(1, cues.events.count { it == "workout-success" })
        assertEquals(2, repository.entry.sessionState?.progress?.completedSets?.single())
    }

    @Test fun `timed pause persists exact milliseconds through recreation and resume`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "10 sec", 2, 6)))
        val original = createSession()
        original.onScreenVisibilityChanged(true)
        runCurrent()
        advanceTimeBy(1_250)
        original.onPause()
        runCurrent()
        val frozen = repository.entry.sessionState!!
        assertEquals(8_750L, frozen.pausedTimedSetRemainingMillis)
        viewModels.clear()
        runCurrent()
        advanceTimeBy(60_000)
        val recovered = createSession()
        recovered.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(frozen, repository.entry.sessionState)
        assertEquals(8_750L, recovered.uiState.value.timedSetRemainingMillis)
        recovered.onResume()
        runCurrent()
        assertEquals(1_000_000L + dispatcher.scheduler.currentTime + 8_750,
            repository.entry.sessionState?.timedSetDeadlineEpochMillis)
        advanceTimeBy(8_750)
        runCurrent()
        assertEquals(SessionStatus.RESTING, repository.entry.sessionState?.status)
        assertEquals(1, repository.entry.sessionState?.progress?.completedSets?.single())
    }

    @Test fun `hidden timed set does not advance and wake catches up only that set`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "2 sec", 3, 0)))
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        val original = repository.entry.sessionState
        viewModel.onScreenVisibilityChanged(false)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(original, repository.entry.sessionState)
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(2, repository.entry.sessionState?.currentSet)
        assertEquals(2_000L, viewModel.uiState.value.timedSetRemainingMillis)
        assertEquals(1, repository.entry.sessionState?.progress?.completedSets?.single())
    }

    @Test fun `rejected timed completion remains retryable without log or cue escaping`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "2 sec", 1, 0)))
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        val original = repository.entry.sessionState
        repository.failWrites = true
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(original, repository.entry.sessionState)
        assertTrue(sender.logs.isEmpty())
        assertTrue(cues.events.isEmpty())
        repository.failWrites = false
        viewModel.clearError()
        runCurrent()
        assertEquals(SessionStatus.COMPLETED, repository.entry.sessionState?.status)
        assertEquals(listOf(LogStatus.DONE), sender.logs)
        assertEquals(listOf("workout-success"), cues.events)
    }

    @Test fun `manual finish racing timed expiry cannot consume two zero rest sets`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "2 sec", 3, 0)))
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        val writes = repository.sessionWrites
        advanceTimeBy(2_000)
        viewModel.onCompleteSet()
        runCurrent()
        assertEquals(2, repository.entry.sessionState?.currentSet)
        assertEquals(1, repository.entry.sessionState?.progress?.completedSets?.single())
        assertEquals(writes + 1, repository.sessionWrites)
        assertEquals(2_000L, viewModel.uiState.value.timedSetRemainingMillis)
    }

    @Test fun `expiry queued behind a slow write cannot complete after screen hides`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Plank", "2 sec", 3, 0)))
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        val original = repository.entry.sessionState
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.beforeCommit = { entered.complete(Unit); release.await() }
        viewModel.onUpgrade()
        runCurrent()
        entered.await()
        advanceTimeBy(2_000)
        runCurrent()
        viewModel.onScreenVisibilityChanged(false)
        release.complete(Unit)
        repository.beforeCommit = {}
        runCurrent()
        assertEquals(original, repository.entry.sessionState)
        assertEquals(4, repository.entry.exercises.single().sets)
        assertEquals(0, repository.entry.sessionState?.progress?.completedSets?.single())
        assertTrue(sender.logs.isEmpty())
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(2, repository.entry.sessionState?.currentSet)
        assertEquals(1, repository.entry.sessionState?.progress?.completedSets?.single())
    }

    @Test fun `final countdown lock survives pause and resume`() = runSessionTest {
        val viewModel = createSession()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        val intervalId = viewModel.uiState.value.session?.restIntervalId
        advanceTimeBy(25_000)
        runCurrent()

        viewModel.onPause()
        runCurrent()
        assertEquals(intervalId, repository.entry.sessionState?.restIntervalId)
        assertTrue(repository.entry.sessionState?.restFinalCountdownStarted == true)
        assertEquals(5, repository.entry.sessionState?.pausedRestRemainingSeconds)

        advanceTimeBy(60_000)
        viewModel.onResume()
        runCurrent()
        assertEquals(intervalId, repository.entry.sessionState?.restIntervalId)
        assertTrue(repository.entry.sessionState?.restFinalCountdownStarted == true)
        assertFalse(viewModel.uiState.value.canExtendRest)
        val deadline = repository.entry.sessionState?.restUntilEpochMillis
        viewModel.onAddRestSeconds(30)
        runCurrent()
        assertEquals(deadline, repository.entry.sessionState?.restUntilEpochMillis)
    }

    @Test fun `legacy active rest is assigned and persists a lock identity`() = runSessionTest {
        repository.entry = repository.entry.copy(sessionState = SessionState(
            workoutEntryId = repository.entry.id,
            exerciseIndex = 0,
            currentSet = 2,
            status = SessionStatus.RESTING,
            restUntilEpochMillis = 1_030_000L,
            elapsedStartedAtEpochMillis = 1_000_000L,
        ))

        val viewModel = createSession()
        runCurrent()

        assertEquals("rest-1", repository.entry.sessionState?.restIntervalId)
        assertEquals("rest-1", viewModel.uiState.value.session?.restIntervalId)
        assertFalse(repository.entry.sessionState?.restFinalCountdownStarted == true)
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
        assertTrue(viewModel.uiState.value.session?.resultSaved == true)
        assertEquals(listOf("cancel:SKIP", "workout-success"), cues.events)
    }

    @Test fun `exercise success is persisted with exact outcome counts and survives recreation`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(
            WorkoutExercise("Squat", "10", sets = 1, rest = 60),
            WorkoutExercise("Row", "8", sets = 2, rest = 0),
        ))
        val viewModel = createSession()
        runCurrent()

        viewModel.onCompleteSet()
        runCurrent()

        val persisted = repository.entry.sessionState
        assertEquals(listOf(ExerciseOutcomeStatus.COMPLETED, ExerciseOutcomeStatus.PENDING),
            persisted?.progress?.exerciseStatuses)
        assertEquals(listOf(1, 0), persisted?.progress?.completedSets)
        assertEquals(0, persisted?.progress?.successExerciseIndex)
        assertEquals(listOf("exercise-success"), cues.events)

        viewModels.clear()
        val recovered = createSession()
        runCurrent()
        assertEquals(0, recovered.uiState.value.progress?.successExerciseIndex)
        assertEquals(listOf("exercise-success", "close"), cues.events)
    }

    @Test fun `failed final write never exposes saved terminal state or completion cue`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(
            WorkoutExercise("Squat", "10", sets = 1, rest = 0),
        ))
        val viewModel = createSession()
        runCurrent()
        repository.failWrites = true

        viewModel.onCompleteSet()
        runCurrent()

        assertEquals(SessionStatus.ACTIVE, viewModel.uiState.value.session?.status)
        assertFalse(viewModel.uiState.value.session?.resultSaved == true)
        assertTrue(cues.events.isEmpty())
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

    @Test fun `pause and end cancel speech only after each durable commit`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        var entered = CompletableDeferred<Unit>()
        var release = CompletableDeferred<Unit>()
        repository.beforeCommit = { entered.complete(Unit); release.await() }

        viewModel.onPause()
        runCurrent()
        entered.await()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertTrue(cues.events.isEmpty())
        release.complete(Unit)
        runCurrent()
        assertEquals(SessionStatus.PAUSED, repository.entry.sessionState?.status)
        assertEquals(listOf("cancel:PAUSE"), cues.events)

        repository.beforeCommit = {}
        viewModel.onResume()
        runCurrent()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        entered = CompletableDeferred()
        release = CompletableDeferred()
        repository.beforeCommit = { entered.complete(Unit); release.await() }
        viewModel.onEndWorkout()
        runCurrent()
        entered.await()
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        assertEquals(listOf("cancel:PAUSE"), cues.events)
        release.complete(Unit)
        runCurrent()
        assertEquals(SessionStatus.ENDED, repository.entry.sessionState?.status)
        assertEquals(listOf("cancel:PAUSE", "cancel:END"), cues.events)
    }

    @Test fun `failed pause or end never cancels speech or changes the active session`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        val before = repository.entry.sessionState
        repository.failWrites = true
        viewModel.onPause()
        runCurrent()
        assertEquals(before, repository.entry.sessionState)
        assertTrue(cues.events.isEmpty())
        viewModel.onEndWorkout()
        runCurrent()
        assertEquals(before, repository.entry.sessionState)
        assertEquals(before, viewModel.uiState.value.session)
        assertTrue(cues.events.isEmpty())
        assertTrue(sender.events.isEmpty())
        assertTrue(sender.logs.isEmpty())
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

    @Test fun `close callback waits for durable pause commit`() = runSessionTest {
        val viewModel = createSession()
        runCurrent()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.beforeCommit = {
            entered.complete(Unit)
            release.await()
        }
        var closed = false

        viewModel.onCancel { closed = true }
        runCurrent()
        entered.await()

        assertFalse(closed)
        assertEquals(SessionStatus.ACTIVE, repository.entry.sessionState?.status)
        release.complete(Unit)
        runCurrent()
        assertTrue(closed)
        assertEquals(SessionStatus.PAUSED, repository.entry.sessionState?.status)
    }

    @Test fun `late phone receipt clears waiting without losing completed summary`() = runSessionTest {
        repository.entry = repository.entry.copy(exercises = listOf(WorkoutExercise("Squat", "10", sets = 1, rest = 0)))
        val receipts = MutableStateFlow(false)
        val viewModel = SessionViewModel(repository.entry.id, repository, sender,
            legacyStartGate = defaultGate(), awaitsPhoneReceipt = true, phoneReceiptStatus = receipts)
        viewModels.put("session", viewModel)
        runCurrent()
        viewModel.onCompleteSet()
        runCurrent()
        val completed = viewModel.uiState.value.session
        assertEquals(SessionStatus.COMPLETED, completed?.status)
        assertTrue(viewModel.uiState.value.awaitingPhoneSync)

        receipts.value = true
        runCurrent()
        assertFalse(viewModel.uiState.value.awaitingPhoneSync)
        assertEquals(completed, viewModel.uiState.value.session)
        receipts.value = false // A later request can replace the single receipt tombstone.
        runCurrent()
        assertFalse(viewModel.uiState.value.awaitingPhoneSync)
        assertEquals(completed, viewModel.uiState.value.session)
        viewModels.clear()
        runCurrent()
        assertEquals(0, receipts.subscriptionCount.value)
    }

    @Test fun `receipt already persisted when screen opens clears waiting`() = runSessionTest {
        val receipts = MutableStateFlow(true)
        val viewModel = SessionViewModel(repository.entry.id, repository, sender,
            legacyStartGate = defaultGate(), awaitsPhoneReceipt = true, phoneReceiptStatus = receipts)
        viewModels.put("session", viewModel)
        runCurrent()
        assertFalse(viewModel.uiState.value.awaitingPhoneSync)
        assertEquals(repository.entry.sessionState, viewModel.uiState.value.session)
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
        var beforeCommit: suspend () -> Unit = {}
        val pendingEffects = mutableListOf<WorkoutSessionEffects>()

        override suspend fun getEntry(entryId: String) = entry.takeIf { available && it.id == entryId }

        override suspend fun commitSession(expected: DownloadedWorkoutEntry,
            updated: DownloadedWorkoutEntry, effects: WorkoutSessionEffects?, action: SessionOutcomeAction?): Boolean {
            if (failWrites) error("Disk unavailable")
            beforeCommit()
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

    private class RecordingSessionCueEmitter : SessionCueEmitter {
        val events = mutableListOf<String>()
        override suspend fun restStarted(entry: DownloadedWorkoutEntry, session: SessionState,
            prescribedRestSeconds: Int, previousExerciseIndex: Int) {
            events += "rest:$prescribedRestSeconds"
        }
        override suspend fun fiveSeconds(entry: DownloadedWorkoutEntry, session: SessionState) {
            events += "five"
        }
        override suspend fun go(entry: DownloadedWorkoutEntry, session: SessionState,
            restDeadlineMillis: Long) {
            events += "cancel:START_NOW"
            events += "go"
        }
        override suspend fun exerciseSuccess(entry: DownloadedWorkoutEntry, session: SessionState,
            prescribedRestSeconds: Int, previousExerciseIndex: Int) {
            events += "exercise-success"
        }
        override suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState) {
            events += "workout-success"
        }
        override suspend fun cancel(reason: WatchCueCancellation) { events += "cancel:$reason" }
        override fun close() { events += "close" }
    }
}
