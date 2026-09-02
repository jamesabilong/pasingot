package app.personal.workouttracker.shared

import kotlinx.serialization.Serializable

// =============================================================================
// Shared data contract — see the v5 build doc's "Shared Data Contract" section.
// Both :app (phone) and :wear (watch) depend on this module so these shapes
// can never drift between the two sides of the Wearable Data Layer.
//
// `reps` stays a String everywhere (may be a range like "8-12") — never coerce
// it to Int downstream, on any platform.
// =============================================================================

/** Current schema version for anything that gets *stored* (downloaded workout
 *  sets, log entries). Bump this and add a migration path if the shape below
 *  ever changes; readers must treat a mismatch as stale data, not attempt to
 *  parse it — see [SessionState.schemaVersion] / [DownloadedWorkoutEntry.schemaVersion]. */
const val CURRENT_SCHEMA_VERSION: Int = 5

/** Cap on simultaneously-downloaded workout sets kept on the watch (Prompt 5). */
const val MAX_STORED_WORKOUTS: Int = 3

private const val SECONDS_PER_REP: Int = 4
private const val SET_SETUP_SECONDS: Int = 10
private const val BETWEEN_EXERCISE_TRANSITION_SECONDS: Int = 15
private const val DEFAULT_REP_COUNT: Int = 10

/** Wearable Data Layer message/data-item paths. Both sides must use these
 *  constants rather than inlined string literals. */
object DataLayerPaths {
    /** Watch -> phone: request today's workout (manual download or the
     *  scheduled WorkManager job — same path, same payload, no date field:
     *  the request always means "today"). */
    const val REQUEST_WORKOUT = "/request-workout"

    /** Phone -> watch: response to [REQUEST_WORKOUT], carries a [WorkoutSetPayload]. */
    const val WORKOUT_SET = "/workout-set"

    /** Watch -> phone: a single completed/skipped log entry, see [LogEntry]. */
    const val LOG = "/log"

    /** Watch -> phone: workout-level completion/end event, see [WorkoutSessionEvent]. */
    const val SESSION_EVENT = "/session-event"
}

/** Session status literals for [SessionState.status]. Kept as plain strings
 *  (not an enum) to match the shared JSON contract exactly, but centralized
 *  here so call sites don't scatter raw string literals. */
object SessionStatus {
    const val ACTIVE = "active"
    const val RESTING = "resting"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
    const val ENDED = "ended"
}

/** Stored reason for a terminal or paused [SessionState]. */
object SessionStopReason {
    const val COMPLETED = "completed"
    const val PAUSED_BY_USER = "paused_by_user"
    const val APP_CLOSED = "app_closed"
    const val ENDED_BY_USER = "ended_by_user"
    const val UNEXPECTED_INTERRUPTION = "unexpected_interruption"
}

/** Workout-level event types sent from watch to phone/PWA. */
object SessionEventType {
    const val COMPLETED = "completed"
    const val ENDED = "ended"
}

/** Log status literals for [LogEntry.status]. */
object LogStatus {
    const val DONE = "done"
    const val SKIPPED = "skipped"
}

/**
 * A single exercise as sent over the Data Layer (Prompt 6).
 * `reps` is a String — may be a range like "8-12" — never parse it as Int.
 */
@Serializable
data class WorkoutExercise(
    val exercise: String,
    val reps: String,
    val sets: Int,
    val rest: Int, // seconds
    val workoutRowId: Long? = null,
    val questId: String? = null,
    val questDayIndex: Int? = null,
    val questDayLabel: String? = null,
    val questLevel: String? = null,
)

/**
 * Phone -> watch response payload on [DataLayerPaths.WORKOUT_SET] (Prompt 6).
 * `date` is the target day in "yyyy-MM-dd" form (always "today" per the
 * resolved request-scope decision — no date field on the request side).
 */
@Serializable
data class WorkoutSetPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val date: String, // "2026-08-03"
    val exercises: List<WorkoutExercise>,
)

/**
 * Per-downloaded-entry session progress on the watch (Prompt 4).
 * Scoped to a specific [workoutEntryId] since several workouts can be cached
 * at once (Prompt 5) — this is never a single global session.
 */
