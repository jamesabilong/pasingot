package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartCancellation
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.encodeQuickStartCancellation
import app.personal.workouttracker.shared.quickstart.quickStartCancellationPath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartCancellationCoordinatorTest {
    @Test fun `READY cancellation persists terminal decision before exact acknowledgement`() = runTest {
        val fixture = fixture()
        val cancellation = fixture.cancellation()
        val first = fixture.coordinator.receive(
            encodeQuickStartCancellation(cancellation), quickStartCancellationPath(ID),
            PHONE, WATCH, NOW + 1,
        )

        assertEquals(QuickStartStatus.CANCELLED, first?.status)
        assertNull(fixture.packages.current(NOW + 1))
        assertEquals(first, fixture.receipts.single())

        val replay = fixture.coordinator.receive(
            encodeQuickStartCancellation(cancellation), quickStartCancellationPath(ID),
            PHONE, WATCH, NOW + 2,
        )
        assertEquals(first, replay)
        assertEquals(2, fixture.receipts.size)
    }

    @Test fun `STARTING wins cancellation and wrong sender cannot alter READY`() = runTest {
        val started = fixture()
        assertTrue(started.gate.startQuickStart(ID, 1, NOW) is QuickStartGateResult.Started)
        assertNull(started.coordinator.receive(
            encodeQuickStartCancellation(started.cancellation()), quickStartCancellationPath(ID),
            PHONE, WATCH, NOW + 1,
        ))
        assertEquals(QuickStartPackageState.STARTING, started.packages.current(NOW + 1)?.state)
        assertTrue(started.receipts.isEmpty())

        val wrong = fixture()
        assertNull(wrong.coordinator.receive(
            encodeQuickStartCancellation(wrong.cancellation()), quickStartCancellationPath(ID),
            "other-phone", WATCH, NOW + 1,
        ))
        assertEquals(QuickStartPackageState.READY, wrong.packages.current(NOW + 1)?.state)
    }

    private suspend fun fixture(): Fixture {
        val packages = WatchSessionPackageStore(CancellationMemoryPersistence())
        packages.accept(runtimePackage().request, NOW, PHONE)
        val gate = GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages)
        val receipts = mutableListOf<app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement>()
        return Fixture(packages, gate, receipts,
            QuickStartCancellationCoordinator(gate, QuickStartReceiptClient { receipts += it }))
    }

    private data class Fixture(
        val packages: WatchSessionPackageStore,
        val gate: GlobalSessionStartGate,
        val receipts: MutableList<app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement>,
        val coordinator: QuickStartCancellationCoordinator,
    ) {
        fun cancellation() = QuickStartCancellation(ID, 2, WATCH, PHONE, NOW)
    }

    private class CancellationMemoryPersistence : QuickStartPackagePersistence {
        private var raw: String? = null
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) { this.raw = raw }
    }
}
