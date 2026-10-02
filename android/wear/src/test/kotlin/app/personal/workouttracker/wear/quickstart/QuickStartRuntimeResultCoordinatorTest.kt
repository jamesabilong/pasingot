package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.QUICK_START_RESULT_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptStatus
import app.personal.workouttracker.shared.quickstart.encodeQuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.quickStartResultReceiptPath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickStartRuntimeResultCoordinatorTest {
    @Test fun `failed cue pruning retries from durable receipt tombstones`() = runTest {
        val fixture = fixture()
        val incoming = fixture.receiptPayload()
        var fail = true
        val coordinator = QuickStartRuntimeResultCoordinator(fixture.runtime, fixture.packages) {
            if (fail) error("Cue storage unavailable")
        }
        var failed = false
        try {
            coordinator.acknowledgePayloadAndPrune(incoming.payload, incoming.path, PHONE, WATCH)
        } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertNull(fixture.runtime.current())
        assertNull(fixture.packages.current(NOW + 10))
        fail = false
        assertTrue(coordinator.acknowledgePayloadAndPrune(incoming.payload, incoming.path, PHONE, WATCH)
            is QuickStartResultCleanupResult.AlreadyPruned)
    }

    @Test fun `exact receipt clears runtime then releases package and replay is idempotent`() = runTest {
        val fixture = fixture()
        val incoming = fixture.receiptPayload()

        assertTrue(fixture.coordinator.acknowledgePayloadAndPrune(
            incoming.payload, incoming.path, PHONE, WATCH,
        ) is QuickStartResultCleanupResult.Pruned)
        assertNull(fixture.runtime.current())
        assertNull(fixture.packages.current(NOW + 10))
        assertEquals(listOf(ID), fixture.clearedCueSessions)

        assertTrue(fixture.coordinator.acknowledgePayloadAndPrune(
            incoming.payload, incoming.path, PHONE, WATCH,
        ) is QuickStartResultCleanupResult.AlreadyPruned)
        assertEquals(listOf(ID, ID), fixture.clearedCueSessions)
    }

    @Test fun `wrong sender or receipt keeps final runtime and package`() = runTest {
        val fixture = fixture()
        val incoming = fixture.receiptPayload()

        assertTrue(fixture.coordinator.acknowledgePayloadAndPrune(
            incoming.payload, incoming.path, "other-phone", WATCH,
        ) is QuickStartResultCleanupResult.Mismatch)
        assertNotNull(fixture.runtime.current()?.finalResult)
        assertNotNull(fixture.packages.current(NOW + 10))
        assertTrue(fixture.clearedCueSessions.isEmpty())
    }

    private suspend fun fixture(): Fixture {
        val packagePersistence = ResultPackageMemoryPersistence()
        val packages = WatchSessionPackageStore(packagePersistence)
        packages.accept(runtimePackage().request, NOW, PHONE)
        val starting = (packages.markStarting(ID, 1, NOW) as MarkQuickStartStartingResult.MarkedStarting)
            .sessionPackage
        val runtime = QuickStartRuntimeStore(RuntimeMemoryPersistence())
        runtime.initialize(starting, initialSession(), NOW)
        val before = requireNotNull(runtime.current())
        runtime.transition(
            ID, before.runtimeRevision, before.session,
            before.session.copy(
                status = SessionStatus.ENDED,
                elapsedStartedAtEpochMillis = null,
                accumulatedElapsedMillis = 1_000,
                lastStopReason = "ended_by_user",
            ),
            nowEpochMillis = NOW + 1_000,
        )
        val cleared = mutableListOf<String>()
        return Fixture(runtime, packages, QuickStartRuntimeResultCoordinator(runtime, packages) { cleared += it }, cleared)
    }

    private data class Fixture(
        val runtime: QuickStartRuntimeStore,
        val packages: WatchSessionPackageStore,
        val coordinator: QuickStartRuntimeResultCoordinator,
        val clearedCueSessions: List<String>,
    ) {
        suspend fun receiptPayload(): Incoming {
            val final = requireNotNull(runtime.current()?.finalResult)
            val receipt = QuickStartResultReceipt(
                final.requestId, final.resultId, final.outcomeRevision, PHONE, NOW + 2_000,
            )
            return Incoming(
                encodeQuickStartResultReceiptEnvelope(QuickStartResultReceiptEnvelope(
                    QUICK_START_RESULT_SCHEMA_VERSION, WATCH,
                    QuickStartResultReceiptStatus.PERSISTED, receipt,
                )),
                quickStartResultReceiptPath(final.requestId, final.resultId),
            )
        }
    }

    private data class Incoming(val payload: String, val path: String)

    private class ResultPackageMemoryPersistence : QuickStartPackagePersistence {
        private var raw: String? = null
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) { this.raw = raw }
    }
}
