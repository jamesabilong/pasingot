package app.personal.workouttracker.wear.data

import android.content.Context
import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WorkoutSessionEvent
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
    private val sessionEventQueue = SessionEventQueueRepository(context)
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

    override suspend fun sendSessionEvent(event: WorkoutSessionEvent) {
        if (!trySendSessionEvent(event)) {
            sessionEventQueue.enqueue(event)
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

        val pendingSessionEvents = sessionEventQueue.queuedEntries.first()
        var sentSessionEventCount = 0
        for (event in pendingSessionEvents) {
            if (trySendSessionEvent(event)) sentSessionEventCount += 1 else break
        }
        sessionEventQueue.removeSentPrefix(sentSessionEventCount)
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

    private suspend fun trySendSessionEvent(event: WorkoutSessionEvent): Boolean = try {
        val bytes = json.encodeToString(event).toByteArray(Charsets.UTF_8)
        sendBytes(DataLayerPaths.SESSION_EVENT, bytes)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to send session event for ${event.workoutEntryId}", e)
        false
    }

    private suspend fun sendBytes(path: String, bytes: ByteArray): Boolean {
        val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (connectedNodes.isEmpty()) return false

        val messageClient = Wearable.getMessageClient(context)
        for (node in connectedNodes) {
            messageClient.sendMessage(node.id, path, bytes).await()
        }
        return true
    }

    companion object {
        private const val TAG = "LogSyncManager"
    }
}
