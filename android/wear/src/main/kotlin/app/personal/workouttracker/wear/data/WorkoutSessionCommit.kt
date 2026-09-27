package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import kotlinx.serialization.Serializable

@Serializable
internal data class WorkoutStoreState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<DownloadedWorkoutEntry> = emptyList(),
    val pendingSessionEffects: List<WorkoutSessionEffects> = emptyList(),
)

/** Compare-and-set keeps a stale screen from restoring reset/deleted or newer progress. */
internal fun commitWorkoutSession(
    state: WorkoutStoreState,
    expected: DownloadedWorkoutEntry,
    updated: DownloadedWorkoutEntry,
    effects: WorkoutSessionEffects?,
): WorkoutStoreState? {
    require(updated.id == expected.id && updated.sessionState?.workoutEntryId == expected.id)
    val index = state.entries.indexOfFirst { it.id == expected.id }
    if (index < 0 || state.entries[index] != expected) return null
    require(effects == null || state.pendingSessionEffects.none { it.id == effects.id })
    return state.copy(
        entries = state.entries.toMutableList().apply { this[index] = updated },
        pendingSessionEffects = state.pendingSessionEffects + listOfNotNull(effects),
    )
}
