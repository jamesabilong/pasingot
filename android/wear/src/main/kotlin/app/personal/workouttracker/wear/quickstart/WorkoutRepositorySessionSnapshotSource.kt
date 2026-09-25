package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.wear.data.WorkoutRepository
import kotlinx.coroutines.flow.first

class WorkoutRepositorySessionSnapshotSource(
    private val repository: WorkoutRepository,
) : LegacySessionSnapshotSource {
    override suspend fun entries(): List<DownloadedWorkoutEntry> = repository.entries.first()
}
