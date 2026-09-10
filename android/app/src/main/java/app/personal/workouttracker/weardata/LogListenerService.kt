package app.personal.workouttracker.weardata

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.LogEntry
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

/**
 * Prompt 8 req 3-4: listens on [DataLayerPaths.LOG] for a completed/skipped
 * exercise from the watch. Manifest-registered (see AndroidManifest.xml) so
 * it works even when the phone app isn't foregrounded.
 *
 * Stages into [PendingLogsStore] rather than writing straight into the
 * PWA's IndexedDB `logs` store — a background Kotlin service has no way to
 * reach into the WebView's JS/IndexedDB context. [WorkoutLogBridgePlugin]
 * is what actually gets these into IndexedDB, from the JS side, on resume.
 */
class LogListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != DataLayerPaths.LOG) return

        try {
            val entry = json.decodeFromString(LogEntry.serializer(), String(event.data, Charsets.UTF_8))
            PendingLogsStore(applicationContext).addPending(entry)
            WatchDataUpdates.notifyChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Malformed /log payload", e)
        }
    }

    companion object {
        private const val TAG = "LogListenerService"
    }
}
