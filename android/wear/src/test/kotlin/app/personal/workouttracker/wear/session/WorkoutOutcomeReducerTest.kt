package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutOutcomeReducerTest {
    @Test
    fun `completed sets resolve an exercise and update completed over total progress`() {
        var state = state()

        state = apply(state, 1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        state = apply(state, 2, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        val snapshot = state.toProgressSnapshot(elapsedActiveSeconds = 45)

        assertEquals(ExerciseOutcomeStatus.COMPLETED, snapshot.exercises[0].status)
        assertEquals(1, snapshot.progress.completed)
        assertEquals(1, snapshot.progress.pending)
        assertEquals("1/2", snapshot.progress.toWorkoutProgressText().compact)
        assertEquals(
            "1 of 2 exercises completed",
            snapshot.progress.toWorkoutProgressText().accessibility,
        )
    }

    @Test
    fun `partial sets remain pending until skipped`() {
        var state = state()

        state = apply(state, 1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        state = apply(state, 2, "squat", WorkoutOutcomeTransitionType.EXERCISE_SKIPPED)
        val squat = state.exercises[0]

        assertEquals(ExerciseOutcomeStatus.SKIPPED, squat.status)
        assertEquals(1, squat.completedSets)
        assertEquals(0, state.toProgressSnapshot(30).progress.completed)
        assertEquals(1, state.toProgressSnapshot(30).progress.skipped)
    }

    @Test
    fun `duplicate transition is idempotent`() {
        val once = reduceWorkoutOutcome(
            state(),
            transition(1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED),
        )
        val duplicate = reduceWorkoutOutcome(
            once.state,
            transition(1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED),
        )

        assertEquals(WorkoutOutcomeTransitionResultCode.DUPLICATE, duplicate.code)
        assertEquals(1, duplicate.state.exercises[0].completedSets)
        assertEquals(once.state, duplicate.state)
    }

    @Test
    fun `revision gaps and unknown items do not mutate progress`() {
        val initial = state()

        val gap = reduceWorkoutOutcome(
            initial,
            transition(2, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED),
        )
        val unknown = reduceWorkoutOutcome(
            initial,
            transition(1, "missing", WorkoutOutcomeTransitionType.SET_COMPLETED),
        )

        assertEquals(WorkoutOutcomeTransitionResultCode.REVISION_GAP, gap.code)
        assertEquals(WorkoutOutcomeTransitionResultCode.UNKNOWN_ITEM, unknown.code)
        assertEquals(initial, gap.state)
        assertEquals(initial, unknown.state)
    }

    @Test
    fun `resolved exercise refuses later outcome changes`() {
        var state = state()
        state = apply(state, 1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        state = apply(state, 2, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)

        val result = reduceWorkoutOutcome(
            state,
            transition(3, "squat", WorkoutOutcomeTransitionType.EXERCISE_SKIPPED),
        )

        assertEquals(WorkoutOutcomeTransitionResultCode.ALREADY_RESOLVED, result.code)
        assertEquals(state, result.state)
    }

    @Test
    fun `final summary is available only after every exercise is resolved`() {
        var state = state()
        state = apply(state, 1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        state = apply(state, 2, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)

        assertNull(state.toCompletionSummaryOrNull(2_000, 60))

        state = apply(state, 3, "row", WorkoutOutcomeTransitionType.EXERCISE_SKIPPED)
        val summary = state.toCompletionSummaryOrNull(2_000, 60)

        assertNotNull(summary)
        assertEquals(0, summary?.snapshot?.progress?.pending)
        assertEquals("1/2", summary?.snapshot?.progress?.toWorkoutProgressText()?.compact)
    }

    @Test
    fun `invalid initial plans fail before any transition is accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            newWorkoutOutcomeState(
                sessionId = "session-1",
                title = null,
                exercises = listOf(
                    WorkoutExerciseOutcomePlan("same", "one", "One", plannedSets = 1),
                    WorkoutExerciseOutcomePlan("same", "two", "Two", plannedSets = 1),
                ),
            )
        }
    }

    private fun state() = newWorkoutOutcomeState(
        sessionId = "session-1",
        title = "Quick workout",
        exercises = listOf(
            WorkoutExerciseOutcomePlan("squat", "exercise-squat", "Squat", plannedSets = 2),
            WorkoutExerciseOutcomePlan("row", "exercise-row", "Row", plannedSets = 3),
        ),
    )

    private fun apply(
        state: WorkoutOutcomeState,
        revision: Long,
        itemId: String,
        type: WorkoutOutcomeTransitionType,
    ): WorkoutOutcomeState {
        val result = reduceWorkoutOutcome(state, transition(revision, itemId, type))
        assertEquals(WorkoutOutcomeTransitionResultCode.APPLIED, result.code)
        return result.state
    }

    private fun transition(
        revision: Long,
        itemId: String,
        type: WorkoutOutcomeTransitionType,
    ) = WorkoutOutcomeTransition(revision, itemId, type)
}
