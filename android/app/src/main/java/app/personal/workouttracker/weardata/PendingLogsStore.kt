package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.LogEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** One received log entry plus a locally-generated id, so the JS side can
 *  ack specific entries after committing them to IndexedDB. */
@Serializable
data class PendingLogRecord(val id: String, val entry: LogEntry)

/**
 * Phone-native staging store for logs received from the watch on "/log"
 * (Prompt 8). [LogListenerService] writes here; [WorkoutLogBridgePlugin]
 * exposes it to the PWA, which drains it on resume, writes into its own
 * IndexedDB `logs` store, and only then acks — see the bridge design note
 * on why a background Kotlin service can't write into IndexedDB directly.
 *
 * `@Synchronized` because a WearableListenerService callback and a
 * Capacitor plugin call (triggered from the WebView) can race on this.
 */
class PendingLogsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun addPending(entry: LogEntry) {
        val current = loadAll().toMutableList()
        current.add(PendingLogRecord(id = UUID.randomUUID().toString(), entry = entry))
        saveAll(current)
    }

    @Synchronized
    fun loadAll(): List<PendingLogRecord> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PendingLogRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Only removes entries the JS side has confirmed it committed to
     *  IndexedDB — ack-after-commit, so a failed web-side write can't lose
     *  an entry. */
    @Synchronized
    fun ack(ids: List<String>) {
        val idSet = ids.toSet()
        saveAll(loadAll().filterNot { it.id in idSet })
    }

    private fun saveAll(records: List<PendingLogRecord>) {
        prefs.edit().putString(KEY_LOGS, json.encodeToString(records)).apply()
    }

    companion object {
        private const val PREFS_NAME = "pending_logs"
        private const val KEY_LOGS = "pending_logs_json"
    }
}
