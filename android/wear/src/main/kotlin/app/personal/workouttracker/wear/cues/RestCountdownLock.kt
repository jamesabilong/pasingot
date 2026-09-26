package app.personal.workouttracker.wear.cues

import kotlinx.serialization.Serializable

/** Store this alongside the rest deadline. A new rest creates a new interval ID. */
@Serializable
data class RestCountdownLock(
    val intervalId: String,
    val deadlineEpochMillis: Long,
    val finalCountdownStarted: Boolean = false,
    val pausedRemainingMillis: Long? = null,
    val finished: Boolean = false,
)

sealed interface RestCountdownChange {
    data class Changed(val state: RestCountdownLock, val crossedFiveSeconds: Boolean = false) : RestCountdownChange
    data class Refused(val state: RestCountdownLock) : RestCountdownChange
}

/** Exact deadline comparison, serialized with persistence by the future session adapter. */
fun RestCountdownLock.tick(nowEpochMillis: Long): RestCountdownChange {
    if (finished || pausedRemainingMillis != null) return RestCountdownChange.Refused(this)
    val remaining = deadlineEpochMillis - nowEpochMillis
    if (remaining <= 0) return RestCountdownChange.Changed(copy(finished = true, finalCountdownStarted = true))
    if (!finalCountdownStarted && remaining <= 5_000L) {
        return RestCountdownChange.Changed(copy(finalCountdownStarted = true), crossedFiveSeconds = true)
    }
    return RestCountdownChange.Refused(this)
}

fun RestCountdownLock.extend(nowEpochMillis: Long, seconds: Int): RestCountdownChange {
    require(seconds in setOf(5, 10, 30))
    if (finished || pausedRemainingMillis != null || finalCountdownStarted ||
        deadlineEpochMillis - nowEpochMillis <= 5_000L
    ) return RestCountdownChange.Refused(this)
    return RestCountdownChange.Changed(copy(deadlineEpochMillis = deadlineEpochMillis + seconds * 1_000L))
}

fun RestCountdownLock.pause(nowEpochMillis: Long): RestCountdownLock =
    if (finished || pausedRemainingMillis != null) this
    else {
        val remaining = (deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0)
        copy(pausedRemainingMillis = remaining,
            finalCountdownStarted = finalCountdownStarted || remaining <= 5_000L)
    }

fun RestCountdownLock.resume(nowEpochMillis: Long): RestCountdownLock =
    if (pausedRemainingMillis == null || finished) this
    else copy(deadlineEpochMillis = nowEpochMillis + pausedRemainingMillis, pausedRemainingMillis = null)

/** Start now invalidates any queued five-second utterance for this interval. */
fun RestCountdownLock.startNow(): RestCountdownLock =
    copy(finished = true, finalCountdownStarted = true, pausedRemainingMillis = null)
