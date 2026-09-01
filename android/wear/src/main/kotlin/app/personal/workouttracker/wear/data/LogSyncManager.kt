package app.personal.workouttracker.wear.data

import android.content.Context
import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WorkoutExercise
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Prompt 8: sends a [LogEntry] on Complete Set/Skip, falling back to the
 * offline queue ([LogQueueRepository]) on any failure — implements
 * [LogSender], the seam [SessionScreen]/[SessionViewModel] talk to.
 */
class LogSyncManager(private val context: Context) : LogSender {

    private val queue = LogQueueRepository(context)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?) {
        val entry = LogEntry(
            exercise = exercise.exercise,
            status = status,
            timestamp = Instant.now().toString(),
            workoutRowId = workoutRowId,
        )
        if (!trySend(entry)) {
            queue.enqueue(entry)
        }
    }

    /** Drains the offline queue — called on app start and by the periodic
     *  [LogFlushWorker]. Stops at the first failure (still offline) rather
     *  than looping through remaining entries pointlessly. */
    suspend fun flushQueue() {
        val pending = queue.queuedEntries.first()
        if (pending.isEmpty()) return

        var sentCount = 0
        for (entry in pending) {
            if (trySend(entry)) sentCount += 1 else break
        }
        queue.removeSentPrefix(sentCount)
    }

    private suspend fun trySend(entry: LogEntry): Boolean = try {
        val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (connectedNodes.isEmpty()) {
            false
        } else {
            val bytes = json.encodeToString(entry).toByteArray(Charsets.UTF_8)
            val messageClient = Wearable.getMessageClient(context)
            for (node in connectedNodes) {
                messageClient.sendMessage(node.id, DataLayerPaths.LOG, bytes).await()
            }
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to send log entry for ${entry.exercise}", e)
        false
    }

    companion object {
        private const val TAG = "LogSyncManager"
    }
}
