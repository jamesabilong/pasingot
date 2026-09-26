package app.personal.workouttracker.quickstart

import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A watch Ready/Started state becomes visible only after this durable write. */
class QuickStartAckListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(applicationContext))
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val path = item.uri.path ?: continue
            if (!path.startsWith(QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX)) continue
            val sender = item.uri.host ?: continue
            val payload = try { DataMapItem.fromDataItem(item).dataMap.getString("payload") }
            catch (error: Exception) {
                Log.w("QuickStartAck", "Unreadable watch acknowledgement", error)
                null
            } ?: continue
            try {
                val decision = runBlocking(Dispatchers.IO) {
                    withTimeout(10_000) { store.acceptAcknowledgement(payload, path, sender) }
                }
                if (decision == PhoneAcknowledgementResult.RECORDED) {
                    QuickStartPhoneEvents.publish(path.removePrefix(QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX))
                }
            } catch (error: Exception) {
                Log.e("QuickStartAck", "Could not store watch acknowledgement", error)
            }
        }
    }
}

/** Live UI hint; persistent getQuickStartStatus remains authoritative on resume. */
object QuickStartPhoneEvents {
    @Volatile var onChange: ((String) -> Unit)? = null
    fun publish(requestId: String) { onChange?.invoke(requestId) }
}
