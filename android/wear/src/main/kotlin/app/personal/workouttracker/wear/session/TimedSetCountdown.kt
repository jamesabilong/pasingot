package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus

/** Only explicit durations become timers; ranges and bare repetition counts stay manual. */
fun timedSetDurationMillis(prescription: String): Long? {
    val text = prescription.trim().lowercase()
    val token = Regex("""(\d+(?:\.\d+)?)\s*(minutes?|mins?|m|seconds?|secs?|s)""")
    val matches = token.findAll(text).toList()
    if (matches.isEmpty() || text.replace(token, "").isNotBlank()) return null
    val seconds = matches.sumOf {
        val value = it.groupValues[1].toDoubleOrNull() ?: return null
        value * if (it.groupValues[2].startsWith("m")) 60 else 1
    }
    return (seconds * 1_000).toLong().takeIf { seconds.isFinite() && it in 1..86_400_000 }
}

fun timedSetRemainingMillis(session: SessionState, now: Long): Long? = when (session.status) {
    SessionStatus.ACTIVE -> session.timedSetDeadlineEpochMillis?.let { (it - now).coerceAtLeast(0) }
    SessionStatus.PAUSED -> session.pausedTimedSetRemainingMillis
    else -> null
}

/** These fields commit with the existing set transition, never in a separate timer write. */
internal fun updateTimedSetCountdown(entry: DownloadedWorkoutEntry, previous: SessionState?,
    next: SessionState, now: Long): SessionState {
    val duration = entry.exercises.getOrNull(next.exerciseIndex)?.reps?.let(::timedSetDurationMillis)
    fun clear() = next.copy(timedSetDeadlineEpochMillis = null, pausedTimedSetRemainingMillis = null)
    if (duration == null) return clear()
    val sameSet = previous?.exerciseIndex == next.exerciseIndex && previous.currentSet == next.currentSet
    return when (next.status) {
        SessionStatus.ACTIVE -> {
            val deadline = when {
                sameSet && previous?.status == SessionStatus.ACTIVE -> previous.timedSetDeadlineEpochMillis ?: now + duration
                sameSet && previous?.status == SessionStatus.PAUSED && previous.pausedRestRemainingSeconds == null ->
                    now + (previous.pausedTimedSetRemainingMillis ?: duration)
                else -> now + duration
            }
            next.copy(timedSetDeadlineEpochMillis = deadline, pausedTimedSetRemainingMillis = null)
        }
        SessionStatus.PAUSED -> next.copy(timedSetDeadlineEpochMillis = null,
            pausedTimedSetRemainingMillis = if (next.pausedRestRemainingSeconds != null) null else
                if (sameSet) timedSetRemainingMillis(previous, now) ?: duration else duration)
        else -> clear()
    }
}
