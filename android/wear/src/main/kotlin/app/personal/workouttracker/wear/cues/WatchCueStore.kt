package app.personal.workouttracker.wear.cues

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.watchCueDataStore by preferencesDataStore(name = "watch_cues")

@Serializable
data class PersistedWatchCueState(
    val schemaVersion: Int = 1,
    val preferences: WatchCuePreferences = WatchCuePreferences(),
    val ledgerSessionId: String? = null,
    val ledger: WatchCueLedger = WatchCueLedger(),
    // One compact tombstone replaces the acknowledged session's transient keys.
    val acknowledgedSessionId: String? = null,
    val acknowledgedWorkoutSuccess: Boolean = false,
)

interface WatchCuePersistence {
    suspend fun read(): String?
    suspend fun write(value: String)
}

class DataStoreWatchCuePersistence internal constructor(
    private val dataStore: DataStore<Preferences>,
) : WatchCuePersistence {
    constructor(context: Context) : this(context.applicationContext.watchCueDataStore)

    private val stateKey = stringPreferencesKey("watch_cue_state_json")

    override suspend fun read(): String? = dataStore.data.first()[stateKey]

    override suspend fun write(value: String) {
        dataStore.edit { it[stateKey] = value }
    }
}

/**
 * Owns voice preferences and the exactly-once cue ledger in one atomic record.
 * Unreadable or future state fails closed: voice stays off and no record is
 * overwritten until the caller explicitly changes preferences.
 */
class WatchCueStore(
    private val persistence: WatchCuePersistence,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private companion object {
        val processMutex = kotlinx.coroutines.sync.Mutex()
    }

    suspend fun state(): PersistedWatchCueState = processMutex.withLock { readState() }

    suspend fun setPreferences(preferences: WatchCuePreferences): PersistedWatchCueState =
        processMutex.withLock {
            val current = readState()
            val updated = current.copy(preferences = preferences)
            persistence.write(json.encodeToString(updated))
            updated
        }

    /** Records before output. Haptics are not gated by the voice preference. */
    suspend fun reserve(event: WatchCueEvent): ReserveWatchCue = processMutex.withLock {
        val current = readState()
        if (current.acknowledgedSessionId == event.sessionId) {
            // Receipt can beat the foreground success callback. Admit that success
            // once without recreating the full ledger; all obsolete cues stay silent.
            if (event.kind != WatchCueKind.WORKOUT_SUCCESS || current.acknowledgedWorkoutSuccess) {
                return@withLock ReserveWatchCue.Duplicate
            }
            persistence.write(json.encodeToString(current.copy(acknowledgedWorkoutSuccess = true)))
            return@withLock ReserveWatchCue.Reserved(current.preferences)
        }
        val ledger = if (current.ledgerSessionId == event.sessionId) current.ledger else WatchCueLedger()
        val recorded = ledger.record(event) ?: return@withLock ReserveWatchCue.Duplicate
        persistence.write(json.encodeToString(current.copy(
            ledgerSessionId = event.sessionId,
            ledger = recorded,
        )))
        ReserveWatchCue.Reserved(current.preferences)
    }

    /** Called only after the session's exact final-result receipt is durable. */
    suspend fun clearAcknowledgedSession(sessionId: String) = processMutex.withLock {
        val current = readState()
        if (current.ledgerSessionId != null && current.ledgerSessionId != sessionId) return@withLock
        if (current.ledgerSessionId == null && current.acknowledgedSessionId == sessionId) return@withLock
        persistence.write(json.encodeToString(current.copy(
            ledgerSessionId = null,
            ledger = WatchCueLedger(),
            acknowledgedSessionId = sessionId,
            acknowledgedWorkoutSuccess = current.ledger.deliveredKeys.any { "|WORKOUT_SUCCESS|" in it },
        )))
    }

    private suspend fun readState(): PersistedWatchCueState {
        val raw = persistence.read() ?: return PersistedWatchCueState()
        return runCatching { json.decodeFromString<PersistedWatchCueState>(raw) }
            .getOrElse { PersistedWatchCueState() }
            .takeIf { it.schemaVersion == 1 }
            ?: PersistedWatchCueState()
    }
}

sealed interface ReserveWatchCue {
    data class Reserved(val preferences: WatchCuePreferences) : ReserveWatchCue
    data object Duplicate : ReserveWatchCue
}
