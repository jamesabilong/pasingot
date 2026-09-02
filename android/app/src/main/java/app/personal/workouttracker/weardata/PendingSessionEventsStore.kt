package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.WorkoutSessionEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** One received session event plus a locally-generated id for JS-side acking. */
@Serializable
data class PendingSessionEventRecord(val id: String, val event: WorkoutSessionEvent)

/** Phone-native staging store for watch workout-level session events. */
class PendingSessionEventsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun addPending(event: WorkoutSessionEvent) {
        val current = loadAll().toMutableList()
        current.add(PendingSessionEventRecord(id = UUID.randomUUID().toString(), event = event))
        saveAll(current)
    }

    @Synchronized
    fun loadAll(): List<PendingSessionEventRecord> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PendingSessionEventRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun ack(ids: List<String>) {
        val idSet = ids.toSet()
        saveAll(loadAll().filterNot { it.id in idSet })
    }

    private fun saveAll(records: List<PendingSessionEventRecord>) {
        prefs.edit().putString(KEY_EVENTS, json.encodeToString(records)).apply()
    }

    companion object {
        private const val PREFS_NAME = "pending_session_events"
        private const val KEY_EVENTS = "pending_session_events_json"
    }
}
