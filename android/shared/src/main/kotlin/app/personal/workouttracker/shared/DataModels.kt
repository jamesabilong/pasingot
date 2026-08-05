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
const val CURRENT_SCHEMA_VERSION: Int = 1

/** Cap on simultaneously-downloaded workout sets kept on the watch (Prompt 5). */
const val MAX_STORED_WORKOUTS: Int = 3

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
}

/** Session status literals for [SessionState.status]. Kept as plain strings
 *  (not an enum) to match the shared JSON contract exactly, but centralized
 *  here so call sites don't scatter raw string literals. */
object SessionStatus {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
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
    val status: String, // SessionStatus.ACTIVE | PAUSED | COMPLETED
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
)

/** Phone-native cache of the full schedule, staged by ScheduleSyncPlugin and
 *  read by WorkoutRequestListenerService when the watch asks for "today". */
@Serializable
data class CachedSchedule(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val rows: List<ScheduleRow> = emptyList(),
)
