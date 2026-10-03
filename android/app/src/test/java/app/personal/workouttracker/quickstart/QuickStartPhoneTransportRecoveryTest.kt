package app.personal.workouttracker.quickstart

import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.shared.session.ExerciseOutcome
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.ExerciseProgressCounts
import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.WorkoutProgressSnapshot
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class QuickStartPhoneTransportRecoveryTest {
    @Test fun `stale acknowledgement cannot regress a completed phone record or prevent cleanup`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val record = seed(store, FIRST)
        assertEquals(PhoneAcknowledgementResult.STALE, store.acceptAcknowledgement(
            Json.encodeToString(ack(FIRST, QuickStartStatus.EXPIRED)),
            QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + FIRST, WATCH))
        val transport = MemoryTransport().apply { addOffer(record) }
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertTrue(transport.items.isEmpty())
        assertEquals(record, store.current(FIRST))
    }

    @Test fun `retained result interrupted before import is durably recovered before receipt and never cancelled`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(FIRST), NOW)
        store.prepareCancellation(FIRST, PHONE, NOW + 1)
        val result = result(FIRST)
        val transport = MemoryTransport().apply {
            addOffer(requireNotNull(store.current(FIRST)))
            items += QuickStartTransportItem(WATCH, quickStartResultPath(FIRST, result.resultId), resultPayload(result))
        }
        val imported = mutableListOf<String>()
        QuickStartPhoneTransportRecovery(QuickStartPhoneStore(persistence), transport,
            onImported = { id ->
                assertNotNull(persistence.raw)
                imported += id
            }).reconcile()
        val record = requireNotNull(store.current(FIRST))
        assertEquals(result, record.finalResult)
        assertEquals(listOf(requireNotNull(record.resultReceipt) to WATCH), transport.sentReceipts)
        assertEquals(listOf(FIRST), imported)
        assertTrue(transport.sentCancellations.isEmpty())
        assertEquals(listOf(quickStartResultPath(FIRST, result.resultId)), transport.items.map { it.path })
        QuickStartPhoneTransportRecovery(store, transport, onImported = { imported += it }).reconcile()
        assertEquals(listOf(FIRST), imported)
        assertEquals(record, store.current(FIRST))
    }

    @Test fun `failed retained result import preserves transport for a durable retry`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        store.saveRequest(request(FIRST), NOW)
        val result = result(FIRST)
        val transport = MemoryTransport().apply {
            items += QuickStartTransportItem(WATCH, quickStartResultPath(FIRST, result.resultId), resultPayload(result))
        }
        persistence.failWrite = true
        val failures = mutableListOf<String>()
        QuickStartPhoneTransportRecovery(store, transport, onFailure = { id, _ -> failures += id }).reconcile()
        assertEquals(listOf(FIRST), failures)
        assertNull(store.current(FIRST)?.finalResult)
        assertTrue(transport.sentReceipts.isEmpty())
        assertEquals(1, transport.items.size)
        persistence.failWrite = false
        QuickStartPhoneTransportRecovery(QuickStartPhoneStore(persistence), transport).reconcile()
        assertEquals(result, store.current(FIRST)?.finalResult)
        assertEquals(1, transport.sentReceipts.size)
    }

    @Test fun `failed terminal cleanup retries after reopen without blocking another record`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        val first = seed(store, FIRST, completed = false)
        val second = seed(store, SECOND, completed = false)
        val transport = MemoryTransport()
        transport.addOffer(first)
        transport.addOffer(second)
        val blocked = PHONE to quickStartCancellationPath(FIRST)
        transport.failDelete = blocked
        val failures = mutableListOf<String>()
        QuickStartPhoneTransportRecovery(store, transport, onFailure = { id, _ -> failures += id }).reconcile()
        assertEquals(listOf(FIRST), failures)
        assertTrue(transport.items.any { it.path.endsWith(FIRST) })
        assertTrue(transport.items.none { it.path.endsWith(SECOND) })
        transport.failDelete = null
        QuickStartPhoneTransportRecovery(QuickStartPhoneStore(persistence), transport).reconcile()
        assertTrue(transport.items.isEmpty())
        assertEquals(first, store.current(FIRST))
        assertEquals(second, store.current(SECOND))
    }

    @Test fun `two consumed sessions stay consumed when phone storage reopens`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        seed(store, FIRST)
        seed(store, SECOND)
        val raw = persistence.raw
        val transport = MemoryTransport()
        repeat(2) { QuickStartPhoneTransportRecovery(QuickStartPhoneStore(persistence), transport).reconcile() }
        assertTrue(transport.sentReceipts.isEmpty())
        assertTrue(transport.items.isEmpty())
        assertEquals(raw, persistence.raw)
    }

    @Test fun `retained exact result retries the original receipt and another record survives transport failure`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val first = seed(store, FIRST)
        val second = seed(store, SECOND)
        val transport = MemoryTransport()
        transport.items += resultItem(first)
        transport.items += resultItem(second)
        transport.failSend = FIRST
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertEquals(listOf(requireNotNull(second.resultReceipt) to WATCH), transport.sentReceipts)
        transport.failSend = null
        transport.sentReceipts.clear()
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertEquals(listOf(requireNotNull(first.resultReceipt) to WATCH,
            requireNotNull(second.resultReceipt) to WATCH), transport.sentReceipts)
    }

    @Test fun `partial watch pair deletion removes only the exact owned orphan receipt`() = runTest {
        val persistence = MemoryPersistence()
        val store = QuickStartPhoneStore(persistence)
        val first = seed(store, FIRST)
        seed(store, SECOND)
        val transport = MemoryTransport()
        val orphan = receiptItem(first)
        val unrelated = listOf(orphan.copy(nodeId = "other-phone"), orphan.copy(path = orphan.path + "/extra"),
            QuickStartTransportItem(WATCH, "/unrelated", "preserve"))
        transport.items += orphan
        transport.items += unrelated
        transport.failDelete = orphan.nodeId to orphan.path
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertTrue(transport.items.contains(orphan))
        transport.failDelete = null
        QuickStartPhoneTransportRecovery(QuickStartPhoneStore(persistence), transport).reconcile()
        assertEquals(unrelated, transport.items)
        assertTrue(transport.sentReceipts.isEmpty())
    }

    @Test fun `conflicting or unreadable retained result cannot authorize receipt or deletion`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val record = seed(store, FIRST)
        val original = resultItem(record)
        val result = requireNotNull(record.finalResult)
        val variants = listOf(
            original.copy(payload = "{"),
            original.copy(payload = resultPayload(result, "other-watch")),
            original.copy(payload = Json.encodeToString(QuickStartResultEnvelope(QUICK_START_RESULT_SCHEMA_VERSION,
                WATCH, result.copy(outcomeRevision = result.outcomeRevision + 1)))),
            original.copy(payload = resultPayload(result.copy(summary = requireNotNull(result.summary).copy(
                snapshot = result.snapshot.copy(elapsedActiveSeconds = 11))))),
            original.copy(payload = resultPayload(result.copy(phoneNodeId = "other-phone"))),
            original.copy(payload = resultPayload(result.copy(resultId = "different-result"))),
        )
        for (variant in variants) {
            val transport = MemoryTransport()
            transport.items += variant
            transport.items += receiptItem(record)
            val before = transport.items.toList()
            QuickStartPhoneTransportRecovery(store, transport).reconcile()
            assertEquals(before, transport.items)
            assertTrue(transport.sentReceipts.isEmpty())
        }
    }

    @Test fun `foreign node and receipt identity cannot authorize orphan deletion`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val record = seed(store, FIRST)
        val original = receiptItem(record)
        val receipt = requireNotNull(record.resultReceipt)
        val variants = listOf(
            original.copy(payload = "{"),
            original.copy(payload = receiptPayload(receipt.copy(receivedAtMillis = receipt.receivedAtMillis + 1))),
            original.copy(payload = receiptPayload(receipt.copy(outcomeRevision = receipt.outcomeRevision + 1))),
            original.copy(payload = receiptPayload(receipt, "other-watch")),
            original.copy(nodeId = "other-phone"),
            original.copy(path = original.path + "/extra"),
        )
        for (variant in variants) {
            val transport = MemoryTransport()
            transport.items += variant
            transport.items += resultItem(record).copy(nodeId = "other-watch")
            val before = transport.items.toList()
            QuickStartPhoneTransportRecovery(store, transport).reconcile()
            assertEquals(before, transport.items)
            assertTrue(transport.sentReceipts.isEmpty())
        }
        val replacedPhone = MemoryTransport().apply {
            nodeId = "replacement-phone"
            items += original
            items += resultItem(record)
            addOffer(record)
        }
        val beforeReplacementRecovery = replacedPhone.items.toList()
        QuickStartPhoneTransportRecovery(store, replacedPhone).reconcile()
        assertTrue(replacedPhone.sentReceipts.isEmpty())
        assertEquals(beforeReplacementRecovery, replacedPhone.items)
    }

    @Test fun `final result without Started acknowledgement cleans the offer instead of resending cancellation`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        store.saveRequest(request(FIRST), NOW)
        val cancellation = store.prepareCancellation(FIRST, PHONE, NOW + 1)
        val record = importResult(store, FIRST)
        val transport = MemoryTransport().apply { addOffer(record) }
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertTrue(transport.items.isEmpty())
        assertTrue(transport.sentCancellations.isEmpty())
        assertEquals(cancellation, store.current(FIRST)?.cancellation)
    }

    @Test fun `pending cancellation remains retryable without pruning a Ready offer`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        store.saveRequest(request(FIRST), NOW)
        store.acceptAcknowledgement(Json.encodeToString(ack(FIRST, QuickStartStatus.READY)),
            QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + FIRST, WATCH)
        val cancellation = store.prepareCancellation(FIRST, PHONE, NOW + 1)
        val record = requireNotNull(store.current(FIRST))
        val transport = MemoryTransport().apply { addOffer(record) }
        val before = transport.items.toList()
        QuickStartPhoneTransportRecovery(store, transport).reconcile()
        assertEquals(listOf(cancellation), transport.sentCancellations)
        assertEquals(before, transport.items)
    }

    @Test fun `coroutine cancellation stops recovery and is not reported as an item failure`() = runTest {
        val store = QuickStartPhoneStore(MemoryPersistence())
        val record = seed(store, FIRST)
        seed(store, SECOND)
        val transport = MemoryTransport().apply {
            items += resultItem(record)
            cancelSend = true
        }
        val failures = mutableListOf<String>()
        try {
            QuickStartPhoneTransportRecovery(store, transport, onFailure = { id, _ -> failures += id }).reconcile()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(failures.isEmpty())
        assertTrue(transport.sentReceipts.isEmpty())
    }

    private suspend fun seed(store: QuickStartPhoneStore, id: String, completed: Boolean = true): PhoneQuickStartRecord {
        store.saveRequest(request(id), NOW)
        store.acceptAcknowledgement(Json.encodeToString(ack(id,
            if (completed) QuickStartStatus.STARTED else QuickStartStatus.CANCELLED)),
            QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + id, WATCH)
        return if (completed) importResult(store, id) else requireNotNull(store.current(id))
    }

    private suspend fun importResult(store: QuickStartPhoneStore, id: String): PhoneQuickStartRecord {
        val result = result(id)
        return (store.importResult(resultPayload(result), quickStartResultPath(id, result.resultId),
            WATCH, PHONE, NOW + 20) as PhoneQuickStartResultImport.Imported).record
    }

    private fun request(id: String) = QuickStartRequest(requestId = id,
        createdAtMillis = NOW, expiresAtMillis = NOW + 300_000, targetNodeId = WATCH,
        source = QuickStartSource.SINGLE,
        exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 1, "10", 60)))
    private fun ack(id: String, status: QuickStartStatus) = QuickStartAcknowledgement(
        id, if (status == QuickStartStatus.READY) 1 else 2, WATCH, status, watchUpdatedAtMillis = NOW + 1)
    private fun result(id: String) = FinalQuickStartResult(id, "result-$id", 1, PHONE,
        summary = WorkoutCompletionSummary(completedAtEpochMillis = NOW + 10,
            snapshot = WorkoutProgressSnapshot(sessionId = id,
                progress = ExerciseProgressCounts(1, 1, 0, 0), completedSets = 1, plannedSets = 1,
                elapsedActiveSeconds = 10, exercises = listOf(ExerciseOutcome(
                    "item", "exercise", "Squat", ExerciseOutcomeStatus.COMPLETED, 1, 1)))))
    private fun resultPayload(result: FinalQuickStartResult, node: String = WATCH) =
        encodeQuickStartResultEnvelope(QuickStartResultEnvelope(QUICK_START_RESULT_SCHEMA_VERSION, node, result))
    private fun receiptPayload(receipt: QuickStartResultReceipt, node: String = WATCH) =
        encodeQuickStartResultReceiptEnvelope(QuickStartResultReceiptEnvelope(
            QUICK_START_RESULT_SCHEMA_VERSION, node, QuickStartResultReceiptStatus.PERSISTED, receipt))
    private fun resultItem(record: PhoneQuickStartRecord) = requireNotNull(record.finalResult).let {
        QuickStartTransportItem(WATCH, quickStartResultPath(it.requestId, it.resultId), resultPayload(it))
    }
    private fun receiptItem(record: PhoneQuickStartRecord) = requireNotNull(record.resultReceipt).let {
        QuickStartTransportItem(PHONE, quickStartResultReceiptPath(it.requestId, it.resultId), receiptPayload(it))
    }

    private class MemoryPersistence : QuickStartPhonePersistence {
        var raw: String? = null
        var failWrite = false
        override suspend fun read() = raw
        override suspend fun write(raw: String) {
            if (failWrite) throw IOException("Phone storage unavailable")
            this.raw = raw
        }
    }

    private class MemoryTransport : QuickStartPhoneTransport {
        var nodeId = PHONE
        val items = mutableListOf<QuickStartTransportItem>()
        val sentReceipts = mutableListOf<Pair<QuickStartResultReceipt, String>>()
        val sentCancellations = mutableListOf<QuickStartCancellation>()
        var failDelete: Pair<String, String>? = null
        var failSend: String? = null
        var cancelSend = false
        override suspend fun localNodeId() = nodeId
        override suspend fun dataItems() = items.toList()
        override suspend fun sendCancellation(cancellation: QuickStartCancellation) { sentCancellations += cancellation }
        override suspend fun sendResultReceipt(receipt: QuickStartResultReceipt, watchNodeId: String) {
            if (cancelSend) throw CancellationException("Recovery cancelled")
            if (failSend == receipt.requestId) throw IOException("Receipt transport unavailable")
            sentReceipts += receipt to watchNodeId
        }
        override suspend fun deleteItem(nodeId: String, path: String) {
            if (failDelete == nodeId to path) throw IOException("Deletion unavailable")
            items.removeAll { it.nodeId == nodeId && it.path == path }
        }
        override suspend fun cleanupTerminalOffer(requestId: String, watchNodeId: String) {
            deleteItem(nodeId, QuickStartDataLayerPaths.REQUEST_PREFIX + requestId)
            deleteItem(nodeId, quickStartCancellationPath(requestId))
            deleteItem(watchNodeId, QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + requestId)
        }
        fun addOffer(record: PhoneQuickStartRecord) {
            items += QuickStartTransportItem(PHONE, QuickStartDataLayerPaths.REQUEST_PREFIX + record.request.requestId, "offer")
            items += QuickStartTransportItem(PHONE, quickStartCancellationPath(record.request.requestId), "cancel")
            items += QuickStartTransportItem(WATCH, QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + record.request.requestId, "ack")
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val FIRST = "123e4567-e89b-12d3-a456-426614174000"
        const val SECOND = "123e4567-e89b-12d3-a456-426614174001"
        const val PHONE = "phone-1"
        const val WATCH = "watch-1"
    }
}
