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
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
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
    fun `denied or failed notification never hides the durable in-app Ready offer`() = runTest {
        for (throws in listOf(false, true)) {
            val persistence = MemoryPersistence()
            val receipts = mutableListOf<QuickStartAcknowledgement>()
            val notifier = RecordingOfferNotifier(throwsOnShow = throws)
            val coordinator = coordinator(persistence, notifier) { receipts += it }

            val result = coordinator.receive(
                json.encodeToString(request()), path(), "phone-node", "watch-node", now,
            )

            assertEquals(QuickStartStatus.READY, result?.status)
            assertEquals(REQUEST_ID, WatchSessionPackageStore(persistence)
                .current(now)?.request?.requestId)
            assertEquals(listOf(result), receipts)
            assertEquals(1, notifier.showCalls)
        }
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
    fun `invalid schema and expired decisions are durable while malformed traffic is ignored`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }

        val schema = coordinator.receive(json.encodeToString(request().copy(schemaVersion = 99)),
            path(), "phone-node", "watch-node", now)
        val expiredId = "123e4567-e89b-12d3-a456-426614174099"
        val expired = coordinator.receive(json.encodeToString(request().copy(requestId = expiredId)),
            QuickStartDataLayerPaths.REQUEST_PREFIX + expiredId,
            "phone-node", "watch-node", now + QUICK_START_TTL_MILLIS + 30_001)
        val malformed = coordinator.receive("{bad", path(), "phone-node", "watch-node", now)

        assertEquals(QuickStartRejectionReason.UNSUPPORTED_SCHEMA, schema?.reason)
        assertEquals(QuickStartStatus.EXPIRED, expired?.status)
        assertEquals(2L, expired?.revision)
        assertNull(malformed)
        assertEquals(2, receipts.size)
        assertEquals(2, persistence.writes)
        val replay = coordinator(persistence) { }.receive(
            json.encodeToString(request().copy(schemaVersion = 99)), path(), "phone-node", "watch-node", now + 1)
        assertEquals(schema, replay)
        assertNull(coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now + 2))
        assertEquals(2, persistence.writes)
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
    fun `different offer reports active workout after durable start and preserves its package`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        val coordinator = coordinator(persistence) { }
        coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
        store.markStarting(REQUEST_ID, 1, now + 1)
        val active = store.current(now + 1)
        val otherId = "123e4567-e89b-12d3-a456-426614174099"
        val other = request().copy(requestId = otherId)
        val otherPath = QuickStartDataLayerPaths.REQUEST_PREFIX + otherId

        val result = coordinator.receive(json.encodeToString(other), otherPath,
            "phone-node", "watch-node", now + 2)

        assertEquals(QuickStartStatus.REJECTED, result?.status)
        assertEquals(QuickStartRejectionReason.ACTIVE_SESSION, result?.reason)
        assertEquals(active, store.current(now + 2))
        val writes = persistence.writes
        val replay = coordinator(persistence) { }.receive(json.encodeToString(other), otherPath,
            "phone-node", "watch-node", now + 3)
        assertEquals(result, replay)
        assertEquals(writes, persistence.writes)
        assertEquals(active, WatchSessionPackageStore(persistence).current(now + 3))
        assertNull(coordinator.receive(json.encodeToString(request()), path(),
            "phone-node", "watch-node", now + 3))
    }

    @Test
    fun `active legacy rejection is durable before receipt and survives blocker clearing`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val active = DownloadedWorkoutEntry("legacy", "2026-09-27", "Legacy",
            listOf(WorkoutExercise("Squat", "10", sets = 3, rest = 60)),
            sessionState = SessionState("legacy", 0, 1, SessionStatus.ACTIVE))
        var activeEntries = listOf(active)
        val coordinator = QuickStartRequestCoordinator(WatchSessionPackageStore(persistence),
            LegacySessionSnapshotSource { activeEntries }, QuickStartReceiptClient {
                val restored = WatchSessionPackageStore(persistence).replay(request(), now, "phone-node")
                assertEquals(it.status, (restored as AcceptQuickStartResult.PreviouslyTerminated).terminal.status)
                receipts += it
            })

        val result = coordinator.receive(json.encodeToString(request()), path(),
            "phone-node", "watch-node", now)

        assertEquals(QuickStartRejectionReason.ACTIVE_SESSION, result?.reason)
        assertEquals(listOf(result), receipts)
        assertEquals(1, persistence.writes)
        activeEntries = emptyList()
        val replay = coordinator(persistence) { receipts += it }.receive(json.encodeToString(request()),
            path(), "phone-node", "watch-node", now + 1)
        assertEquals(result, replay)
        assertEquals(1, persistence.writes)
        assertNull(WatchSessionPackageStore(persistence).current(now + 1))
    }

    @Test
    fun `pending rejection survives dismissing its blocker and store restart`() = runTest {
        val persistence = MemoryPersistence()
        val coordinator = coordinator(persistence) { }
        coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
        val otherId = "123e4567-e89b-12d3-a456-426614174099"
        val other = request().copy(requestId = otherId)
        val otherPath = QuickStartDataLayerPaths.REQUEST_PREFIX + otherId
        val rejected = coordinator.receive(json.encodeToString(other), otherPath,
            "phone-node", "watch-node", now + 1)
        WatchSessionPackageStore(persistence).dismiss(REQUEST_ID, 2, now + 2)
        val writes = persistence.writes

        val replay = coordinator(persistence) { }.receive(json.encodeToString(other), otherPath,
            "phone-node", "watch-node", now + 3)

        assertEquals(QuickStartRejectionReason.PENDING_REQUEST, rejected?.reason)
        assertEquals(rejected, replay)
        assertEquals(writes, persistence.writes)
        assertNull(WatchSessionPackageStore(persistence).current(now + 3))
    }

    @Test
    fun `future offer refusal survives history pruning until its arrival window closes`() = runTest {
        val persistence = MemoryPersistence()
        val coordinator = coordinator(persistence) { }
        val future = request().copy(createdAtMillis = now + 600_000, expiresAtMillis = now + 900_000)
        val rejected = coordinator.receive(json.encodeToString(future), path(), "phone-node", "watch-node", now)
        val otherId = "123e4567-e89b-12d3-a456-426614174099"
        val later = now + 360_001
        val other = request().copy(requestId = otherId, createdAtMillis = later,
            expiresAtMillis = later + QUICK_START_TTL_MILLIS)
        assertEquals(QuickStartStatus.READY, coordinator.receive(json.encodeToString(other),
            QuickStartDataLayerPaths.REQUEST_PREFIX + otherId, "phone-node", "watch-node", later)?.status)
        WatchSessionPackageStore(persistence).dismiss(otherId, 2, later + 1)

        val replay = coordinator(persistence) { }.receive(json.encodeToString(future), path(),
            "phone-node", "watch-node", now + 600_000)

        assertEquals(QuickStartRejectionReason.INVALID_PAYLOAD, rejected?.reason)
        assertEquals(rejected, replay)
        assertNull(WatchSessionPackageStore(persistence).current(now + 600_000))
    }

    @Test
    fun `terminal receipt binds original sender and exact offer`() = runTest {
        for (reject in listOf(false, true)) {
            val persistence = MemoryPersistence()
            val receipts = mutableListOf<QuickStartAcknowledgement>()
            val coordinator = coordinator(persistence) { receipts += it }
            val offer = if (reject) request().copy(schemaVersion = 99) else request()
            val payload = json.encodeToString(offer)
            coordinator.receive(payload, path(), "phone-node", "watch-node", now)
            if (!reject) WatchSessionPackageStore(persistence).dismiss(REQUEST_ID, 2, now + 1)
            val raw = persistence.raw
            val writes = persistence.writes
            val sent = receipts.size

            assertNull(coordinator.receive(payload, path(), "another-phone", "watch-node", now + 2))
            assertNull(coordinator.receive(json.encodeToString(offer.copy(title = "Changed")),
                path(), "phone-node", "watch-node", now + 2))
            assertEquals(raw, persistence.raw)
            assertEquals(writes, persistence.writes)
            assertEquals(sent, receipts.size)
            val replay = coordinator.receive(payload, path(), "phone-node", "watch-node", now + 3)
            assertEquals(if (reject) QuickStartStatus.REJECTED else QuickStartStatus.DISMISSED, replay?.status)
        }
    }

    @Test
    fun `malformed oversized and altered traffic cannot overwrite a ready decision`() = runTest {
        val persistence = MemoryPersistence()
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val coordinator = coordinator(persistence) { receipts += it }
        coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
        val raw = persistence.raw

        for (payload in listOf("{broken", "x".repeat(32_769), json.encodeToString(request().copy(exercises = emptyList())))) {
            assertNull(coordinator.receive(payload, path(), "phone-node", "watch-node", now + 1))
        }

        assertEquals(raw, persistence.raw)
        assertEquals(1, persistence.writes)
        assertEquals(1, receipts.size)
    }

    @Test
    fun `failed refusal persistence and corrupt storage never publish a receipt`() = runTest {
        for (raw in listOf(null, "corrupt", "{\"schemaVersion\":99}")) {
            val persistence = MemoryPersistence(failWrites = raw == null).apply { this.raw = raw }
            val receipts = mutableListOf<QuickStartAcknowledgement>()
            val coordinator = coordinator(persistence) { receipts += it }

            try {
                coordinator.receive(json.encodeToString(request().copy(schemaVersion = 99)),
                    path(), "phone-node", "watch-node", now)
                fail("Expected storage failure")
            } catch (_: IllegalStateException) { }

            assertTrue(receipts.isEmpty())
            assertEquals(raw, persistence.raw)
            assertEquals(0, persistence.writes)
        }
    }

    @Test
    fun `failed durable write never sends ready`() = runTest {
        val persistence = MemoryPersistence(failWrites = true)
        val receipts = mutableListOf<QuickStartAcknowledgement>()
        val notifier = RecordingOfferNotifier()
        val coordinator = coordinator(persistence, notifier) { receipts += it }

        try {
            coordinator.receive(json.encodeToString(request()), path(), "phone-node", "watch-node", now)
            fail("Expected storage failure")
        } catch (_: IllegalStateException) {
            assertTrue(receipts.isEmpty())
            assertEquals(0, notifier.showCalls)
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

        assertNull(coordinator.receive(payload, path(), "phone-node", "watch-node", now + 2))
        assertNull(coordinator.receive(payload, path(), "phone-node", "watch-node", now + 900_000))
        assertEquals(1, receipts.size)
    }

    @Test
    fun `expired ready and dismissed replay keep their persisted terminal receipt`() = runTest {
        for (dismiss in listOf(false, true)) {
            val persistence = MemoryPersistence()
            val coordinator = coordinator(persistence) { }
            val payload = json.encodeToString(request())
            coordinator.receive(payload, path(), "phone-node", "watch-node", now)
            val store = WatchSessionPackageStore(persistence)
            if (dismiss) store.dismiss(REQUEST_ID, 2, now + 1)
            val first = coordinator.receive(payload, path(), "phone-node", "watch-node", now + 300_001)
            val replay = coordinator.receive(payload, path(), "phone-node", "watch-node", now + 900_000)
            assertEquals(if (dismiss) QuickStartStatus.DISMISSED else QuickStartStatus.EXPIRED, first?.status)
            assertEquals(first, replay)
            assertNull(store.current(now + 900_000))
        }
    }

    private fun coordinator(
        persistence: MemoryPersistence,
        notifier: QuickStartOfferNotifier = NoOpQuickStartOfferNotifier,
        send: suspend (QuickStartAcknowledgement) -> Unit,
    ) =
        QuickStartRequestCoordinator(WatchSessionPackageStore(persistence),
            LegacySessionSnapshotSource { emptyList<DownloadedWorkoutEntry>() },
            QuickStartReceiptClient(send), offerNotifier = notifier)

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

    private class RecordingOfferNotifier(
        private val throwsOnShow: Boolean = false,
    ) : QuickStartOfferNotifier {
        var showCalls = 0
        override fun showReady(sessionPackage: WatchSessionPackage):
            QuickStartOfferNotificationResult {
            showCalls++
            if (throwsOnShow) error("notifications unavailable")
            return QuickStartOfferNotificationResult.PERMISSION_DENIED
        }
        override fun cancel() = Unit
    }

    private companion object { const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000" }
}
