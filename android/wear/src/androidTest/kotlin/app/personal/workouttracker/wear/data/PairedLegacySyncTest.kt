package app.personal.workouttracker.wear.data

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.personal.workouttracker.shared.*
import app.personal.workouttracker.wear.download.ScheduleDownloadWorker
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartPackagePersistence
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartRuntimePersistence
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
private data class LegacyFixture(val marker: String, val payload: WorkoutSetPayload, val timestamp: String)

/** Opt-in real Send today/download/worker/log/state transport, preserving active legacy progress. */
@RunWith(AndroidJUnit4::class)
class PairedLegacySyncTest {
    @Test fun legacyTransportSurvivesQuickStartRecovery() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("legacyPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val local = Wearable.getNodeClient(context).localNode.await().id
        val repository = WorkoutRepository(context)
        val beforeEntries = repository.entries.first()
        assertTrue("Need a free cache slot to avoid eviction", beforeEntries.size < MAX_STORED_WORKOUTS)
        val queue = LogQueueRepository(context)
        val eventQueue = SessionEventQueueRepository(context)
        assertTrue("Preserve pending user logs", queue.queuedEntries.first().isEmpty())
        assertTrue("Preserve pending user session events", eventQueue.queuedEntries.first().isEmpty())
        assertNull("Preserve pending user snapshot", eventQueue.pendingSnapshot.first())
        val packages = DataStoreQuickStartPackagePersistence(context)
        val runtime = DataStoreQuickStartRuntimePersistence(context)
        val beforePackages = packages.read()
        val beforeRuntime = runtime.read()
        val data = Wearable.getDataClient(context)
        val buffer = data.dataItems.await()
        val oldSnapshot = try { buffer.firstOrNull { it.uri.host == local && it.uri.path == DataLayerPaths.SESSION_STATE }
            ?.let { DataMapItem.fromDataItem(it).dataMap.toByteArray() } } finally { buffer.release() }
        val messages = Wearable.getMessageClient(context)
        val replies = Channel<String>(Channel.UNLIMITED)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId == peer && event.path == REPLY) replies.trySend(event.data.toString(Charsets.UTF_8))
        }
        messages.addListener(listener).await()
        suspend fun probe(command: String): String {
            messages.sendMessage(peer, PROBE, command.toByteArray()).await()
            return withTimeout(25_000) { replies.receive() }.also { assertFalse(it, it.startsWith("error:")) }
        }
        suspend fun workoutStamp(): Long? {
            val items = data.dataItems.await()
            return try { items.firstOrNull { it.uri.host == peer && it.uri.path == DataLayerPaths.WORKOUT_SET }
                ?.let { DataMapItem.fromDataItem(it).dataMap.getLong("sentAtMillis") } } finally { items.release() }
        }
        suspend fun awaitDownload(priorStamp: Long?, priorFeedback: Long?) = withTimeout(15_000) {
            while (workoutStamp() == priorStamp || repository.downloadFeedback.value?.revision == priorFeedback) delay(100)
        }
        var fixture: LegacyFixture? = null
        var workerId: java.util.UUID? = null
        try {
            val prepared = Json.decodeFromString<LegacyFixture>(probe("prepare"))
            fixture = prepared
            assertTrue("Today's cache must be empty for an isolated download fixture",
                beforeEntries.none { it.id == prepared.payload.date })
            val originalStamp = workoutStamp()
            val originalFeedback = repository.downloadFeedback.value?.revision
            assertEquals("pushed", probe("push"))
            awaitDownload(originalStamp, originalFeedback)
            withTimeout(15_000) {
                while (repository.getEntry(prepared.payload.date) == null) delay(100)
            }
            val entry = requireNotNull(repository.getEntry(prepared.payload.date))
            assertEquals(prepared.payload.exercises, entry.exercises)
            assertNull(entry.sessionState)
            assertEquals(beforeEntries, repository.entries.first().filterNot { it.id == entry.id })

            val manualStamp = workoutStamp()
            val manualFeedback = repository.downloadFeedback.value?.revision
            WearSyncClient.requestWorkout(context).getOrThrow()
            awaitDownload(manualStamp, manualFeedback)
            assertEquals(entry, repository.getEntry(entry.id))

            val scheduledStamp = workoutStamp()
            val scheduledFeedback = repository.downloadFeedback.value?.revision
            val work = OneTimeWorkRequestBuilder<ScheduleDownloadWorker>().build()
            workerId = work.id
            val manager = WorkManager.getInstance(context)
            manager.enqueue(work).result.get(5, TimeUnit.SECONDS)
            withTimeout(25_000) {
                while (manager.getWorkInfoById(work.id).get(5, TimeUnit.SECONDS)?.state != WorkInfo.State.SUCCEEDED) delay(100)
            }
            awaitDownload(scheduledStamp, scheduledFeedback)
            assertEquals(entry, repository.getEntry(entry.id))
            assertTrue(manager.getWorkInfosForUniqueWork("scheduled_workout_download").get(5, TimeUnit.SECONDS)
                .any { it.state == WorkInfo.State.ENQUEUED })

            val log = LogEntry(exercise = prepared.marker, status = LogStatus.DONE, timestamp = prepared.timestamp)
            val event = WorkoutSessionEvent(workoutEntryId = prepared.marker, workoutDate = prepared.payload.date,
                eventType = SessionEventType.COMPLETED, stopReason = SessionStopReason.COMPLETED,
                timestamp = prepared.timestamp, elapsedSeconds = 10, exerciseIndex = 1,
                currentSet = 1, totalExercises = 2, currentExercise = "${prepared.marker}-second")
            val sender = LogSyncManager(context)
            sender.sendEntry(log)
            sender.sendEntry(log)
            sender.sendSessionEvent(event)
            sender.sendSessionEvent(event)
            assertEquals("history:passed", probe("history"))
            assertTrue(queue.queuedEntries.first().isEmpty())
            assertTrue(eventQueue.queuedEntries.first().isEmpty())
            sender.sendSessionSnapshot(WatchSessionSnapshot(workoutEntryId = prepared.marker,
                workoutDate = prepared.payload.date, status = SessionStatus.ACTIVE, timestamp = prepared.timestamp,
                exerciseIndex = 0, currentSet = 1, totalExercises = 2,
                currentExercise = prepared.marker, elapsedSeconds = 10))
            assertEquals("snapshot:passed", probe("snapshot"))
            assertNull(eventQueue.pendingSnapshot.first())
            assertEquals(beforePackages, packages.read())
            assertEquals(beforeRuntime, runtime.read())
        } finally {
            if (oldSnapshot == null) data.deleteDataItems(Uri.parse("wear://$local${DataLayerPaths.SESSION_STATE}")).await()
            else data.putDataItem(PutDataMapRequest.create(DataLayerPaths.SESSION_STATE).apply {
                dataMap.putAll(DataMap.fromByteArray(oldSnapshot))
            }.asPutDataRequest().setUrgent()).await()
            try { assertEquals("finished", probe("finish")) }
            finally {
                workerId?.let { WorkManager.getInstance(context).cancelWorkById(it).result.get(5, TimeUnit.SECONDS) }
                fixture?.let { value ->
                    repository.getEntry(value.payload.date)?.takeIf { it.exercises == value.payload.exercises && it.sessionState == null }
                        ?.let { repository.deleteEntry(it.id) }
                }
                messages.removeListener(listener).await()
                replies.close()
            }
            assertEquals(beforeEntries, repository.entries.first())
        }
    }

    companion object {
        const val PROBE = "/validation/legacy/probe"
        const val REPLY = "/validation/legacy/reply"
    }
}
