package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QuickStartStartCoordinatorTest {
    @Test fun `first timed deadline is durable before a delayed Started receipt and retry preserves it`() = runTest {
        val packages = WatchSessionPackageStore(PackagePersistence())
        val request = timedRequest()
        packages.accept(request, NOW, PHONE)
        val disk = RuntimeMemoryPersistence()
        val runtime = QuickStartRuntimeStore(disk)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var now = NOW
        val coordinator = QuickStartStartCoordinator(
            GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages), runtime,
            QuickStartReceiptClient {
                assertEquals(NOW + 30_000, runtime.current()!!.session.timedSetDeadlineEpochMillis)
                entered.complete(Unit)
                release.await()
            }, { now },
        )
        val pending = async { coordinator.start(ID, request.revision) }
        entered.await()
        val persisted = QuickStartRuntimeStore(disk).current()!!
        assertEquals(NOW, persisted.session.elapsedStartedAtEpochMillis)
        assertEquals(NOW + 30_000, persisted.session.timedSetDeadlineEpochMillis)
        now += 45_000
        release.complete(Unit)
        assertEquals(persisted, pending.await())
        val retry = QuickStartStartCoordinator(
            GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages),
            QuickStartRuntimeStore(disk), QuickStartReceiptClient { error("offline") }, { now },
        ).start(ID, request.revision)
        assertEquals(persisted, retry)
        assertEquals(1, disk.writes)
    }

    @Test fun `repetition first exercise keeps manual completion despite a later timed exercise`() = runTest {
        val packages = WatchSessionPackageStore(PackagePersistence())
        val request = runtimePackage().request
        packages.accept(request, NOW, PHONE)
        val runtime = QuickStartRuntimeStore(RuntimeMemoryPersistence())
        val started = QuickStartStartCoordinator(
            GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages), runtime,
            QuickStartReceiptClient {}, { NOW },
        ).start(ID, request.revision)
        assertNull(started.session.timedSetDeadlineEpochMillis)
    }

    private class PackagePersistence : QuickStartPackagePersistence {
        var raw: String? = null
        override suspend fun read() = raw
        override suspend fun write(raw: String?) { this.raw = raw }
    }
}

internal fun timedRequest(): QuickStartRequest = runtimePackage().request.let { request ->
    request.copy(exercises = listOf(request.exercises.first().copy(
        exerciseName = "Plank", prescription = "30 sec", sets = 2, restSeconds = 6,
    )))
}
