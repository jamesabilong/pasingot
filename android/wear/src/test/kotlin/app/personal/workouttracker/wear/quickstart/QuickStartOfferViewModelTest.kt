package app.personal.workouttracker.wear.quickstart

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
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
class QuickStartOfferViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test fun `Start initializes runtime before publishing receipt and navigation`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel().also { viewModels.put("start", it) }
        runCurrent()
        var opened: String? = null

        viewModel.start { opened = it }
        runCurrent()

        val runtime = fixture.runtime.current()
        assertNotNull(runtime)
        assertEquals(ID, opened)
        assertEquals(QuickStartStatus.STARTED, fixture.receipts.single().status)
        assertEquals(runtime!!.startedAcknowledgement, fixture.receipts.single())
    }

    @Test fun `Start recovers a package interrupted after STARTING was persisted`() = runTest(dispatcher) {
        val fixture = fixture()
        assertTrue(fixture.gate.startQuickStart(ID, 1, NOW) is QuickStartGateResult.Started)
        val viewModel = fixture.viewModel().also { viewModels.put("recover", it) }
        runCurrent()
        var opened: String? = null

        viewModel.start { opened = it }
        runCurrent()

        assertEquals(ID, opened)
        assertNotNull(fixture.runtime.current())
        assertEquals(QuickStartStatus.STARTED, fixture.receipts.last().status)
    }

    @Test fun `Dismiss persists terminal state before publishing receipt`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel().also { viewModels.put("dismiss", it) }
        runCurrent()

        viewModel.dismiss()
        runCurrent()

        assertNull(fixture.packages.current(NOW))
        assertEquals(QuickStartStatus.DISMISSED, fixture.receipts.single().status)
        assertNull(viewModel.uiState.value.sessionPackage)
    }

    private suspend fun fixture(): Fixture {
        val packages = WatchSessionPackageStore(MemoryPackagePersistence())
        packages.accept(runtimePackage().request, NOW, PHONE)
        val gate = GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages)
        return Fixture(
            packages = packages,
            runtime = QuickStartRuntimeStore(RuntimeMemoryPersistence()),
            gate = gate,
        )
    }

    private data class Fixture(
        val packages: WatchSessionPackageStore,
        val runtime: QuickStartRuntimeStore,
        val gate: GlobalSessionStartGate,
        val receipts: MutableList<QuickStartAcknowledgement> = mutableListOf(),
    ) {
        fun viewModel() = QuickStartOfferViewModel(
            packageStore = packages,
            runtimeStore = runtime,
            startGate = gate,
            receiptClient = QuickStartReceiptClient { receipts += it },
            nowEpochMillis = { NOW },
        )
    }

    private class MemoryPackagePersistence : QuickStartPackagePersistence {
        private var raw: String? = null
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) { this.raw = raw }
    }
}
