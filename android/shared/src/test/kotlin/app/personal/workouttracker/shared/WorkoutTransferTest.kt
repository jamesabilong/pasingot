package app.personal.workouttracker.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTransferTest {
    @Test
    fun sendsOnlyRequestedDayInTimeOrderWithStableOrderWithinASession() {
        val rows = listOf(
            row("Thursday", "18:00", "Evening"),
            row("Friday", "06:00", "Tomorrow"),
            row("thursday", "07:00", "First"),
            row("Thursday", "07:00", "Second"),
        )
        val payload = buildWorkoutTransfer(rows, "2026-09-10", "Thursday")
        assertEquals("2026-09-10", payload.date)
        assertEquals(listOf("First", "Second", "Evening"), payload.exercises.map { it.exercise })
        assertEquals(CURRENT_SCHEMA_VERSION, payload.schemaVersion)
    }

    @Test
    fun preservesLoggingIdentityAndQuestPrescription() {
        val row = row("Thursday", "07:00", "Squat").copy(
            loadWeight = 45.0, loadUnit = "lb", workoutRowId = 42,
            questId = "foundations", questDayIndex = 2, questDayLabel = "Day 3", questLevel = "beginner",
        )
        val actual = buildWorkoutTransfer(listOf(row), "2026-09-10", "Thursday").exercises.single()
        assertEquals(WorkoutExercise(
            exercise = "Squat", reps = "8-10", sets = 3, rest = 60,
            loadWeight = 45.0, loadUnit = "lb", workoutRowId = 42,
            questId = "foundations", questDayIndex = 2, questDayLabel = "Day 3", questLevel = "beginner",
        ), actual)
    }

    @Test
    fun restDayHasNoExercises() {
        assertTrue(buildWorkoutTransfer(listOf(row("Friday", "07:00", "Squat")), "2026-09-10", "Thursday").exercises.isEmpty())
    }

    private fun row(day: String, time: String, exercise: String) =
        ScheduleRow(day = day, time = time, exercise = exercise, sets = 3, reps = "8-10", rest = 60)
}
