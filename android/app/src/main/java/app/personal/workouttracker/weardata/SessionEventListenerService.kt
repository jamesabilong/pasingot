package app.personal.workouttracker.weardata

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.WorkoutSessionEvent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

/** Receives workout-level session events from the watch and stages them for the PWA. */
class SessionEventListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != DataLayerPaths.SESSION_EVENT) return

        try {
            val sessionEvent = json.decodeFromString(
                WorkoutSessionEvent.serializer(),
                String(event.data, Charsets.UTF_8),
            )
            PendingSessionEventsStore(applicationContext).addPending(sessionEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Malformed /session-event payload", e)
        }
    }

    companion object {
        private const val TAG = "SessionEventListener"
    }
}
