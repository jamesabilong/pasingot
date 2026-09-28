package app.personal.workouttracker.wear.cues

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class WatchCueCancellation { PAUSE, START_NOW, RESTART, END, SKIP, NAVIGATION, AUDIO_ROUTE }

enum class WatchCueResult {
    SPOKEN,
    HAPTIC_ONLY,
    DUPLICATE,
    LOWER_PRIORITY,
}

interface WatchCueOutput {
    val talkBackEnabled: Boolean
    suspend fun speak(utteranceId: String, text: String): Boolean
    fun haptic(kind: WatchCueKind)
    fun cancel(reason: WatchCueCancellation)
    fun close()
}

/**
 * Serializes cue decisions. Persistence happens before any haptic or speech,
 * so recreation cannot replay a success cue. Speech is optional; visual state
 * remains owned by the session UI and haptics still run when TTS is unavailable.
 */
class WatchCueController(
    private val store: WatchCueStore,
    private val output: WatchCueOutput,
) {
    private val mutex = Mutex()
    private var active: WatchCueEvent? = null

    suspend fun emit(event: WatchCueEvent, script: String?): WatchCueResult {
        val admission = mutex.withLock {
            val selected = selectWatchCue(active, event)
            if (selected !== event) return@withLock WatchCueResult.LOWER_PRIORITY

            val reserved = store.reserve(event)
            if (reserved is ReserveWatchCue.Duplicate) return@withLock WatchCueResult.DUPLICATE
            if (active != null) output.cancel(WatchCueCancellation.AUDIO_ROUTE)
            active = event
            (reserved as ReserveWatchCue.Reserved).preferences
        }
        if (admission is WatchCueResult) return admission
        val preferences = admission as WatchCuePreferences

        output.haptic(event.kind)
        if (!preferences.allows(event) || script.isNullOrBlank() || output.talkBackEnabled) {
            mutex.withLock { if (active == event) active = null }
            return WatchCueResult.HAPTIC_ONLY
        }
        val spoken = runCatching {
            withTimeoutOrNull(MAX_SPEECH_MILLIS) { output.speak(event.key, script) } ?: false
        }.getOrDefault(false)
        mutex.withLock { if (active == event) active = null }
        return if (spoken) WatchCueResult.SPOKEN else WatchCueResult.HAPTIC_ONLY
    }

    suspend fun cancel(reason: WatchCueCancellation) = mutex.withLock {
        active = null
        output.cancel(reason)
    }

    suspend fun close() = mutex.withLock {
        active = null
        output.close()
    }

    private companion object {
        const val MAX_SPEECH_MILLIS = 5_000L
    }
}
