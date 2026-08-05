package app.personal.workouttracker.weardata

import android.content.Context
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.CachedSchedule
import app.personal.workouttracker.shared.ScheduleRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phone-native staging store for the PWA's parsed CSV schedule.
 *
 * This exists because [WorkoutRequestListenerService] runs in the background
 * and cannot reach into the WebView's IndexedDB (JS-only) to find "today's"
 * exercises. [ScheduleSyncPlugin] pushes the full schedule here every time
 * the PWA parses a CSV; this class just reads/writes the resulting JSON blob.
 *
 * SharedPreferences (not DataStore) is deliberate here: this is a single
 * small blob with no concurrent-writer story, on the *phone* module which
 * doesn't otherwise depend on DataStore — keeps :app's dependency surface
 * smaller. The watch side (Phase D/E) uses Preferences DataStore instead,
 * where concurrent access from Compose + WorkManager is a real concern.
 */
class ScheduleCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun save(rows: List<ScheduleRow>) {
        val cached = CachedSchedule(rows = rows)
        prefs.edit().putString(KEY_SCHEDULE, json.encodeToString(cached)).apply()
    }

    /** Returns null if nothing has been synced yet, or if the cached shape is
     *  stale ([CachedSchedule.schemaVersion] mismatch) — callers should treat
     *  that the same as "no schedule available" rather than guessing at a
     *  malformed shape. */
    fun load(): CachedSchedule? {
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return null
        return try {
            val cached = json.decodeFromString(CachedSchedule.serializer(), raw)
            if (cached.schemaVersion != CURRENT_SCHEMA_VERSION) null else cached
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "schedule_cache"
        private const val KEY_SCHEDULE = "cached_schedule_json"
    }
}
