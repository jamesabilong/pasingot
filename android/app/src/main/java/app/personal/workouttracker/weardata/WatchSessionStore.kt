package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.WatchSessionSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** A latest-state cache, deliberately separate from completed workout history. */
class WatchSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watch_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): WatchSessionSnapshot? = synchronized(lock) {
        val raw = prefs.getString(KEY_SESSION, null) ?: return@synchronized null
        runCatching { json.decodeFromString<WatchSessionSnapshot>(raw) }.getOrNull()
            ?.takeIf { it.schemaVersion == CURRENT_SCHEMA_VERSION }
    }

    fun save(snapshot: WatchSessionSnapshot): Boolean = synchronized(lock) {
        if (snapshot.schemaVersion != CURRENT_SCHEMA_VERSION) return@synchronized false
        val incomingTime = Instant.parse(snapshot.timestamp)
        val existing = load()
        if (existing != null && Instant.parse(existing.timestamp).isAfter(incomingTime)) return@synchronized false
        if (existing == snapshot) return@synchronized false
        check(prefs.edit().putString(KEY_SESSION, json.encodeToString(snapshot)).commit()) {
            "Could not save watch session"
        }
        true
    }

    companion object {
        private val lock = Any()
        private const val KEY_SESSION = "latest_session_json"
    }
}
