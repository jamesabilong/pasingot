package app.personal.workouttracker.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartCancellation
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartResultDecodeResult
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.decodeQuickStartResult
import app.personal.workouttracker.shared.quickstart.decodeQuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.quickStartResultPath
import app.personal.workouttracker.shared.quickstart.quickStartResultReceiptPath
import kotlinx.coroutines.CancellationException

/** A copied Data Item identity/payload; no native buffer escapes the adapter. */
data class QuickStartTransportItem(val nodeId: String, val path: String, val payload: String?)

interface QuickStartPhoneTransport {
    suspend fun localNodeId(): String
    suspend fun dataItems(): List<QuickStartTransportItem>
    suspend fun cleanupTerminalOffer(requestId: String, watchNodeId: String)
    suspend fun sendCancellation(cancellation: QuickStartCancellation)
    suspend fun sendResultReceipt(receipt: QuickStartResultReceipt, watchNodeId: String)
    suspend fun deleteItem(nodeId: String, path: String)
}

/** Reconcile durable phone decisions with retained transport, without reviving consumed history. */
class QuickStartPhoneTransportRecovery(
    private val store: QuickStartPhoneStore,
    private val transport: QuickStartPhoneTransport,
    private val onFailure: (String, Exception) -> Unit = { _, _ -> },
    private val onImported: (String) -> Unit = {},
) {
    suspend fun reconcile() {
        val records = store.recordsForTransportRecovery()
        if (records.isEmpty()) return
        val localNodeId = transport.localNodeId()
        val items = transport.dataItems()
        for (record in records) {
            val boundPhoneNodeId = record.resultReceipt?.phoneNodeId ?: record.cancellation?.phoneNodeId
            if (boundPhoneNodeId != null && boundPhoneNodeId != localNodeId) continue
            var recovered = record
            // A listener may have been interrupted before its durable import.
            // Retained watch-owned results use the same validation/write boundary.
            for (item in items.filter { it.nodeId == record.request.targetNodeId &&
                it.path.startsWith(QuickStartDataLayerPaths.RESULT_PREFIX + record.request.requestId + "/") }) {
                val payload = item.payload ?: continue
                attempt(record.request.requestId) {
                    when (val imported = store.importResult(payload, item.path, item.nodeId,
                        localNodeId, System.currentTimeMillis())) {
                        is PhoneQuickStartResultImport.Imported -> {
                            recovered = imported.record
                            onImported(record.request.requestId)
                        }
                        is PhoneQuickStartResultImport.Duplicate -> recovered = imported.record
                        else -> Unit
                    }
                }
            }
            val terminal = recovered.finalResult != null ||
                recovered.acknowledgement?.let { it.status != QuickStartStatus.READY } == true
            if (terminal) attempt(record.request.requestId) {
                transport.cleanupTerminalOffer(record.request.requestId, record.request.targetNodeId)
            } else recovered.cancellation?.let { cancellation ->
                attempt(record.request.requestId) { transport.sendCancellation(cancellation) }
            }
            attempt(record.request.requestId) { reconcileReceipt(recovered, localNodeId, items) }
        }
    }

    private suspend fun reconcileReceipt(
        record: PhoneQuickStartRecord,
        localNodeId: String,
        items: List<QuickStartTransportItem>,
    ) {
        val receipt = record.resultReceipt ?: return
        if (receipt.phoneNodeId != localNodeId) return
        val watchNodeId = record.request.targetNodeId
        val resultPath = quickStartResultPath(receipt.requestId, receipt.resultId)
        val retainedResult = items.firstOrNull { it.nodeId == watchNodeId && it.path == resultPath }
        if (retainedResult != null) {
            val decoded = retainedResult.payload?.let {
                decodeQuickStartResult(it, resultPath, watchNodeId, record.request, localNodeId)
            }
            if (decoded is QuickStartResultDecodeResult.Accepted && decoded.value == record.finalResult) {
                transport.sendResultReceipt(receipt, watchNodeId)
            }
            // An unreadable/conflicting retained result cannot prove cleanup is safe.
            return
        }
        val receiptPath = quickStartResultReceiptPath(receipt.requestId, receipt.resultId)
        val retainedReceipt = items.firstOrNull { it.nodeId == localNodeId && it.path == receiptPath } ?: return
        val decoded = retainedReceipt.payload?.let {
            decodeQuickStartResultReceipt(it, receiptPath, localNodeId, receipt, watchNodeId)
        }
        if (decoded is QuickStartResultDecodeResult.Accepted) {
            // The watch deletes the result only after durable pruning. Finish a
            // partially deleted pair even if a newer watch runtime replaced its tombstone.
            transport.deleteItem(localNodeId, receiptPath)
        }
    }

    private suspend fun attempt(requestId: String, operation: suspend () -> Unit) {
        try { operation() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { onFailure(requestId, error) }
    }
}