@Serializable
data class SessionState(
    val workoutEntryId: String, // matches DownloadedWorkoutEntry.id
    val exerciseIndex: Int,
    val currentSet: Int,
    val status: String, // SessionStatus.ACTIVE | RESTING | PAUSED | COMPLETED | ENDED
    val restUntilEpochMillis: Long? = null,
    val pausedRestRemainingSeconds: Int? = null,
    val accumulatedElapsedMillis: Long = 0,
    val elapsedStartedAtEpochMillis: Long? = null,
    val lastStopReason: String? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
)

/**
 * One cached workout set on the watch, as tracked by the Manage Downloads
 * screen (Prompt 5). `id` is the target date ("yyyy-MM-dd") — see the
 * resolved duplicate-date policy: one entry per date, ever, skip on repeat.
 */
@Serializable
data class DownloadedWorkoutEntry(
    val id: String, // = date, e.g. "2026-08-03"
    val date: String,
    val label: String, // display label, e.g. "Tue, Aug 3"
    val exercises: List<WorkoutExercise>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionState: SessionState? = null, // null => "Not Started"
)

/** Derived UI status for a [DownloadedWorkoutEntry] — never stored. */
enum class EntryDisplayStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    RESTING("Resting"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    ENDED("Ended"),
    STALE("Needs re-download"),
}

fun DownloadedWorkoutEntry.displayStatus(): EntryDisplayStatus {
    val session = sessionState
    return when {
        schemaVersion != CURRENT_SCHEMA_VERSION -> EntryDisplayStatus.STALE
        session == null -> EntryDisplayStatus.NOT_STARTED
        session.schemaVersion != CURRENT_SCHEMA_VERSION -> EntryDisplayStatus.STALE
        session.status == SessionStatus.ACTIVE -> EntryDisplayStatus.IN_PROGRESS
        session.status == SessionStatus.RESTING -> EntryDisplayStatus.RESTING
        session.status == SessionStatus.PAUSED -> EntryDisplayStatus.PAUSED
        session.status == SessionStatus.COMPLETED -> EntryDisplayStatus.COMPLETED
        session.status == SessionStatus.ENDED -> EntryDisplayStatus.ENDED
        else -> EntryDisplayStatus.NOT_STARTED
    }
}

fun DownloadedWorkoutEntry.estimatedDurationSeconds(): Int =
    estimateWorkoutDurationSeconds(exercises, exercises.firstNotNullOfOrNull { it.questLevel })

fun estimateWorkoutDurationSeconds(
    exercises: List<WorkoutExercise>,
    level: String? = null,
): Int {
    if (exercises.isEmpty()) return 0

    val baseSeconds = exercises.mapIndexed { index, exercise ->
        val sets = exercise.sets.coerceAtLeast(1)
        val restSeconds = exercise.rest.coerceAtLeast(0)
        val restCount = (sets - 1) + if (index < exercises.lastIndex) 1 else 0
        (estimateSetWorkSeconds(exercise.reps) + SET_SETUP_SECONDS) * sets +
            restSeconds * restCount +
            if (index < exercises.lastIndex) BETWEEN_EXERCISE_TRANSITION_SECONDS else 0
    }.sum()

    val multiplier = when (level?.lowercase()) {
        "advanced" -> 1.12
        "intermediate" -> 1.18
        else -> 1.3
    }
    val minimumBufferSeconds = when (level?.lowercase()) {
        "advanced" -> 60
        "intermediate" -> 90
        else -> 150
    }
    val bufferedSeconds = (baseSeconds * multiplier).toInt().coerceAtLeast(baseSeconds + minimumBufferSeconds)
    return roundUpToMinute(bufferedSeconds)
}

fun formatEstimatedDuration(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    return when {
        minutes <= 0 -> "Est. 0 min"
        minutes < 60 -> "Est. $minutes min"
        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes == 0) "Est. ${hours}h" else "Est. ${hours}h ${remainingMinutes}m"
        }
    }
}

private fun estimateSetWorkSeconds(reps: String): Int {
    val normalized = reps.lowercase()
    val values = NUMBER_PATTERN.findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
    val maxValue = values.maxOrNull() ?: DEFAULT_REP_COUNT.toDouble()
    return when {
        "min" in normalized -> (maxValue * 60).toInt()
        "sec" in normalized || "second" in normalized -> maxValue.toInt()
        "side" in normalized || "/leg" in normalized || "each" in normalized -> (maxValue * 2 * SECONDS_PER_REP).toInt()
        else -> (maxValue * SECONDS_PER_REP).toInt()
    }.coerceAtLeast(SECONDS_PER_REP)
}

