package app.personal.workouttracker.weardata

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.WatchSessionSnapshot
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Receives live state even when the phone activity is closed. */
class SessionStateListenerService : WearableListenerService() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != DataLayerPaths.SESSION_STATE) continue
            try {
                val payload = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("payload") ?: continue
                val snapshot = json.decodeFromString<WatchSessionSnapshot>(payload)
                if (WatchSessionStore(applicationContext).save(snapshot)) WatchDataUpdates.notifyChanged()
            } catch (error: Exception) {
                Log.e("SessionStateListener", "Could not store live watch session", error)
            }
        }
        // WearableListenerService owns and releases the callback's DataEventBuffer.
    }
}
