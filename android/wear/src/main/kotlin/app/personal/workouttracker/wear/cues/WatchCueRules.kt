package app.personal.workouttracker.wear.cues

import kotlinx.serialization.Serializable

/** Voice is opt-in. A missing preference never enables the microphone or speech. */
@Serializable
data class WatchCuePreferences(
    val voiceEnabled: Boolean = false,
    val voicePromptResolved: Boolean = false,
    val startBriefing: Boolean = true,
    val restAnnouncements: Boolean = true,
    val countdown: Boolean = true,
    val completion: Boolean = true,
)

enum class WatchCueCategory { START, REST, COUNTDOWN, COMPLETION }
enum class WatchCueKind { BRIEFING, REST, FIVE_SECONDS, GO, EXERCISE_SUCCESS, WORKOUT_SUCCESS }

data class WatchCueEvent(
    val sessionId: String,
    val revision: Long,
    val exerciseIndex: Int,
    val setIndex: Int,
    val kind: WatchCueKind,
    val thresholdMillis: Long? = null,
) {
    init {
        require(sessionId.isNotBlank() && revision >= 0 && exerciseIndex >= 0 && setIndex >= 0)
    }

    val key: String get() = listOf(sessionId, revision, exerciseIndex, setIndex, kind.name,
        thresholdMillis?.toString().orEmpty()).joinToString("|")
    val category: WatchCueCategory get() = when (kind) {
        WatchCueKind.BRIEFING -> WatchCueCategory.START
        WatchCueKind.REST -> WatchCueCategory.REST
        WatchCueKind.FIVE_SECONDS, WatchCueKind.GO -> WatchCueCategory.COUNTDOWN
        WatchCueKind.EXERCISE_SUCCESS, WatchCueKind.WORKOUT_SUCCESS -> WatchCueCategory.COMPLETION
    }
    val priority: Int get() = when (kind) {
        WatchCueKind.WORKOUT_SUCCESS -> 4
        WatchCueKind.GO -> 3
        WatchCueKind.FIVE_SECONDS -> 2
        else -> 1
    }
}

fun WatchCuePreferences.allows(event: WatchCueEvent): Boolean = voiceEnabled && when (event.category) {
    WatchCueCategory.START -> startBriefing
    WatchCueCategory.REST -> restAnnouncements
    WatchCueCategory.COUNTDOWN -> countdown
    WatchCueCategory.COMPLETION -> completion
}

/** Persist with the session transition before issuing a non-repeatable cue. */
@Serializable
data class WatchCueLedger(val deliveredKeys: List<String> = emptyList()) {
    fun record(event: WatchCueEvent): WatchCueLedger? {
        if (event.key in deliveredKeys) return null
        require(deliveredKeys.size < 64) { "Cue ledger is full" }
        return copy(deliveredKeys = deliveredKeys + event.key)
    }
}

/** The highest-priority eligible cue replaces obsolete speech in a single queue. */
fun selectWatchCue(current: WatchCueEvent?, incoming: WatchCueEvent): WatchCueEvent =
    if (current == null || incoming.priority >= current.priority) incoming else current

private fun cleanCueText(raw: String, limit: Int): String = raw
    .replace(Regex("[\\p{Cntrl}\\p{Cf}]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit)
    .trimEnd()

/** Bounded scripts keep the countdown independent of slow or missing TTS. */
object WatchCueScripts {
    const val MAX_BRIEF_LENGTH = 140
    const val FIVE_SECONDS = "Starting in five seconds."
    const val EXERCISE_SUCCESS = "Exercise complete."
    const val WORKOUT_SUCCESS = "Workout complete. Great work."

    fun briefing(name: String, sets: Int, prescription: String, load: String? = null): String {
        require(sets in 1..99)
        val safeName = cleanCueText(name, 60).ifEmpty { "Exercise" }
        val safeTarget = cleanCueText(prescription, 55).ifEmpty { "the planned target" }
        val safeLoad = load?.let { cleanCueText(it, 32) }?.takeIf(String::isNotEmpty)
        val tail = buildString {
            append(if (sets == 1) "One set of " else "$sets sets of ")
            append(safeTarget.trimEnd('.'))
            if (safeLoad != null) append(" at $safeLoad")
            append('.')
        }
        return "$safeName. $tail".take(MAX_BRIEF_LENGTH).trimEnd()
    }

    fun rest(seconds: Int, nextName: String?, nextTarget: String?): String? {
        require(seconds >= 0)
        if (seconds <= 5) return null
        val name = nextName?.let { cleanCueText(it, 42) }?.takeIf(String::isNotEmpty)
        if (seconds <= 10) {
            val short = name?.let { "Next: $it." } ?: return null
            // Leave at least one second before the five-second warning.
            return short.takeIf { estimatedSpeechMillis(it) + 1_000L < (seconds - 5) * 1_000L }
        }
        val duration = if (seconds % 60 == 0) {
            val minutes = seconds / 60
            "$minutes ${if (minutes == 1) "minute" else "minutes"}"
        } else "$seconds seconds"
        val preview = if (name != null) {
            val target = nextTarget?.let { cleanCueText(it, 36) }?.takeIf(String::isNotEmpty)
            " Up next: $name${target?.let { ", $it" }.orEmpty()}."
        } else ""
        return "Rest for $duration.$preview".take(140).trimEnd()
    }

    fun go(nextName: String?, nextSet: Int?): String = when {
        nextName != null -> "Go. ${cleanCueText(nextName, 42).ifEmpty { "Next exercise" }}."
        nextSet != null -> "Go. Set $nextSet."
        else -> "Go."
    }

    private fun estimatedSpeechMillis(script: String): Long =
        ((script.split(' ').size * 60_000L + 179) / 180)
}
