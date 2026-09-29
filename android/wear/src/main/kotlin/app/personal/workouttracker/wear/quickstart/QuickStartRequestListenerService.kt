package app.personal.workouttracker.wear.quickstart

import android.content.Context
import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.wear.data.WorkoutRepository
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Manifest-registered receiver; a Ready receipt follows the package DataStore write. */
class QuickStartRequestListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val context = applicationContext
            val packageStore = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
            val coordinator = QuickStartRequestCoordinator(
                packageStore,
                WorkoutRepositorySessionSnapshotSource(WorkoutRepository(context)),
                DataLayerQuickStartReceiptClient(this),
                offerNotifier = AndroidQuickStartOfferNotifier(context),
            )
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                if (!path.startsWith(QuickStartDataLayerPaths.REQUEST_PREFIX)) continue
                val sender = item.uri.host ?: continue
                val payload = try { DataMapItem.fromDataItem(item).dataMap.getString("payload") }
                catch (error: Exception) {
                    Log.w(TAG, "Unreadable Quick Start request", error)
                    null
                } ?: continue
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(10_000) {
                            val localNode = Wearable.getNodeClient(context).localNode.await().id
                            coordinator.receive(payload, path, sender, localNode, System.currentTimeMillis())
                            QuickStartOfferEvents.notifyChanged()
                        }
                    }
                } catch (error: Exception) {
                    // A failed persistence/transport call must not fabricate a Ready receipt.
                    Log.e(TAG, "Could not process Quick Start request", error)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private companion object { const val TAG = "QuickStartRequest" }
}

class DataLayerQuickStartReceiptClient(private val context: Context) : QuickStartReceiptClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(acknowledgement: QuickStartAcknowledgement) {
        val item = PutDataMapRequest.create(
            QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + acknowledgement.requestId,
        ).apply {
            dataMap.putString("payload", json.encodeToString(acknowledgement))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }
}
