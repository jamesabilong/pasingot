package app.personal.workouttracker.wear.data

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.WorkoutSetPayload
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Receives the phone's reply to "/request-workout" (Prompt 6) on
 * "/workout-set" and adds it to local storage — this is where the
 * duplicate-date skip and cap/eviction rules (Prompt 5 req 2-3) actually
 * take effect, since [WorkoutRepository.addDownload] is the single call site
 * for both the manual and scheduled download paths.
 */
class WorkoutSetListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                if (item.uri.path != DataLayerPaths.WORKOUT_SET) continue

                val payloadJson = DataMapItem.fromDataItem(item).dataMap.getString("payload") ?: continue
                val payload = try {
                    json.decodeFromString(WorkoutSetPayload.serializer(), payloadJson)
                } catch (e: Exception) {
                    Log.e(TAG, "Malformed workout set payload", e)
                    continue
                }

                serviceScope.launch {
                    val repository = WorkoutRepository(applicationContext)
                    when (val result = repository.addDownload(payload)) {
                        is AddResult.Added -> Log.i(TAG, "Added workout for ${payload.date}")
                        is AddResult.SkippedDuplicateDate -> Log.i(TAG, "Skipped duplicate date ${payload.date}")
                        AddResult.Blocked -> {
                            Log.w(TAG, "Download blocked — cap reached, nothing evictable")
                            NotificationHelper.notifyBlockedDownload(applicationContext)
                        }
                    }
                }
            }
        } finally {
            // DataEventBuffer is a Releasable — must be released to free the
            // underlying native resources, per the Wearable Data Layer API.
            dataEvents.release()
        }
    }

    companion object {
        private const val TAG = "WorkoutSetListener"
    }
}
