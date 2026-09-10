package app.personal.workouttracker.wear.data

import android.content.Context
import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WatchSessionSnapshot
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.wear.download.LogFlushWorker
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** Persists before sending, and serializes app/worker drains so they cannot drop each other's queue. */
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
        deliveryMutex.withLock {
            queue.enqueue(entry)
            LogFlushWorker.scheduleRetry(context)
            flushLocked()
        }
    }

    override suspend fun sendSessionEvent(event: WorkoutSessionEvent) {
        deliveryMutex.withLock {
            sessionEventQueue.enqueue(event)
            LogFlushWorker.scheduleRetry(context)
            flushLocked()
        }
    }

    override suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) {
        deliveryMutex.withLock {
            sessionEventQueue.setPendingSnapshot(snapshot)
            LogFlushWorker.scheduleRetry(context)
            flushLocked()
        }
    }

    /** False keeps the one-time retry worker alive; an empty queue makes no radio calls. */
    suspend fun flushQueue(): Boolean = deliveryMutex.withLock { flushLocked() }

    private suspend fun flushLocked(): Boolean {
        // Live state uses a persistent DataItem, so Play services handles disconnected peers.
        // Keep a fallback locally only until Play services accepts the item.
        val snapshot = sessionEventQueue.pendingSnapshot.first()
        var snapshotSent = true
        if (snapshot != null) {
            snapshotSent = trySendSnapshot(snapshot)
            if (snapshotSent) sessionEventQueue.setPendingSnapshot(null)
        }

        val pending = queue.queuedEntries.first()
        val pendingSessionEvents = sessionEventQueue.queuedEntries.first()
        if (pending.isEmpty() && pendingSessionEvents.isEmpty()) return snapshotSent

        val nodes = try {
            Wearable.getNodeClient(context).connectedNodes.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not find connected phone", error)
            return false
        }
        if (nodes.isEmpty()) return false
        val nodeIds = nodes.map { it.id }

        val historySent = flushQueuedHistory(
            pending,
            pendingSessionEvents,
            sendLog = { trySendBytes(nodeIds, DataLayerPaths.LOG, json.encodeToString(it)) },
            sendSessionEvent = { trySendBytes(nodeIds, DataLayerPaths.SESSION_EVENT, json.encodeToString(it)) },
            removeLogs = queue::removeSentPrefix,
            removeSessionEvents = sessionEventQueue::removeSentPrefix,
        )
        return snapshotSent && historySent
    }

    private suspend fun trySendSnapshot(snapshot: WatchSessionSnapshot): Boolean = try {
        val request = PutDataMapRequest.create(DataLayerPaths.SESSION_STATE).apply {
            dataMap.putString("payload", json.encodeToString(snapshot))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Failed to publish live watch session", error)
        false
    }

    private suspend fun trySendBytes(nodeIds: List<String>, path: String, payload: String): Boolean = try {
        val messageClient = Wearable.getMessageClient(context)
        val bytes = payload.toByteArray(Charsets.UTF_8)
        for (nodeId in nodeIds) messageClient.sendMessage(nodeId, path, bytes).await()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Failed to send queued watch data on $path", error)
        false
    }

    companion object {
        private const val TAG = "LogSyncManager"
        private val deliveryMutex = Mutex()
    }
}

/** Drain both histories independently, preserving each unsent suffix on failure. */
internal suspend fun flushQueuedHistory(
    logs: List<LogEntry>,
    sessionEvents: List<WorkoutSessionEvent>,
    sendLog: suspend (LogEntry) -> Boolean,
    sendSessionEvent: suspend (WorkoutSessionEvent) -> Boolean,
    removeLogs: suspend (Int) -> Unit,
    removeSessionEvents: suspend (Int) -> Unit,
): Boolean {
    var sentLogs = 0
    for (entry in logs) {
        if (sendLog(entry)) sentLogs += 1 else break
    }
    removeLogs(sentLogs)

    // A workout ended before completing any set still has a session event to deliver.
    var sentEvents = 0
    for (event in sessionEvents) {
        if (sendSessionEvent(event)) sentEvents += 1 else break
    }
    removeSessionEvents(sentEvents)
    return sentLogs == logs.size && sentEvents == sessionEvents.size
}
