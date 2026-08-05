package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.WorkoutSetPayload
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phone-side sender for [DataLayerPaths.WORKOUT_SET] (Prompt 6, req 4).
 *
 * Single paired watch only — no multi-device broadcast logic, per the
 * prompt's scope note.
 */
object WearSyncClient {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends [payload] to the paired watch. Never throws — all failures
     * (including "no connected node") come back as [Result.failure] so
     * callers (here, [WorkoutRequestListenerService]) can log/handle them
     * instead of crashing a background listener service.
     */
    suspend fun sendWorkoutSet(context: Context, payload: WorkoutSetPayload): Result<Unit> =
        runCatching {
            val nodeClient = Wearable.getNodeClient(context)
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                // No paired watch node reachable right now. Named to match
                // what the watch side would show the user if a request of
                // its own times out (see WearSyncClient.requestWorkout in
                // :wear, Phase E) — from the phone's side this just means
                // the reply can't be delivered, so give up cleanly rather
                // than hang a background listener service.
                throw IllegalStateException("No connected watch node — workout set not delivered")
            }

            val request = PutDataMapRequest.create(DataLayerPaths.WORKOUT_SET).apply {
                dataMap.putString("payload", json.encodeToString(payload))
                // DataClient only fires a change event when the payload actually
                // differs from the last one stored at this path; stamping the
                // send time guarantees a "download now" against an unchanged
                // schedule still triggers a fresh delivery.
                dataMap.putLong("sentAtMillis", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Unit
        }
}
