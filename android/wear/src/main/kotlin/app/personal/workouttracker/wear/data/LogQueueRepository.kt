package app.personal.workouttracker.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.LogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.logQueueDataStore by preferencesDataStore(name = "log_queue")

@Serializable
private data class LogQueueState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<LogEntry> = emptyList(),
)

/**
 * Prompt 8 req 5: small DataStore-backed offline queue. If the phone is
 * unreachable when Complete Set/Skip is tapped, the entry lands here instead
 * of being lost, and gets retried on next connection (app start + a
 * periodic WorkManager flush — see LogSyncManager).
 */
class LogQueueRepository(private val context: Context) {

    private val key = stringPreferencesKey("log_queue_json")
    private val json = Json { ignoreUnknownKeys = true }

    val queuedEntries: Flow<List<LogEntry>> = context.logQueueDataStore.data.map { prefs ->
        prefs[key]?.let { decodeState(it) }?.entries ?: emptyList()
    }

    suspend fun enqueue(entry: LogEntry) {
        context.logQueueDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: LogQueueState()
            prefs[key] = json.encodeToString(current.copy(entries = current.entries + entry))
        }
    }

    suspend fun removeAll(sent: List<LogEntry>) {
        context.logQueueDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: return@edit
            val sentSet = sent.toSet()
            prefs[key] = json.encodeToString(current.copy(entries = current.entries.filterNot { it in sentSet }))
        }
    }

    private fun decodeState(raw: String): LogQueueState? = try {
        val state = json.decodeFromString<LogQueueState>(raw)
        if (state.schemaVersion != CURRENT_SCHEMA_VERSION) null else state
    } catch (e: Exception) {
        null
    }
}
