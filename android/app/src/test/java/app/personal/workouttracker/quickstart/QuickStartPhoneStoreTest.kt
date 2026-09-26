package app.personal.workouttracker.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickStartPhoneStoreTest {
    private val now = 1_800_000_000_000L
    private val requestId = "123e4567-e89b-12d3-a456-426614174000"
    private val path = QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + requestId
    private fun request(id: String = requestId) = QuickStartRequest(
        requestId = id, createdAtMillis = now, expiresAtMillis = now + 300_000,
        targetNodeId = "watch-1", source = QuickStartSource.SINGLE,
        exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 3, "10 reps", 60)),
    )
    private fun ack(status: QuickStartStatus, revision: Long = 1) = QuickStartAcknowledgement(
        requestId, revision, "watch-1", status, watchUpdatedAtMillis = now + revision,
    )
    private class MemoryPersistence(var raw: String? = null) : QuickStartPhonePersistence {
        var writes = 0
        var beforeWrite: () -> Unit = {}
        var afterWrite: () -> Unit = {}
        override suspend fun read() = raw
        override suspend fun write(raw: String) {
            beforeWrite()
            this.raw = raw
            writes++
            afterWrite()
        }
    }

    @Test fun `transport acceptance stays pending until a watch Ready receipt persists`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(), now)
        store.markTransportAccepted(requestId, now + 10)
        assertNull(QuickStartPhoneStore(persistence).current(requestId)?.acknowledgement)
        assertEquals(PhoneAcknowledgementResult.RECORDED,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.READY)), path, "watch-1"))
        val restored = QuickStartPhoneStore(persistence).current(requestId)!!
        assertEquals(QuickStartStatus.READY, restored.acknowledgement?.status)
        assertEquals(now + 10, restored.transportAcceptedAtMillis)
        val writes = persistence.writes
        assertEquals(PhoneAcknowledgementResult.DUPLICATE,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.READY)), path, "watch-1"))
        assertEquals(writes, persistence.writes)
    }

    @Test fun `wrong node path and stale revision never replace newer state`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(), now)
        assertEquals(PhoneAcknowledgementResult.RECORDED,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.READY)), path, "watch-1"))
        val started = ack(QuickStartStatus.STARTED, 2)
        assertEquals(PhoneAcknowledgementResult.RECORDED,
            store.acceptAcknowledgement(Json.encodeToString(started), path, "watch-1"))
        val writes = persistence.writes
        assertEquals(PhoneAcknowledgementResult.WRONG_NODE,
            store.acceptAcknowledgement(Json.encodeToString(started.copy(watchUpdatedAtMillis = now + 10)), path, "other-watch"))
        assertEquals(PhoneAcknowledgementResult.INVALID,
            store.acceptAcknowledgement(Json.encodeToString(started), "$path/extra", "watch-1"))
        assertEquals(PhoneAcknowledgementResult.STALE,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.READY)), path, "watch-1"))
        assertEquals(PhoneAcknowledgementResult.STALE,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.CANCELLED, 3)), path, "watch-1"))
        assertEquals(writes, persistence.writes)
        assertEquals(started, store.current(requestId)?.acknowledgement)
    }

    @Test fun `conflicting equal revision is stale and latest offer survives recreation`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        assertNull(store.latest())
        store.saveRequest(request(), now)
        val ready = ack(QuickStartStatus.READY)
        assertEquals(PhoneAcknowledgementResult.RECORDED,
            store.acceptAcknowledgement(Json.encodeToString(ready), path, "watch-1"))
        assertEquals(PhoneAcknowledgementResult.STALE,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.STARTED)), path, "watch-1"))
        val restored = QuickStartPhoneStore(persistence).latest()
        assertEquals(request(), restored?.request)
        assertEquals(ready, restored?.acknowledgement)
    }

    @Test fun `accepted pending offer blocks another send until terminal or expiry`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val next = request("123e4567-e89b-12d3-a456-426614174001")
        store.saveRequest(request(), now)
        store.markTransportAccepted(requestId, now + 1)
        try { store.saveRequest(next, now + 2); fail("Expected pending offer") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("pending")) }
        assertEquals(PhoneAcknowledgementResult.RECORDED,
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.STARTED)), path, "watch-1"))
        assertEquals(next, store.saveRequest(next, now + 2).request)
        store.markTransportAccepted(next.requestId, now + 3)

        val afterExpiry = request("123e4567-e89b-12d3-a456-426614174002").copy(
            createdAtMillis = now + 400_000, expiresAtMillis = now + 700_000,
        )
        assertEquals(afterExpiry, store.saveRequest(afterExpiry, now + 400_000).request)
    }

    @Test fun `interrupted writes reconcile from durable bytes`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        persistence.afterWrite = { throw IOException("lost write response") }
        try { store.saveRequest(request(), now); fail("Expected failure") } catch (_: IOException) {}
        persistence.afterWrite = {}
        assertEquals(request(), QuickStartPhoneStore(persistence).saveRequest(request(), now).request)
        persistence.beforeWrite = { throw IOException("disk full") }
        try { store.markTransportAccepted(requestId, now + 1); fail("Expected failure") } catch (_: IOException) {}
        assertNull(store.current(requestId)?.transportAcceptedAtMillis)
        persistence.beforeWrite = {}
        store.markTransportAccepted(requestId, now + 1)
        assertEquals(now + 1, QuickStartPhoneStore(persistence).current(requestId)?.transportAcceptedAtMillis)
    }

    @Test fun `history preserves earlier requests and rejects full queue`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        repeat(64) { index ->
            store.saveRequest(request("123e4567-e89b-12d3-a456-${(index + 1).toString().padStart(12, '0')}"), now)
        }
        val raw = persistence.raw
        try {
            store.saveRequest(request(), now)
            fail("Expected bounded history")
        } catch (_: IllegalArgumentException) {
            assertEquals(raw, persistence.raw)
        }
        assertTrue(store.current("123e4567-e89b-12d3-a456-000000000001") != null)
    }

    @Test fun `future or damaged records fail closed without erasing requests`() = runTest {
        for (raw in listOf("broken", "{\"schemaVersion\":99}")) {
            val persistence = MemoryPersistence(raw)
            try { QuickStartPhoneStore(persistence).current(requestId); fail("Expected failure") }
            catch (_: IllegalStateException) { assertEquals(raw, persistence.raw); assertEquals(0, persistence.writes) }
        }
    }
}
