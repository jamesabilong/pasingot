package app.personal.workouttracker.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.WorkoutSessionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionEventQueueDataStore by preferencesDataStore(name = "session_event_queue")

@Serializable
private data class SessionEventQueueState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<WorkoutSessionEvent> = emptyList(),
)

/** Offline queue for workout-level watch session events. */
class SessionEventQueueRepository(private val context: Context) {

    private val key = stringPreferencesKey("session_event_queue_json")
    private val json = Json { ignoreUnknownKeys = true }

    val queuedEntries: Flow<List<WorkoutSessionEvent>> = context.sessionEventQueueDataStore.data.map { prefs ->
        prefs[key]?.let { decodeState(it) }?.entries ?: emptyList()
    }

    suspend fun enqueue(entry: WorkoutSessionEvent) {
        context.sessionEventQueueDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: SessionEventQueueState()
            prefs[key] = json.encodeToString(current.copy(entries = current.entries + entry))
        }
    }

    suspend fun removeSentPrefix(count: Int) {
        if (count <= 0) return
        context.sessionEventQueueDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: return@edit
            prefs[key] = json.encodeToString(current.copy(entries = current.entries.drop(count)))
        }
    }

    private fun decodeState(raw: String): SessionEventQueueState? = try {
        val state = json.decodeFromString<SessionEventQueueState>(raw)
        if (state.schemaVersion != CURRENT_SCHEMA_VERSION) null else state
    } catch (e: Exception) {
        null
    }
}
