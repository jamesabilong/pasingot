package app.personal.workouttracker.weardata

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Prompt 6, req 2-3: listens on [DataLayerPaths.REQUEST_WORKOUT] for a
 * download request from the watch — manual "Download Now" and the scheduled
 * WorkManager job both hit this same path (there's no date field on the
 * request; it always means "today", per the resolved request-scope decision).
 *
 * Manifest-registered (see AndroidManifest.xml) so this fires even when the
 * phone app isn't foregrounded — the scheduled-download path in particular
 * has no user with the app open.
 */
class WorkoutRequestListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != DataLayerPaths.REQUEST_WORKOUT) return

        val payload = ScheduleCache(applicationContext).todaysWorkout()

        // onMessageReceived runs on a binder thread with no guaranteed
        // lifetime beyond this call; launch on a service-scoped coroutine
        // rather than blocking here, and just log failures — there's no
        // request-response channel back to the watch beyond the
        // WORKOUT_SET data item itself.
        serviceScope.launch {
            val result = WearSyncClient.sendWorkoutSet(applicationContext, payload)
            result.onFailure { Log.e(TAG, "Failed to deliver workout set for ${payload.date}", it) }
        }
    }

    companion object {
        private const val TAG = "WorkoutRequestListener"
    }
}
