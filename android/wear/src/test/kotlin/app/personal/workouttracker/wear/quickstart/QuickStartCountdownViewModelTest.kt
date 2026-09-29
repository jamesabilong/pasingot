package app.personal.workouttracker.wear.quickstart

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.wear.cues.WatchCueCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickStartCountdownViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test fun `zero atomically starts runtime before Go and navigation`() = runTest(dispatcher) {
        var elapsed = 0L
        val fixture = fixture(
            elapsed = { elapsed },
            delay = { elapsed += 1_000L },
        )
        var opened: String? = null
        fixture.viewModel.begin { opened = it }
        runCurrent()

        assertEquals(ID, opened)
        assertEquals(listOf("begin", "cancel:START_NOW", "go"), fixture.cues.events)
        assertEquals(QuickStartPackageState.STARTING, fixture.packages.current(NOW)?.state)
        assertNotNull(fixture.runtime.current())
        assertEquals(QuickStartStatus.STARTED, fixture.receipts.single().status)
        assertTrue(fixture.viewModel.uiState.value.starting)
        assertEquals(1, fixture.notifier.cancels)
    }

    @Test fun `leaving before zero keeps Ready and creates no runtime`() = runTest(dispatcher) {
        val frame = CompletableDeferred<Unit>()
        val fixture = fixture(elapsed = { 0L }, delay = { frame.await() })
        var cancelled = false
        fixture.viewModel.begin {}
        runCurrent()

        fixture.viewModel.leave { cancelled = true }
        runCurrent()

        assertTrue(cancelled)
        assertEquals(QuickStartPackageState.READY, fixture.packages.current(NOW)?.state)
        assertNull(fixture.runtime.current())
        assertTrue(fixture.receipts.isEmpty())
        assertTrue("cancel:NAVIGATION" in fixture.cues.events)
        assertTrue("close" in fixture.cues.events)
    }

    @Test fun `expiry is rechecked at zero and cannot create a session`() = runTest(dispatcher) {
        var elapsed = 0L
        var wall = NOW
        val fixture = fixture(
            now = { wall },
            elapsed = { elapsed },
            delay = {
                elapsed += 1_000L
                if (elapsed == 5_000L) wall = NOW + 10 * 60_000L
            },
        )
        fixture.viewModel.begin {}
        runCurrent()

        assertNull(fixture.runtime.current())
        assertTrue(fixture.viewModel.uiState.value.error?.contains("expired") == true)
        assertTrue(fixture.receipts.isEmpty())
    }

    @Test fun `leaving cannot roll back a start after the zero boundary`() = runTest(dispatcher) {
        var elapsed = 0L
        val goGate = CompletableDeferred<Unit>()
        val cues = FakeCountdownCues(goGate)
        val fixture = fixture(
            elapsed = { elapsed },
            delay = { elapsed += 1_000L },
            cues = cues,
        )
        var opened: String? = null
        var cancelled = false
        fixture.viewModel.begin { opened = it }
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value.starting)
        assertNotNull(fixture.runtime.current())
        fixture.viewModel.leave { cancelled = true }
        runCurrent()
        assertTrue(!cancelled)
        assertTrue("cancel:NAVIGATION" !in cues.events)

        fixture.viewModel.onLeavingForeground { cancelled = true }
        runCurrent()
        assertTrue(!cancelled)
        assertTrue("cancel:NAVIGATION" in cues.events)
        assertTrue("close" in cues.events)
        assertEquals(QuickStartPackageState.STARTING, fixture.packages.current(NOW)?.state)

        goGate.complete(Unit)
        runCurrent()
        assertEquals(ID, opened)
    }

    private suspend fun fixture(
        now: () -> Long = { NOW },
        elapsed: () -> Long,
        delay: suspend () -> Unit,
        cues: FakeCountdownCues = FakeCountdownCues(),
    ): Fixture {
        val packages = WatchSessionPackageStore(MemoryCountdownPackagePersistence())
        packages.accept(runtimePackage().request, NOW, PHONE)
        val runtime = QuickStartRuntimeStore(RuntimeMemoryPersistence())
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val notifier = CountdownOfferNotifier()
        val viewModel = QuickStartCountdownViewModel(
            requestId = ID,
            packageStore = packages,
            startCoordinator = QuickStartStartCoordinator(
                GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages),
                runtime,
                QuickStartReceiptClient { receipts += it },
                now,
                notifier,
            ),
            cues = cues,
            nowEpochMillis = now,
            elapsedRealtimeMillis = elapsed,
            countdownDelay = QuickStartCountdownDelay { delay() },
        ).also { viewModels.put("countdown", it) }
        return Fixture(viewModel, packages, runtime, receipts, cues, notifier)
    }

    private data class Fixture(
        val viewModel: QuickStartCountdownViewModel,
        val packages: WatchSessionPackageStore,
        val runtime: QuickStartRuntimeStore,
        val receipts: MutableList<QuickStartAcknowledgement>,
        val cues: FakeCountdownCues,
        val notifier: CountdownOfferNotifier,
    )
}

private class CountdownOfferNotifier : QuickStartOfferNotifier {
    var cancels = 0
    override fun showReady(sessionPackage: WatchSessionPackage) = QuickStartOfferNotificationResult.SHOWN
    override fun cancel() { cancels++ }
}

private class FakeCountdownCues(
    private val goGate: CompletableDeferred<Unit>? = null,
) : QuickStartCountdownCues {
    val events = mutableListOf<String>()
    override suspend fun begin(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long) {
        events += "begin"
    }
    override suspend fun go(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long) {
        events += "go"
        goGate?.await()
    }
    override suspend fun cancel(reason: WatchCueCancellation) { events += "cancel:$reason" }
    override suspend fun close() { events += "close" }
}

private class MemoryCountdownPackagePersistence : QuickStartPackagePersistence {
    private var raw: String? = null
    override suspend fun read(): String? = raw
    override suspend fun write(raw: String?) { this.raw = raw }
}
