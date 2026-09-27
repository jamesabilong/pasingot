package app.personal.workouttracker.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.FinalQuickStartResult
import app.personal.workouttracker.shared.quickstart.QuickStartResultEnvelope
import app.personal.workouttracker.shared.quickstart.QUICK_START_RESULT_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.encodeQuickStartResultEnvelope
import app.personal.workouttracker.shared.quickstart.quickStartResultPath
import app.personal.workouttracker.shared.session.ExerciseOutcome
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.ExerciseProgressCounts
import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.WorkoutProgressSnapshot
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

    @Test fun `persisted offer reserves slot before transport and across ambiguous failure`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(), now)
        val other = request("123e4567-e89b-12d3-a456-426614174001")
        // The first send is still waiting for DataClient, or its response was
        // lost. A second plugin/store instance must not start another offer.
        val restored = QuickStartPhoneStore(persistence)
        try { restored.saveRequest(other, now + 1); fail("Expected pending reservation") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("pending")) }
        assertEquals(request(), restored.saveRequest(request(), now + 2).request)
        assertEquals(requestId, restored.latest()?.request?.requestId)
        assertNull(restored.current(requestId)?.transportAcceptedAtMillis)
    }

    @Test fun `history preserves earlier requests and rejects full queue`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        repeat(64) { index ->
            val saved = request("123e4567-e89b-12d3-a456-${(index + 1).toString().padStart(12, '0')}")
            store.saveRequest(saved, now)
            store.acceptAcknowledgement(Json.encodeToString(ack(QuickStartStatus.STARTED, 2)
                .copy(requestId = saved.requestId)),
                QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + saved.requestId, "watch-1")
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

    @Test fun `cancellation is durable before transport and cannot follow Started`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(), now)
        val cancellation = store.prepareCancellation(requestId, "phone-1", now + 1)
        assertEquals(2L, cancellation.revision)
        assertEquals(cancellation,
            QuickStartPhoneStore(persistence).prepareCancellation(requestId, "phone-1", now + 2))
        assertEquals(cancellation,
            QuickStartPhoneStore(persistence).recordsWithPendingCancellations().single().cancellation)
        store.acceptAcknowledgement(
            Json.encodeToString(ack(QuickStartStatus.STARTED, 2)), path, "watch-1",
        )
        assertTrue(store.recordsWithPendingCancellations().isEmpty())

        val startedPersistence = MemoryPersistence()
        val startedStore = QuickStartPhoneStore(startedPersistence)
        startedStore.saveRequest(request(), now)
        startedStore.acceptAcknowledgement(
            Json.encodeToString(ack(QuickStartStatus.STARTED, 2)), path, "watch-1",
        )
        try {
            startedStore.prepareCancellation(requestId, "phone-1", now + 2)
            fail("Expected Started cancellation refusal")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("started"))
        }
    }

    @Test fun `final result import persists exact receipt atomically and replays idempotently`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(), now)
        val result = completedResult()
        val resultPath = quickStartResultPath(requestId, result.resultId)
        val payload = encodeQuickStartResultEnvelope(QuickStartResultEnvelope(
            QUICK_START_RESULT_SCHEMA_VERSION, "watch-1", result,
        ))

        val imported = store.importResult(payload, resultPath, "watch-1", "phone-1", now + 10)
            as PhoneQuickStartResultImport.Imported
        assertEquals(result, imported.record.finalResult)
        assertEquals(now + 10, imported.record.resultReceipt?.receivedAtMillis)
        assertTrue(store.importResult(payload, resultPath, "watch-1", "phone-1", now + 99)
            is PhoneQuickStartResultImport.Duplicate)
        assertTrue(store.importResult(payload, resultPath, "other-watch", "phone-1", now + 99)
            is PhoneQuickStartResultImport.Invalid)
        assertEquals(imported.record.resultReceipt,
            QuickStartPhoneStore(persistence).recordsWithResultReceipts().single().resultReceipt)
    }

    private fun completedResult() = FinalQuickStartResult(
        requestId = requestId,
        resultId = "result-1",
        outcomeRevision = 3,
        phoneNodeId = "phone-1",
        summary = WorkoutCompletionSummary(
            completedAtEpochMillis = now + 5,
            snapshot = WorkoutProgressSnapshot(
                sessionId = requestId,
                progress = ExerciseProgressCounts(1, 1, 0, 0),
                completedSets = 3,
                plannedSets = 3,
                elapsedActiveSeconds = 30,
                exercises = listOf(ExerciseOutcome(
                    "item", "exercise", "Squat", ExerciseOutcomeStatus.COMPLETED, 3, 3,
                )),
            ),
        ),
    )
}
