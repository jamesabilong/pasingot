package app.personal.workouttracker.quickstart

import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
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
                            when (val imported = store.importResult(
                                payload, path, sender, localNodeId, System.currentTimeMillis(),
                            )) {
                                is PhoneQuickStartResultImport.Imported -> {
                                    client.sendResultReceipt(requireNotNull(imported.record.resultReceipt), sender)
                                    QuickStartPhoneEvents.publish(imported.record.request.requestId)
                                }
                                is PhoneQuickStartResultImport.Duplicate ->
                                    client.sendResultReceipt(requireNotNull(imported.record.resultReceipt), sender)
                                PhoneQuickStartResultImport.UnknownRequest,
                                PhoneQuickStartResultImport.Invalid,
                                PhoneQuickStartResultImport.Conflict -> Unit
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
