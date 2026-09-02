package app.personal.workouttracker.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelsTest {

    @Test
    fun displayStatusTreatsEntrySchemaMismatchAsStale() {
        val entry = downloadedEntry(date = "2026-08-01", schemaVersion = CURRENT_SCHEMA_VERSION + 1)

        assertEquals(EntryDisplayStatus.STALE, entry.displayStatus())
    }

    @Test
    fun displayStatusTreatsSessionSchemaMismatchAsStale() {
        val entry = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ACTIVE,
                schemaVersion = CURRENT_SCHEMA_VERSION + 1,
            ),
        )

        assertEquals(EntryDisplayStatus.STALE, entry.displayStatus())
    }

    @Test
    fun displayStatusTreatsRestingSessionAsResting() {
        val entry = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 0,
                currentSet = 2,
                status = SessionStatus.RESTING,
                restUntilEpochMillis = 1_775_000_000_000L,
            ),
        )

        assertEquals(EntryDisplayStatus.RESTING, entry.displayStatus())
    }

    @Test
    fun displayStatusTreatsEndedSessionAsEnded() {
        val entry = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ENDED,
            ),
        )

        assertEquals(EntryDisplayStatus.ENDED, entry.displayStatus())
    }

    @Test
    fun planDownloadInsertSkipsDuplicateDateWithoutReplacingProgress() {
        val existing = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 1,
                currentSet = 2,
                status = SessionStatus.PAUSED,
            ),
        )

        val plan = planWorkoutDownloadInsert(
            currentEntries = listOf(existing),
            payload = workoutPayload(date = existing.date),
        )

        assertTrue(plan is DownloadInsertPlan.SkippedDuplicateDate)
        assertSame(existing, (plan as DownloadInsertPlan.SkippedDuplicateDate).existing)
    }

    @Test
    fun planDownloadInsertEvictsOldestCompletedEntryFirst() {
        val oldestCompleted = downloadedEntry(
            date = "2026-08-01",
            sessionState = completedSession("2026-08-01"),
        )
        val newerCompleted = downloadedEntry(
            date = "2026-08-02",
            sessionState = completedSession("2026-08-02"),
        )
        val paused = downloadedEntry(
            date = "2026-08-03",
            sessionState = SessionState(
                workoutEntryId = "2026-08-03",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.PAUSED,
            ),
        )

        val plan = planWorkoutDownloadInsert(
            currentEntries = listOf(paused, newerCompleted, oldestCompleted),
            payload = workoutPayload(date = "2026-08-04"),
        )

        assertTrue(plan is DownloadInsertPlan.Added)
        val added = plan as DownloadInsertPlan.Added
        assertEquals(oldestCompleted, added.evicted)
        assertEquals(listOf("2026-08-03", "2026-08-02", "2026-08-04"), added.entries.map { it.id })
    }

    @Test
    fun planDownloadInsertCanEvictEndedEntry() {
        val ended = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ENDED,
            ),
        )
        val active = downloadedEntry(
            date = "2026-08-02",
            sessionState = SessionState(
                workoutEntryId = "2026-08-02",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ACTIVE,
            ),
        )
        val paused = downloadedEntry(
            date = "2026-08-03",
            sessionState = SessionState(
                workoutEntryId = "2026-08-03",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.PAUSED,
            ),
        )

        val plan = planWorkoutDownloadInsert(
            currentEntries = listOf(active, paused, ended),
            payload = workoutPayload(date = "2026-08-04"),
        )

        assertTrue(plan is DownloadInsertPlan.Added)
        val added = plan as DownloadInsertPlan.Added
        assertEquals(ended, added.evicted)
        assertEquals(listOf("2026-08-02", "2026-08-03", "2026-08-04"), added.entries.map { it.id })
    }

    @Test
    fun planDownloadInsertBlocksWhenNoCompletedEntryCanBeEvicted() {
        val active = downloadedEntry(
            date = "2026-08-01",
            sessionState = SessionState(
                workoutEntryId = "2026-08-01",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ACTIVE,
            ),
        )
        val paused = downloadedEntry(
            date = "2026-08-02",
            sessionState = SessionState(
                workoutEntryId = "2026-08-02",
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.PAUSED,
            ),
        )
        val notStarted = downloadedEntry(date = "2026-08-03")

        val plan = planWorkoutDownloadInsert(
            currentEntries = listOf(active, paused, notStarted),
            payload = workoutPayload(date = "2026-08-04"),
        )

        assertSame(DownloadInsertPlan.Blocked, plan)
    }

    @Test
    fun planDownloadInsertRejectsStalePayload() {
        val plan = planWorkoutDownloadInsert(
            currentEntries = emptyList(),
            payload = workoutPayload(date = "2026-08-04", schemaVersion = CURRENT_SCHEMA_VERSION + 1),
        )

        assertSame(DownloadInsertPlan.StalePayload, plan)
    }

    @Test
    fun planDownloadInsertAddsFreshPayloadWithoutEvictionWhenBelowCap() {
        val plan = planWorkoutDownloadInsert(
            currentEntries = listOf(downloadedEntry(date = "2026-08-01")),
            payload = workoutPayload(date = "2026-08-02"),
        )

        assertTrue(plan is DownloadInsertPlan.Added)
        val added = plan as DownloadInsertPlan.Added
        assertNull(added.evicted)
        assertEquals("2026-08-02", added.entry.id)
        assertEquals(listOf("2026-08-01", "2026-08-02"), added.entries.map { it.id })
    }

    @Test
    fun estimateWorkoutDurationUsesSetsRepsRestTransitionsAndBeginnerBuffer() {
        val seconds = estimateWorkoutDurationSeconds(
            exercises = listOf(
                WorkoutExercise(exercise = "Push-up", reps = "8-12", sets = 2, rest = 75),
                WorkoutExercise(exercise = "Squat", reps = "10", sets = 3, rest = 60),
            ),
            level = "beginner",
        )

        assertEquals(12 * 60, seconds)
    }

    @Test
    fun estimateWorkoutDurationUsesTimedRepsWithoutMultiplyingByRepPace() {
        val seconds = estimateWorkoutDurationSeconds(
            exercises = listOf(WorkoutExercise(exercise = "Walk", reps = "20 min", sets = 1, rest = 0)),
            level = "advanced",
        )

        assertEquals(23 * 60, seconds)
        assertEquals("Est. 23 min", formatEstimatedDuration(seconds))
    }

    private fun workoutPayload(
        date: String,
        schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    ): WorkoutSetPayload = WorkoutSetPayload(
        schemaVersion = schemaVersion,
        date = date,
        exercises = listOf(WorkoutExercise(exercise = "Run", reps = "20 min", sets = 1, rest = 0)),
    )

    private fun downloadedEntry(
        date: String,
        schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        sessionState: SessionState? = null,
    ): DownloadedWorkoutEntry = DownloadedWorkoutEntry(
        id = date,
        date = date,
        label = date,
        exercises = listOf(WorkoutExercise(exercise = "Run", reps = "20 min", sets = 1, rest = 0)),
        schemaVersion = schemaVersion,
        sessionState = sessionState,
    )

    private fun completedSession(entryId: String): SessionState = SessionState(
        workoutEntryId = entryId,
        exerciseIndex = 0,
        currentSet = 1,
        status = SessionStatus.COMPLETED,
    )
}
