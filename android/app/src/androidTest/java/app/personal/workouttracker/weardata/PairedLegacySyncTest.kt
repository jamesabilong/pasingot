package app.personal.workouttracker.weardata

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.*
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
private data class LegacyFixture(val marker: String, val payload: WorkoutSetPayload, val timestamp: String)

/** Real legacy listeners, with synthetic history and reversible phone cache/transport fixtures. */
@RunWith(AndroidJUnit4::class)
class PairedLegacySyncTest {
    @Test fun serveLegacyProbe() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("legacyPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val local = Wearable.getNodeClient(context).localNode.await().id
        val json = Json { ignoreUnknownKeys = true }
        val cache = ScheduleCache(context)
        val schedulePrefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val sessionPrefs = context.getSharedPreferences("watch_session", Context.MODE_PRIVATE)
        val oldSchedule = schedulePrefs.getString("cached_schedule_json", null)
        val oldSession = sessionPrefs.getString("latest_session_json", null)
        val logs = PendingLogsStore(context)
        val events = PendingSessionEventsStore(context)
        val oldLogs = logs.loadAll()
        val oldEvents = events.loadAll()
        val data = Wearable.getDataClient(context)
        val buffer = data.dataItems.await()
        val oldWorkout = try { buffer.firstOrNull { it.uri.host == local && it.uri.path == DataLayerPaths.WORKOUT_SET }
            ?.let { DataMapItem.fromDataItem(it).dataMap.toByteArray() } } finally { buffer.release() }
        val marker = "legacy-validation-${UUID.randomUUID()}"
        var fixture: LegacyFixture? = null
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messages = Wearable.getMessageClient(context)

        suspend fun restore() {
            logs.ack(logs.loadAll().filter { it.entry.exercise == marker }.map { it.id })
            events.ack(events.loadAll().filter { it.event.workoutEntryId == marker }.map { it.id })
            check(schedulePrefs.edit().apply {
                if (oldSchedule == null) remove("cached_schedule_json") else putString("cached_schedule_json", oldSchedule)
            }.commit())
            if (oldWorkout == null) data.deleteDataItems(Uri.parse("wear://$local${DataLayerPaths.WORKOUT_SET}")).await()
            else data.putDataItem(PutDataMapRequest.create(DataLayerPaths.WORKOUT_SET).apply {
                dataMap.putAll(DataMap.fromByteArray(oldWorkout))
            }.asPutDataRequest().setUrgent()).await()
            check(sessionPrefs.edit().apply {
                if (oldSession == null) remove("latest_session_json") else putString("latest_session_json", oldSession)
            }.commit())
            assertEquals(oldSchedule, schedulePrefs.getString("cached_schedule_json", null))
            assertEquals(oldSession, sessionPrefs.getString("latest_session_json", null))
            assertEquals(oldLogs, logs.loadAll())
            assertEquals(oldEvents, events.loadAll())
        }

        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId != peer || event.path != PROBE) return@OnMessageReceivedListener
            scope.launch {
                val command = event.data.toString(Charsets.UTF_8)
                val reply = try {
                    when (command) {
                        "prepare" -> {
                            val weekday = SimpleDateFormat("EEEE", Locale.US).format(Date())
                            cache.save(listOf(
                                ScheduleRow(weekday, "12:00", marker, 2, "8-10", 30, 2.5, "kg",
                                    questId = marker, questDayIndex = 1, questDayLabel = "Legacy validation", questLevel = "beginner"),
                                ScheduleRow(weekday, "12:01", "$marker-second", 1, "20 sec", 0),
                            ))
                            assertEquals(2, cache.todaysWorkout().exercises.size)
                            LegacyFixture(marker, cache.todaysWorkout(), Instant.now().toString()).also { fixture = it }
                                .let { json.encodeToString(it) }
                        }
                        "push" -> {
                            val expected = requireNotNull(fixture).payload
                            assertEquals(expected, cache.todaysWorkout())
                            WearSyncClient.sendWorkoutSet(context, expected).getOrThrow()
                            "pushed"
                        }
                        "history" -> {
                            val expected = requireNotNull(fixture)
                            val log = LogEntry(exercise = marker, status = LogStatus.DONE, timestamp = expected.timestamp)
                            val sessionEvent = WorkoutSessionEvent(workoutEntryId = marker, workoutDate = expected.payload.date,
                                eventType = SessionEventType.COMPLETED, stopReason = SessionStopReason.COMPLETED,
                                timestamp = expected.timestamp, elapsedSeconds = 10, exerciseIndex = 1,
                                currentSet = 1, totalExercises = 2, currentExercise = "$marker-second")
                            withTimeout(15_000) {
                                while (logs.loadAll().none { it.entry == log } || events.loadAll().none { it.event == sessionEvent }) delay(100)
                            }
                            delay(500)
                            assertEquals(1, logs.loadAll().count { it.entry.exercise == marker })
                            assertEquals(1, events.loadAll().count { it.event.workoutEntryId == marker })
                            assertEquals(oldLogs, logs.loadAll().filterNot { it.entry.exercise == marker })
                            assertEquals(oldEvents, events.loadAll().filterNot { it.event.workoutEntryId == marker })
                            "history:passed"
                        }
                        "snapshot" -> {
                            val expected = requireNotNull(fixture)
                            withTimeout(15_000) {
                                while (WatchSessionStore(context).load()?.workoutEntryId != marker) delay(100)
                            }
                            val snapshot = requireNotNull(WatchSessionStore(context).load())
                            assertEquals(expected.timestamp, snapshot.timestamp)
                            assertEquals(expected.payload.date, snapshot.workoutDate)
                            assertEquals(SessionStatus.ACTIVE, snapshot.status)
                            assertEquals(2, snapshot.totalExercises)
                            assertEquals(10, snapshot.elapsedSeconds)
                            assertEquals(marker, snapshot.currentExercise)
                            "snapshot:passed"
                        }
                        "finish" -> { restore(); "finished" }
                        else -> error("Unknown legacy command")
                    }
                } catch (error: Throwable) { "error:$command:${error.javaClass.simpleName}:${error.message}" }
                messages.sendMessage(peer, REPLY, reply.toByteArray()).await()
                if (command == "finish") done.countDown()
            }
        }
        messages.addListener(listener).await()
        try {
            assertTrue("Watch legacy driver did not finish", done.await(180, TimeUnit.SECONDS))
        } finally {
            messages.removeListener(listener).await()
            scope.cancel()
            restore()
        }
    }

    companion object {
        const val PROBE = "/validation/legacy/probe"
        const val REPLY = "/validation/legacy/reply"
    }
}