private fun roundUpToMinute(seconds: Int): Int =
    ((seconds.coerceAtLeast(0) + 59) / 60) * 60

private val NUMBER_PATTERN = Regex("""\d+(?:\.\d+)?""")

sealed interface DownloadInsertPlan {
    data class Added(
        val entries: List<DownloadedWorkoutEntry>,
        val entry: DownloadedWorkoutEntry,
        val evicted: DownloadedWorkoutEntry?,
    ) : DownloadInsertPlan

    data class SkippedDuplicateDate(val existing: DownloadedWorkoutEntry) : DownloadInsertPlan
    data object Blocked : DownloadInsertPlan
    data object StalePayload : DownloadInsertPlan
}

fun planWorkoutDownloadInsert(
    currentEntries: List<DownloadedWorkoutEntry>,
    payload: WorkoutSetPayload,
    maxStoredWorkouts: Int = MAX_STORED_WORKOUTS,
): DownloadInsertPlan {
    if (payload.schemaVersion != CURRENT_SCHEMA_VERSION) return DownloadInsertPlan.StalePayload

    val existing = currentEntries.find { it.id == payload.date }
    if (existing != null) return DownloadInsertPlan.SkippedDuplicateDate(existing)

    var entries = currentEntries
    var evicted: DownloadedWorkoutEntry? = null
    if (entries.size >= maxStoredWorkouts) {
        evicted = entries
            .filter { it.displayStatus() == EntryDisplayStatus.COMPLETED || it.displayStatus() == EntryDisplayStatus.ENDED }
            .minByOrNull { it.date }
        if (evicted == null) return DownloadInsertPlan.Blocked
        entries = entries.filterNot { it.id == evicted.id }
    }

    val entry = DownloadedWorkoutEntry(
        id = payload.date,
        date = payload.date,
        label = payload.date,
        exercises = payload.exercises,
        schemaVersion = CURRENT_SCHEMA_VERSION,
        sessionState = null,
    )

    return DownloadInsertPlan.Added(
        entries = entries + entry,
        entry = entry,
        evicted = evicted,
    )
}

/**
 * Watch -> phone log entry on [DataLayerPaths.LOG] (Prompt 8). Sent on
 * Complete Set / Skip only — Reset (Prompt 5) never produces one of these.
 */
@Serializable
data class LogEntry(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exercise: String,
    val status: String, // LogStatus.DONE | SKIPPED
    val timestamp: String, // ISO-8601, e.g. "2026-08-03T07:32:00Z"
    /** Present when this log completes/skips a whole scheduled row. The
     * phone uses it to update Today status and quest-day progress. */
    val workoutRowId: Long? = null,
)

/**
 * Watch -> phone workout-level event on [DataLayerPaths.SESSION_EVENT].
 * Separate from [LogEntry] so an ended workout does not look like a skipped
 * exercise row in PWA completion stats.
 */
@Serializable
data class WorkoutSessionEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val workoutEntryId: String,
    val workoutDate: String,
    val eventType: String, // SessionEventType.COMPLETED | ENDED
    val stopReason: String,
    val timestamp: String,
    val elapsedSeconds: Int,
    val exerciseIndex: Int,
    val currentSet: Int,
    val totalExercises: Int,
    val currentExercise: String? = null,
)

/**
 * One row of the PWA's imported CSV schedule (Prompt 1's parsed JSON
 * contract, unchanged), pushed from JS to phone-native code via
 * ScheduleSyncPlugin on every CSV import (Prompt 6's reverse bridge — native
 * code can't reach into the WebView's IndexedDB directly). This is the full
 * multi-day schedule, unlike [WorkoutExercise] which is already scoped to a
 * single day's payload.
 */
@Serializable
data class ScheduleRow(
    val day: String,
    val time: String,
    val exercise: String,
    val sets: Int,
    val reps: String,
    val rest: Int,
    val workoutRowId: Long? = null,
    val questId: String? = null,
    val questDayIndex: Int? = null,
    val questDayLabel: String? = null,
    val questLevel: String? = null,
)

/** Phone-native cache of the full schedule, staged by ScheduleSyncPlugin and
 *  read by WorkoutRequestListenerService when the watch asks for "today". */
@Serializable
data class CachedSchedule(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val rows: List<ScheduleRow> = emptyList(),
)
