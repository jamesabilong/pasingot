package app.personal.workouttracker.shared.session

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProgressSummaryTest {
    @Test
    fun `progress derives completed skipped and pending exercise counts`() {
        val exercises = listOf(
            outcome("completed", ExerciseOutcomeStatus.COMPLETED, completedSets = 3),
            outcome("skipped", ExerciseOutcomeStatus.SKIPPED, completedSets = 1),
            outcome("pending-1", ExerciseOutcomeStatus.PENDING, completedSets = 0),
            outcome("pending-2", ExerciseOutcomeStatus.PENDING, completedSets = 1),
        )

        assertEquals(
            ExerciseProgressCounts(total = 4, completed = 1, skipped = 1, pending = 2),
            summarizeExerciseProgress(exercises),
        )
    }

    @Test
    fun `valid progress snapshot includes explicit pending number and set totals`() {
        val exercises = listOf(
            outcome("done", ExerciseOutcomeStatus.COMPLETED, completedSets = 3),
            outcome("pending", ExerciseOutcomeStatus.PENDING, completedSets = 1),
        )
        val snapshot = snapshot(exercises)

        assertNull(validateWorkoutProgressSnapshot(snapshot))
        assertEquals(1, snapshot.progress.pending)
        assertEquals(4, snapshot.completedSets)
        assertEquals(6, snapshot.plannedSets)
    }

    @Test
    fun `completion requires zero pending exercises`() {
        val exercises = listOf(
            outcome("done", ExerciseOutcomeStatus.COMPLETED, completedSets = 3),
            outcome("pending", ExerciseOutcomeStatus.PENDING, completedSets = 1),
        )
        val summary = WorkoutCompletionSummary(
            completedAtEpochMillis = 1_800_000_000_000L,
            snapshot = snapshot(exercises),
        )

        assertEquals(
            WorkoutSummaryValidationCode.PENDING_COMPLETION,
            validateWorkoutCompletionSummary(summary)?.code,
        )
    }

    @Test
    fun `completion accepts completed and skipped exercises`() {
        val exercises = listOf(
            outcome("done", ExerciseOutcomeStatus.COMPLETED, completedSets = 3),
            outcome("skipped", ExerciseOutcomeStatus.SKIPPED, completedSets = 1),
        )
        val summary = WorkoutCompletionSummary(
            completedAtEpochMillis = 1_800_000_000_000L,
            snapshot = snapshot(exercises),
        )

        assertNull(validateWorkoutCompletionSummary(summary))
        assertEquals(0, summary.snapshot.progress.pending)
    }

    @Test
    fun `mismatched progress and set totals are rejected`() {
        val exercises = listOf(outcome("done", ExerciseOutcomeStatus.COMPLETED, completedSets = 3))
        val valid = snapshot(exercises)

        assertEquals(
            WorkoutSummaryValidationCode.INVALID_PROGRESS_COUNTS,
            validateWorkoutProgressSnapshot(
                valid.copy(progress = valid.progress.copy(pending = 1)),
            )?.code,
        )
        assertEquals(
            WorkoutSummaryValidationCode.INVALID_SET_COUNTS,
            validateWorkoutProgressSnapshot(valid.copy(completedSets = 2))?.code,
        )
    }

    @Test
    fun `invalid outcome semantics and duplicate identity are rejected`() {
        val invalidCompleted = outcome(
            "same",
            ExerciseOutcomeStatus.COMPLETED,
            completedSets = 2,
        )
        assertEquals(
            WorkoutSummaryValidationCode.INVALID_EXERCISE,
            validateWorkoutProgressSnapshot(snapshot(listOf(invalidCompleted)))?.code,
        )

        val duplicates = listOf(
            outcome("same", ExerciseOutcomeStatus.COMPLETED, completedSets = 3),
            outcome("same", ExerciseOutcomeStatus.PENDING, completedSets = 0),
        )
        assertEquals(
            WorkoutSummaryValidationCode.DUPLICATE_ITEM_ID,
            validateWorkoutProgressSnapshot(snapshot(duplicates))?.code,
        )
    }

    @Test
    fun `serialization uses stable lower-case status and pending field`() {
        val snapshot = snapshot(
            listOf(outcome("pending", ExerciseOutcomeStatus.PENDING, completedSets = 1)),
        )

        val json = Json.encodeToString(snapshot)

        assertTrue(json.contains("\"status\":\"pending\""))
        assertTrue(json.contains("\"pending\":1"))
    }

    private fun snapshot(exercises: List<ExerciseOutcome>): WorkoutProgressSnapshot =
        WorkoutProgressSnapshot(
            sessionId = "session-1",
            title = "Quick workout",
            progress = summarizeExerciseProgress(exercises),
            completedSets = exercises.sumOf { it.completedSets },
            plannedSets = exercises.sumOf { it.plannedSets },
            elapsedActiveSeconds = 180,
            estimatedDurationSeconds = 300,
            exercises = exercises,
        )

    private fun outcome(
        itemId: String,
        status: ExerciseOutcomeStatus,
        completedSets: Int,
    ) = ExerciseOutcome(
        itemId = itemId,
        exerciseId = "exercise-$itemId",
        exerciseName = "Exercise $itemId",
        status = status,
        completedSets = completedSets,
        plannedSets = 3,
    )
}
