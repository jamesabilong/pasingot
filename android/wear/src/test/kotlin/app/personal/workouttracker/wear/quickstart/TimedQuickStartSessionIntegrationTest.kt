package app.personal.workouttracker.wear.quickstart

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.session.NoOpSessionCueEmitter
import app.personal.workouttracker.wear.session.SessionCueEmitter
import app.personal.workouttracker.wear.session.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real engine, adapter, serialized runtime and outcomes; no fake downloaded-session store. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimedQuickStartSessionIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val disk = RuntimeMemoryPersistence()
    private val sent = mutableListOf<FinalQuickStartResult>()
    private var successCues = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun sessionTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { models.clear() }
    }
    private fun now() = NOW + dispatcher.scheduler.currentTime
    private fun runtime() = QuickStartRuntimeStore(disk)
    private suspend fun initialize(sets: Int) {
        val request = timedRequest().let { it.copy(exercises = it.exercises.map {
            exercise -> exercise.copy(prescription = "2 sec", sets = sets, restSeconds = 0)
        }) }
        assertTrue(runtime().initialize(runtimePackage().copy(request = request),
            initialSession().copy(timedSetDeadlineEpochMillis = NOW + 2_000), NOW)
            is InitializeQuickStartRuntimeResult.Initialized)
    }
    private fun open(): SessionViewModel {
        val store = runtime()
        val adapter = QuickStartSessionStore(ID, store, ::now, QuickStartResultClient {
            assertEquals(it, store.current()!!.finalResult)
            sent += it
            error("offline")
        })
        val cues = object : SessionCueEmitter by NoOpSessionCueEmitter {
            override suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState) {
                assertNotNull(runtime().current()!!.finalResult)
                successCues++
            }
        }
        return SessionViewModel(ID, adapter, NoOpQuickStartLogSender, ::now,
            legacyStartGate = null, canAdjustSets = false, canRestart = false,
            awaitsPhoneReceipt = true, cueEmitter = cues).also { models.put("session", it) }
    }

    @Test fun `expired hidden runtime recovers one timed set only after foreground admission`() = sessionTest {
        initialize(3)
        val original = open()
        original.onScreenVisibilityChanged(true)
        runCurrent()
        original.onScreenVisibilityChanged(false)
        runCurrent()
        val hidden = runtime().current()!!
        models.clear()
        advanceTimeBy(60_000)
        val recovered = open()
        runCurrent()
        assertEquals(hidden, runtime().current())
        assertEquals(0L, recovered.uiState.value.timedSetRemainingMillis)
        assertTrue(sent.isEmpty())
        recovered.onScreenVisibilityChanged(true)
        runCurrent()
        val caughtUp = runtime().current()!!
        assertEquals(1, caughtUp.outcomes.exercises.single().completedSets)
        assertEquals(2, caughtUp.session.currentSet)
        assertEquals(NOW + 62_000, caughtUp.session.timedSetDeadlineEpochMillis)
        assertEquals(hidden.runtimeRevision + 1, caughtUp.runtimeRevision)
        assertEquals(2_000L, recovered.uiState.value.timedSetRemainingMillis)
        recovered.onScreenVisibilityChanged(false)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(caughtUp, runtime().current())
    }

    @Test fun `paused adapter runtime recreates and resumes exact subsecond time`() = sessionTest {
        initialize(2)
        val original = open()
        original.onScreenVisibilityChanged(true)
        runCurrent()
        advanceTimeBy(1_250)
        original.onPause()
        runCurrent()
        val paused = runtime().current()!!
        assertEquals(750L, paused.session.pausedTimedSetRemainingMillis)
        assertNull(paused.session.timedSetDeadlineEpochMillis)
        models.clear()
        advanceTimeBy(60_000)
        val recovered = open()
        recovered.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(paused, runtime().current())
        assertEquals(750L, recovered.uiState.value.timedSetRemainingMillis)
        recovered.onResume()
        runCurrent()
        assertEquals(now() + 750, runtime().current()!!.session.timedSetDeadlineEpochMillis)
        advanceTimeBy(750)
        runCurrent()
        assertEquals(1, runtime().current()!!.outcomes.exercises.single().completedSets)
        assertEquals(2, runtime().current()!!.session.currentSet)
        assertTrue(sent.isEmpty())
    }

    @Test fun `failed expiry retries one durable final result and terminal recreation does not replay success`() = sessionTest {
        initialize(1)
        val viewModel = open()
        viewModel.onScreenVisibilityChanged(true)
        runCurrent()
        val before = runtime().current()!!
        disk.beforeWrite = { error("disk unavailable") }
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(before, runtime().current())
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(sent.isEmpty())
        assertEquals(0, successCues)
        disk.beforeWrite = {}
        viewModel.clearError()
        runCurrent()
        val completed = runtime().current()!!
        assertEquals(SessionStatus.COMPLETED, completed.session.status)
        assertNull(completed.session.timedSetDeadlineEpochMillis)
        assertEquals(1, completed.outcomes.exercises.single().completedSets)
        assertEquals(before.runtimeRevision + 1, completed.runtimeRevision)
        assertEquals(listOf(completed.finalResult), sent)
        assertEquals(1, successCues)
        viewModel.onCompleteSet()
        runCurrent()
        assertEquals(completed, runtime().current())
        models.clear()
        val reopened = open()
        reopened.onScreenVisibilityChanged(true)
        runCurrent()
        assertEquals(completed, runtime().current())
        assertEquals(SessionStatus.COMPLETED, reopened.uiState.value.session!!.status)
        assertTrue(reopened.uiState.value.awaitingPhoneSync)
        assertEquals(1, sent.size)
        assertEquals(1, successCues)
    }
}
