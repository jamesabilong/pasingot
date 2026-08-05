package app.personal.workouttracker.wear.data

import android.content.Context
import app.personal.workouttracker.shared.DataLayerPaths
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Watch-side counterpart to :app's WearSyncClient (Prompt 6). Sends the
 * "/request-workout" message — used by both the manual "Download Now"
 * action and the scheduled WorkManager job (Prompt 5 req 1-2); there's no
 * date field, the request always means "today".
 *
 * This only confirms the request was *sent* — the actual workout set
 * arrives asynchronously via [WorkoutSetListenerService] on "/workout-set"
 * and is added to storage from there, not synchronously here.
 */
object WearSyncClient {

    suspend fun requestWorkout(context: Context): Result<Unit> = runCatching {
        val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (connectedNodes.isEmpty()) {
            // This is the check the spec's "so the watch can show that
            // instead of hanging" language actually refers to: the watch
            // itself fails fast here rather than sending into the void.
            throw IllegalStateException("Phone unreachable — no connected node")
        }
        val messageClient = Wearable.getMessageClient(context)
        for (node in connectedNodes) {
            messageClient.sendMessage(node.id, DataLayerPaths.REQUEST_WORKOUT, ByteArray(0)).await()
        }
        Unit
    }
}
