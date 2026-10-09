package app.personal.workouttracker.quickstart

import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.voice.PhoneVoiceCueService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Durably imports a watch result before publishing its exact receipt. */
class QuickStartResultListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val context = applicationContext
            val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
            val client = WatchQuickStartClient(context)
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                if (!path.startsWith(QuickStartDataLayerPaths.RESULT_PREFIX)) continue
                val sender = item.uri.host ?: continue
                val payload = try { DataMapItem.fromDataItem(item).dataMap.getString("payload") }
                catch (error: Exception) {
                    Log.w(TAG, "Unreadable Quick Start result", error)
                    null
                } ?: continue
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(10_000) {
                            val localNodeId = Wearable.getNodeClient(context).localNode.await().id
                            val imported = store.importResult(
                                payload, path, sender, localNodeId, System.currentTimeMillis(),
                            )
                            val record = when (imported) {
                                is PhoneQuickStartResultImport.Imported -> imported.record
                                is PhoneQuickStartResultImport.Duplicate -> imported.record
                                PhoneQuickStartResultImport.UnknownRequest,
                                PhoneQuickStartResultImport.Invalid,
                                PhoneQuickStartResultImport.Conflict -> null
                            }
                            if (record != null) {
                                // Success speech can race the durable final result/receipt.
                                PhoneVoiceCueService.terminal(record.request.requestId, 6_000)
                                // A durable validated final result also proves the offer is
                                // terminal, including when its Started acknowledgement was missed.
                                try { client.cleanupTerminalOffer(record.request.requestId, sender) }
                                catch (error: CancellationException) { throw error }
                                catch (error: Exception) { Log.w(TAG, "Could not clean up terminal offer", error) }
                                client.sendResultReceipt(requireNotNull(record.resultReceipt), sender)
                                if (imported is PhoneQuickStartResultImport.Imported) {
                                    QuickStartPhoneEvents.publish(record.request.requestId)
                                }
                            }
                        }
                    }
                } catch (error: Exception) {
                    // The retained result Data Item or app-resume replay retries the exact receipt.
                    Log.e(TAG, "Could not import Quick Start result", error)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private companion object { const val TAG = "QuickStartResult" }
}
