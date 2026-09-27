package app.personal.workouttracker.wear.quickstart

import android.content.Context
import android.net.Uri
import android.util.Log
import app.personal.workouttracker.shared.quickstart.QUICK_START_RESULT_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartResultEnvelope
import app.personal.workouttracker.shared.quickstart.encodeQuickStartResultEnvelope
import app.personal.workouttracker.shared.quickstart.quickStartResultPath
import app.personal.workouttracker.shared.quickstart.quickStartResultReceiptPath
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

fun interface QuickStartResultClient {
    suspend fun send(result: FinalQuickStartResult)
}

class DataLayerQuickStartResultClient(private val context: Context) : QuickStartResultClient {
    override suspend fun send(result: FinalQuickStartResult) {
        val localNodeId = Wearable.getNodeClient(context).localNode.await().id
        val payload = encodeQuickStartResultEnvelope(QuickStartResultEnvelope(
            schemaVersion = QUICK_START_RESULT_SCHEMA_VERSION,
            watchNodeId = localNodeId,
            result = result,
        ))
        val item = PutDataMapRequest.create(
            quickStartResultPath(result.requestId, result.resultId),
        ).apply { dataMap.putString("payload", payload) }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }
}

sealed interface QuickStartResultCleanupResult {
    data class Pruned(val receipt: QuickStartResultReceipt) : QuickStartResultCleanupResult
    data class AlreadyPruned(val receipt: QuickStartResultReceipt) : QuickStartResultCleanupResult
    data object Missing : QuickStartResultCleanupResult
    data object Mismatch : QuickStartResultCleanupResult
}

class QuickStartRuntimeResultCoordinator(
    private val runtime: QuickStartRuntimeStore,
    private val packages: QuickStartPackageStore,
) {
    suspend fun acknowledgePayloadAndPrune(
        payload: String,
        path: String,
        observedPhoneNodeId: String,
        localWatchNodeId: String,
    ): QuickStartResultCleanupResult {
        val cleared = runtime.clearAcknowledgedPayload(
            payload, path, observedPhoneNodeId, localWatchNodeId,
        )
        val receipt = when (cleared) {
            is ClearQuickStartRuntimePayloadResult.Cleared -> cleared.receipt
            is ClearQuickStartRuntimePayloadResult.AlreadyCleared -> cleared.receipt
            ClearQuickStartRuntimePayloadResult.Missing -> return QuickStartResultCleanupResult.Missing
            ClearQuickStartRuntimePayloadResult.Mismatch -> return QuickStartResultCleanupResult.Mismatch
        }
        return when (packages.releaseAcknowledged(receipt)) {
            is ReleaseAcknowledgedQuickStartResult.Released -> QuickStartResultCleanupResult.Pruned(receipt)
            is ReleaseAcknowledgedQuickStartResult.AlreadyReleased ->
                QuickStartResultCleanupResult.AlreadyPruned(receipt)
            ReleaseAcknowledgedQuickStartResult.Missing,
            ReleaseAcknowledgedQuickStartResult.Mismatch,
            ReleaseAcknowledgedQuickStartResult.NotStarting -> QuickStartResultCleanupResult.Mismatch
        }
    }
}

/** Exact phone receipt prunes runtime/package state before transport items are deleted. */
class QuickStartResultReceiptListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val context = applicationContext
            val coordinator = QuickStartRuntimeResultCoordinator(
                QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context)),
                WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context)),
            )
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                if (!path.startsWith(QuickStartDataLayerPaths.RESULT_RECEIPT_PREFIX)) continue
                val sender = item.uri.host ?: continue
                val payload = try { DataMapItem.fromDataItem(item).dataMap.getString("payload") }
                catch (error: Exception) {
                    Log.w(TAG, "Unreadable Quick Start result receipt", error)
                    null
                } ?: continue
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(10_000) {
                            val localNodeId = Wearable.getNodeClient(context).localNode.await().id
                            when (val result = coordinator.acknowledgePayloadAndPrune(
                                payload, path, sender, localNodeId,
                            )) {
                                is QuickStartResultCleanupResult.Pruned,
                                is QuickStartResultCleanupResult.AlreadyPruned -> {
                                    val receipt = when (result) {
                                        is QuickStartResultCleanupResult.Pruned -> result.receipt
                                        is QuickStartResultCleanupResult.AlreadyPruned -> result.receipt
                                        else -> error("unreachable")
                                    }
                                    deleteTransportItems(context, receipt, localNodeId, sender)
                                    QuickStartOfferEvents.notifyChanged()
                                }
                                QuickStartResultCleanupResult.Missing,
                                QuickStartResultCleanupResult.Mismatch -> Unit
                            }
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Could not prune Quick Start result", error)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private suspend fun deleteTransportItems(
        context: Context,
        receipt: QuickStartResultReceipt,
        localWatchNodeId: String,
        phoneNodeId: String,
    ) {
        val client = Wearable.getDataClient(context)
        val resultUri = Uri.parse(
            "wear://$localWatchNodeId${quickStartResultPath(receipt.requestId, receipt.resultId)}",
        )
        val receiptUri = Uri.parse(
            "wear://$phoneNodeId${quickStartResultReceiptPath(receipt.requestId, receipt.resultId)}",
        )
        client.deleteDataItems(resultUri).await()
        client.deleteDataItems(receiptUri).await()
    }

    private companion object { const val TAG = "QuickStartReceipt" }
}
