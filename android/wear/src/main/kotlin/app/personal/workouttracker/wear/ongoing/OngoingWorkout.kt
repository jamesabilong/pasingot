package app.personal.workouttracker.wear.ongoing

import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus

/** Presentation identity only; the repository remains the workout authority. */
data class OngoingWorkout(val kind: String, val id: String, val title: String, val status: String)

fun ongoingWorkout(kind: String, id: String, title: String, session: SessionState?): OngoingWorkout? {
    if (kind !in setOf("session", "quick-start") || id.isBlank() || id.length > 256 ||
        session?.schemaVersion != CURRENT_SCHEMA_VERSION ||
        session.status !in setOf(SessionStatus.ACTIVE, SessionStatus.RESTING, SessionStatus.PAUSED)) return null
    return OngoingWorkout(kind, id, title, session.status)
}

/** Conflicting active identities must not silently redirect the user to a different workout. */
fun singleOngoingWorkout(candidates: List<OngoingWorkout>): OngoingWorkout? = candidates.singleOrNull()
