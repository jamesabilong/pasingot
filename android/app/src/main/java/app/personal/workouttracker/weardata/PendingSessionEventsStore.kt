package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.WorkoutSessionEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** One received session event plus a stable id for JS-side deduplication and acking. */
@Serializable
data class PendingSessionEventRecord(val id: String, val event: WorkoutSessionEvent)

/** Phone-native staging store for watch workout-level session events. */
class PendingSessionEventsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun addPending(event: WorkoutSessionEvent) = synchronized(lock) {
        val current = loadAll().toMutableList()
        val id = UUID.nameUUIDFromBytes(json.encodeToString(event).toByteArray(Charsets.UTF_8)).toString()
        if (current.any { it.id == id }) return@synchronized
        current.add(PendingSessionEventRecord(id = id, event = event))
        saveAll(current)
    }

    fun loadAll(): List<PendingSessionEventRecord> = synchronized(lock) {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return@synchronized emptyList()
        try {
            json.decodeFromString<List<PendingSessionEventRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun ack(ids: List<String>) = synchronized(lock) {
        val idSet = ids.toSet()
        saveAll(loadAll().filterNot { it.id in idSet })
    }

    private fun saveAll(records: List<PendingSessionEventRecord>) {
        check(prefs.edit().putString(KEY_EVENTS, json.encodeToString(records)).commit()) {
            "Could not save pending watch session events"
        }
    }

    companion object {
        private val lock = Any()
        private const val PREFS_NAME = "pending_session_events"
        private const val KEY_EVENTS = "pending_session_events_json"
    }
}
