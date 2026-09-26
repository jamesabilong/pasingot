package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRejectionReason
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickStartRequestCoordinatorTest {
    private val now = 1_800_000_000_000L
    private val json = Json

    @Test
    fun `ready is sent only after package survives store recreation`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { acknowledgement ->
            assertEquals("phone-node", WatchSessionPackageStore(persistence)
                .current(now)?.sourcePhoneNodeId)
            receipts += acknowledgement
        }

        val result = coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)

        assertEquals(QuickStartStatus.READY, result?.status)
        assertEquals(listOf(result), receipts)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun `duplicate resends ready without changing durable package`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }
        val payload = json.encodeToString(request())
        coordinator.receive(payload, path(), "phone-node", "watch-node", now)

        val duplicate = coordinator.receive(payload, path(), "phone-node", "watch-node", now + 1)

        assertEquals(QuickStartStatus.READY, duplicate?.status)
        assertEquals(2, receipts.size)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun `wrong path or target never stores or acknowledges`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }
        val payload = json.encodeToString(request())
        assertNull(coordinator.receive(payload,
            QuickStartDataLayerPaths.REQUEST_PREFIX + "123e4567-e89b-12d3-a456-426614174099",
            "phone-node", "watch-node", now))
        assertNull(coordinator.receive(payload, path(), "watch-node", "watch-node", now))
        assertNull(coordinator.receive(payload, path(), "phone-node", "other-watch", now))
        assertEquals(0, persistence.writes)
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `invalid schema expired and malformed payload yield bounded receipts`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }

        val schema = coordinator.receive(json.encodeToString(request().copy(schemaVersion = 99)),
            path(), "phone-node", "watch-node", now)
        val expired = coordinator.receive(json.encodeToString(request()), path(),
            "phone-node", "watch-node", now + QUICK_START_TTL_MILLIS + 30_001)
        val malformed = coordinator.receive("{bad", path(), "phone-node", "watch-node", now)

        assertEquals(QuickStartRejectionReason.UNSUPPORTED_SCHEMA, schema?.reason)
        assertEquals(QuickStartStatus.EXPIRED, expired?.status)
        assertEquals(2L, expired?.revision)
        assertEquals(QuickStartRejectionReason.INVALID_PAYLOAD, malformed?.reason)
        assertEquals(3, receipts.size)
        assertEquals(0, persistence.writes)
    }

    @Test
    fun `different request is rejected while ready is pending`() = runTest {
        val persistence = MemoryPersistence()
        val coordinator = coordinator(persistence) { }
        coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
        val otherId = "123e4567-e89b-12d3-a456-426614174099"

        val result = coordinator.receive(json.encodeToString(request().copy(requestId = otherId)),
            QuickStartDataLayerPaths.REQUEST_PREFIX + otherId, "phone-node", "watch-node", now + 1)

        assertEquals(QuickStartRejectionReason.PENDING_REQUEST, result?.reason)
        assertEquals(REQUEST_ID, WatchSessionPackageStore(persistence).current(now + 1)?.request?.requestId)
    }

    @Test
    fun `active legacy session rejects offer before persistence`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val active = DownloadedWorkoutEntry("legacy", "2026-09-27", "Legacy",
            listOf(WorkoutExercise("Squat", "10", sets = 3, rest = 60)),
            sessionState = SessionState("legacy", 0, 1, SessionStatus.ACTIVE))
        val coordinator = QuickStartRequestCoordinator(WatchSessionPackageStore(persistence),
            LegacySessionSnapshotSource { listOf(active) }, QuickStartReceiptClient { receipts += it })

        val result = coordinator.receive(json.encodeToString(request()), path(),
            "phone-node", "watch-node", now)

        assertEquals(QuickStartRejectionReason.ACTIVE_SESSION, result?.reason)
        assertEquals(listOf(result), receipts)
        assertEquals(0, persistence.writes)
    }

    @Test
    fun `failed durable write never sends ready`() = runTest {
        val persistence = MemoryPersistence(failWrites = true)
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }

        try {
            coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
            fail("Expected storage failure")
        } catch (_: IllegalStateException) {
            assertTrue(receipts.isEmpty())
        }
    }

    @Test
    fun `starting replay emits no premature started receipt`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }
        val payload = json.encodeToString(request())
        coordinator.receive(payload, path(), "phone-node", "watch-node", now)
        WatchSessionPackageStore(persistence).markStarting(REQUEST_ID, 1, now + 1)

        val replay = coordinator.receive(payload, path(), "phone-node", "watch-node", now + 2)

        assertNull(replay)
        assertEquals(1, receipts.size)
    }

    private fun coordinator(persistence: MemoryPersistence, send: suspend (QuickStartAcknowledgement) -> Unit) =
        QuickStartRequestCoordinator(WatchSessionPackageStore(persistence),
            LegacySessionSnapshotSource { emptyList<DownloadedWorkoutEntry>() },
            QuickStartReceiptClient(send))

    private fun path() = QuickStartDataLayerPaths.REQUEST_PREFIX + REQUEST_ID

    private fun request() = QuickStartRequest(REQUEST_ID, createdAtMillis = now,
        expiresAtMillis = now + QUICK_START_TTL_MILLIS, targetNodeId = "watch-node",
        source = QuickStartSource.SINGLE,
        exercises = listOf(QuickStartExercise("item-1", "squat", "Squat", 3, "8-12 reps", 60)))

    private class MemoryPersistence(private val failWrites: Boolean = false) : QuickStartPackagePersistence {
        var raw: String? = null
        var writes = 0
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) {
            if (failWrites) throw IllegalStateException("disk unavailable")
            this.raw = raw
            writes++
        }
    }

    private companion object { const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000" }
}
