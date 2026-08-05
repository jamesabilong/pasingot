package app.personal.workouttracker.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.MAX_STORED_WORKOUTS
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutSetPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.workoutDataStore by preferencesDataStore(name = "workout_downloads")

/** Local persistence shape — not part of the cross-device contract in
 *  :shared, but stamped with the same schema version for the same reason:
 *  a future shape change must be detectable rather than silently misparsed. */
@Serializable
private data class WorkoutStoreState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<DownloadedWorkoutEntry> = emptyList(),
)

/** Derived (never stored) UI status for a [DownloadedWorkoutEntry] — Prompt 5 req 4. */
enum class EntryDisplayStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    STALE("Needs re-download"),
}

fun DownloadedWorkoutEntry.displayStatus(): EntryDisplayStatus = when {
    schemaVersion != CURRENT_SCHEMA_VERSION -> EntryDisplayStatus.STALE
    sessionState == null -> EntryDisplayStatus.NOT_STARTED
    sessionState.status == SessionStatus.ACTIVE -> EntryDisplayStatus.IN_PROGRESS
    sessionState.status == SessionStatus.PAUSED -> EntryDisplayStatus.PAUSED
    sessionState.status == SessionStatus.COMPLETED -> EntryDisplayStatus.COMPLETED
    else -> EntryDisplayStatus.NOT_STARTED
}

/** Result of attempting to add a newly-downloaded workout set (Prompt 5 req 3). */
sealed interface AddResult {
    data class Added(val entry: DownloadedWorkoutEntry) : AddResult
    /** A cached entry for this exact date already exists — never overwritten,
     *  even if that entry is already completed (resolved duplicate-date policy). */
    data class SkippedDuplicateDate(val existing: DownloadedWorkoutEntry) : AddResult
    /** Cap reached and every cached entry is active/paused — nothing is safe
     *  to silently evict. Caller must prompt the user to delete/finish one. */
    object Blocked : AddResult
}

/**
 * Single source of truth for downloaded workout sets on the watch (Prompt 5)
 * and their per-entry [SessionState] (Prompt 4). Backed by Preferences
 * DataStore rather than Proto DataStore — see the build plan's note on
 * avoiding a `protoc` codegen dependency for a project that can't be
 * compile-verified in this sandbox.
 */
class WorkoutRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("workout_store_json")

    val entries: Flow<List<DownloadedWorkoutEntry>> = context.workoutDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        val state = decodeState(raw) ?: return@map emptyList()
        state.entries
    }

    suspend fun getEntry(entryId: String): DownloadedWorkoutEntry? =
        entries.first().find { it.id == entryId }

    /**
     * Adds a freshly-downloaded [WorkoutSetPayload] (from either the manual
     * "Download Now" action or the scheduled WorkManager job — same call
     * site either way). Encodes the duplicate-date and cap/eviction rules
     * from Prompt 5 req 2-3.
     */
    suspend fun addDownload(payload: WorkoutSetPayload): AddResult {
        var result: AddResult = AddResult.Blocked
        context.workoutDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: WorkoutStoreState()

            val existing = current.entries.find { it.id == payload.date }
            if (existing != null) {
                // Resolved decision: skip always, even if completed — one
                // entry per date, ever, full stop.
                result = AddResult.SkippedDuplicateDate(existing)
                return@edit
            }

            var entries = current.entries
            if (entries.size >= MAX_STORED_WORKOUTS) {
                val evictable = entries
                    .filter { it.displayStatus() == EntryDisplayStatus.COMPLETED }
                    .minByOrNull { it.date } // oldest completed first
                if (evictable != null) {
                    entries = entries.filterNot { it.id == evictable.id }
                } else {
                    // Every cached entry is active/paused — never silently
                    // discard in-progress data.
                    result = AddResult.Blocked
                    return@edit
                }
            }

            val newEntry = DownloadedWorkoutEntry(
                id = payload.date,
                date = payload.date,
                label = payload.date, // UI layer may reformat for display
                exercises = payload.exercises,
                schemaVersion = CURRENT_SCHEMA_VERSION,
                sessionState = null,
            )
            entries = entries + newEntry
            prefs[key] = json.encodeToString(current.copy(entries = entries))
            result = AddResult.Added(newEntry)
        }
        return result
    }

    /** Prompt 4: persists progress for a specific entry — resume-in-place or
     *  auto-save-as-paused on exit. */
    suspend fun updateSessionState(entryId: String, newState: SessionState) {
        context.workoutDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: return@edit
            val updated = current.entries.map { if (it.id == entryId) it.copy(sessionState = newState) else it }
            prefs[key] = json.encodeToString(current.copy(entries = updated))
        }
    }

    /** Prompt 5 secondary action: clears SessionState only — the cached
     *  exercise data stays. Reset never writes a log entry (Prompt 8). */
    suspend fun resetEntry(entryId: String) {
        context.workoutDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: return@edit
            val updated = current.entries.map { if (it.id == entryId) it.copy(sessionState = null) else it }
            prefs[key] = json.encodeToString(current.copy(entries = updated))
        }
    }

    /** Prompt 5 secondary action: removes the entry entirely, freeing a slot. */
    suspend fun deleteEntry(entryId: String) {
        context.workoutDataStore.edit { prefs ->
            val current = prefs[key]?.let { decodeState(it) } ?: return@edit
            val updated = current.entries.filterNot { it.id == entryId }
            prefs[key] = json.encodeToString(current.copy(entries = updated))
        }
    }

    /** Null on a JSON parse failure OR a schema-version mismatch — callers
     *  treat both the same as "nothing usable cached" rather than guessing
     *  at a malformed/old shape (Prompt 5 req 5). */
    private fun decodeState(raw: String): WorkoutStoreState? = try {
        val state = json.decodeFromString<WorkoutStoreState>(raw)
        if (state.schemaVersion != CURRENT_SCHEMA_VERSION) null else state
    } catch (e: Exception) {
        null
    }
}
