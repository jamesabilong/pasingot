package app.personal.workouttracker.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.DownloadInsertPlan
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.WorkoutSetPayload
import app.personal.workouttracker.shared.planWorkoutDownloadInsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.personal.workouttracker.wear.download.LogFlushWorker

private val Context.workoutDataStore by preferencesDataStore(name = "workout_downloads")

/** Result of attempting to add a newly-downloaded workout set (Prompt 5 req 3). */
sealed interface AddResult {
    data class Added(val entry: DownloadedWorkoutEntry) : AddResult
    /** A cached entry for this exact date already exists — never overwritten,
     *  even if that entry is already completed (resolved duplicate-date policy). */
    data class SkippedDuplicateDate(val existing: DownloadedWorkoutEntry) : AddResult
    /** Cap reached and every cached entry is active/paused — nothing is safe
     *  to silently evict. Caller must prompt the user to delete/finish one. */
    object Blocked : AddResult
    /** Payload schema is newer/older than this app knows how to read. */
    object StalePayload : AddResult
}

data class DownloadFeedback(val message: String, val error: Boolean, val revision: Long = System.nanoTime())

/**
 * Single source of truth for downloaded workout sets on the watch (Prompt 5)
 * and their per-entry [SessionState] (Prompt 4). Backed by Preferences
 * DataStore rather than Proto DataStore — see the build plan's note on
 * avoiding a `protoc` codegen dependency for a project that can't be
 * compile-verified in this sandbox.
 */
class WorkoutRepository(private val context: Context) : WorkoutSessionStore {

    // UI and listener service share one app process. Feedback is transient, not workout data.
    companion object {
        private val latestFeedback = MutableStateFlow<DownloadFeedback?>(null)
        private val effectsMutex = Mutex()
    }

    val downloadFeedback = latestFeedback.asStateFlow()

    fun reportDownload(message: String, error: Boolean = false) {
        latestFeedback.value = DownloadFeedback(message, error)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("workout_store_json")

    val entries: Flow<List<DownloadedWorkoutEntry>> = context.workoutDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        val state = decodeState(raw)
        state.entries
    }

    override suspend fun getEntry(entryId: String): DownloadedWorkoutEntry? =
        entries.first().find { it.id == entryId }

    override suspend fun commitSession(
        expected: DownloadedWorkoutEntry,
        updated: DownloadedWorkoutEntry,
        effects: WorkoutSessionEffects?,
        action: SessionOutcomeAction?,
    ): Boolean {
        var committed = false
        context.workoutDataStore.edit { prefs ->
            val raw = prefs[key] ?: return@edit
            val current = decodeState(raw)
            val next = commitWorkoutSession(current, expected, updated, effects) ?: return@edit
            prefs[key] = json.encodeToString(next)
            committed = true
        }
        if (committed && effects != null) runCatching { LogFlushWorker.scheduleRetry(context) }
        return committed
    }

    override suspend fun flushPendingEffects(sender: LogSender) = effectsMutex.withLock {
        while (true) {
            val prefs = context.workoutDataStore.data.first()
            val raw = prefs[key] ?: return@withLock
            val state = decodeState(raw)
            val effect = state.pendingSessionEffects.firstOrNull() ?: return@withLock
            effect.log?.let { sender.sendEntry(it) }
            effect.event?.let { sender.sendSessionEvent(it) }
            context.workoutDataStore.edit { currentPrefs ->
                val currentRaw = currentPrefs[key] ?: return@edit
                val current = decodeState(currentRaw)
                currentPrefs[key] = json.encodeToString(current.copy(
                    pendingSessionEffects = current.pendingSessionEffects.filterNot { it.id == effect.id },
                ))
            }
        }
    }

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
            when (val plan = planWorkoutDownloadInsert(current.entries, payload)) {
                is DownloadInsertPlan.Added -> {
                    prefs[key] = json.encodeToString(current.copy(entries = plan.entries))
                    result = AddResult.Added(plan.entry)
                }
                is DownloadInsertPlan.SkippedDuplicateDate -> {
                    // Resolved decision: skip always, even if completed — one
                    // entry per date, ever, full stop.
                    result = AddResult.SkippedDuplicateDate(plan.existing)
                }
                DownloadInsertPlan.Blocked -> {
                    // Every cached entry is active/paused — never silently
                    // discard in-progress data.
                    result = AddResult.Blocked
                }
                DownloadInsertPlan.StalePayload -> {
                    result = AddResult.StalePayload
                }
            }
        }
        return result
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

    /** Preserve unreadable/future bytes and fail closed instead of releasing session ownership. */
    private fun decodeState(raw: String): WorkoutStoreState {
        val state = json.decodeFromString<WorkoutStoreState>(raw)
        check(state.schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported workout store" }
        check(state.entries.map { it.id }.distinct().size == state.entries.size) { "Duplicate workout identity" }
        return state
    }
}
