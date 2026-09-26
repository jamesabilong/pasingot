package app.personal.workouttracker.shared.session

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEndedSummaryTest {
    @Test
    fun `ending before the first set preserves every pending exercise`() {
        val summary = summary(listOf(outcome("first", ExerciseOutcomeStatus.PENDING, 0)))

        assertNull(validateWorkoutEndedSummary(summary))
        assertEquals(0, summary.snapshot.completedSets)
        assertEquals(1, summary.snapshot.progress.pending)
    }

    @Test
    fun `ending retains partial completed and skipped outcomes without resolving pending`() {
        val summary = summary(listOf(
            outcome("done", ExerciseOutcomeStatus.COMPLETED, 3),
            outcome("skip", ExerciseOutcomeStatus.SKIPPED, 1),
            outcome("partial", ExerciseOutcomeStatus.PENDING, 2),
            outcome("untouched", ExerciseOutcomeStatus.PENDING, 0),
        ))

        assertNull(validateWorkoutEndedSummary(summary))
        assertEquals(6, summary.snapshot.completedSets)
        assertEquals(ExerciseProgressCounts(4, 1, 1, 2), summary.snapshot.progress)
        assertEquals(ExerciseOutcomeStatus.PENDING, summary.snapshot.exercises[2].status)
    }

    @Test
    fun `fully resolved workouts cannot be labelled ended before completion`() {
        val summary = summary(listOf(
            outcome("done", ExerciseOutcomeStatus.COMPLETED, 3),
            outcome("skip", ExerciseOutcomeStatus.SKIPPED, 0),
        ))

        assertEquals(
            WorkoutSummaryValidationIssue(WorkoutSummaryValidationCode.NO_PENDING_END, field = "progress.pending"),
            validateWorkoutEndedSummary(summary),
        )
    }

    @Test
    fun `unsupported summary schema and negative ending time are rejected`() {
        val summary = summary(listOf(outcome("first", ExerciseOutcomeStatus.PENDING, 0)))

        assertEquals(
            WorkoutSummaryValidationCode.UNSUPPORTED_SCHEMA,
            validateWorkoutEndedSummary(summary.copy(schemaVersion = 2))?.code,
        )
        assertEquals(
            WorkoutSummaryValidationIssue(
                WorkoutSummaryValidationCode.INVALID_COMPLETION_TIME,
                field = "endedAtEpochMillis",
            ),
            validateWorkoutEndedSummary(summary.copy(endedAtEpochMillis = -1)),
        )
        assertNull(validateWorkoutEndedSummary(summary.copy(endedAtEpochMillis = 0)))
    }

    @Test
    fun `ended summary validates full snapshot before terminal classification`() {
        val summary = summary(listOf(outcome("first", ExerciseOutcomeStatus.PENDING, 0)))

        assertEquals(
            WorkoutSummaryValidationCode.UNSUPPORTED_SCHEMA,
            validateWorkoutEndedSummary(summary.copy(snapshot = summary.snapshot.copy(schemaVersion = 2)))?.code,
        )
        assertEquals(
            WorkoutSummaryValidationCode.INVALID_PROGRESS_COUNTS,
            validateWorkoutEndedSummary(summary.copy(
                snapshot = summary.snapshot.copy(progress = summary.snapshot.progress.copy(pending = 0)),
            ))?.code,
        )
        assertEquals(
            WorkoutSummaryValidationCode.INVALID_SET_COUNTS,
            validateWorkoutEndedSummary(summary.copy(snapshot = summary.snapshot.copy(completedSets = 1)))?.code,
        )
        assertEquals(
            WorkoutSummaryValidationCode.INVALID_DURATION,
            validateWorkoutEndedSummary(summary.copy(snapshot = summary.snapshot.copy(elapsedActiveSeconds = -1)))?.code,
        )
    }

    @Test
    fun `serialization round trip preserves ended time and pending progress`() {
        val summary = summary(listOf(outcome("partial", ExerciseOutcomeStatus.PENDING, 1)))
        val raw = Json.encodeToString(summary)

        assertTrue(raw.contains("\"endedAtEpochMillis\":1800000000000"))
        assertTrue(raw.contains("\"status\":\"pending\""))
        assertEquals(summary, Json.decodeFromString<WorkoutEndedSummary>(raw))
    }

    private fun summary(exercises: List<ExerciseOutcome>) = WorkoutEndedSummary(
        endedAtEpochMillis = 1_800_000_000_000L,
        snapshot = WorkoutProgressSnapshot(
            sessionId = "session-1",
            title = "Quick workout",
            progress = summarizeExerciseProgress(exercises),
            completedSets = exercises.sumOf { it.completedSets },
            plannedSets = exercises.sumOf { it.plannedSets },
            elapsedActiveSeconds = 0,
            estimatedDurationSeconds = 300,
            exercises = exercises,
        ),
    )

    private fun outcome(itemId: String, status: ExerciseOutcomeStatus, completedSets: Int) = ExerciseOutcome(
        itemId = itemId,
        exerciseId = "exercise-$itemId",
        exerciseName = "Exercise $itemId",
        status = status,
        completedSets = completedSets,
        plannedSets = 3,
    )
}
