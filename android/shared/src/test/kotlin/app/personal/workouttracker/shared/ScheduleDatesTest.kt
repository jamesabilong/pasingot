package app.personal.workouttracker.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScheduleDatesTest {
    private val row = ScheduleRow("Tuesday", "07:00", "Squat", 2, "8", 30, loadWeight = 25.0, loadUnit = "kg", workoutRowId = 1)
    private val source = "2026-10-13"
    private val destination = "2026-10-14"

    @Test fun movedOccurrenceChangesOnlyTheSpecifiedDatesAndKeepsIdentity() {
        val moved = row.copy(dateOverrides = mapOf(source to ScheduleDateOverride(destination, "08:15")))
        assertTrue(buildWorkoutTransfer(listOf(moved), source, "Tuesday").exercises.isEmpty())
        val result = buildWorkoutTransfer(listOf(moved), destination, "Wednesday").exercises.single()
        assertEquals(1L, result.workoutRowId)
        assertEquals(25.0, result.loadWeight!!, 0.0)
        assertEquals(listOf(row), scheduleRowsOnDate(listOf(row), "2026-10-20", "Tuesday"))
        assertEquals(1, buildWorkoutTransfer(listOf(moved), "2026-10-20", "Tuesday").exercises.size)
    }

    @Test fun skipAndSameDateTimeMoveDoNotDuplicateRecurrence() {
        assertTrue(scheduleRowsOnDate(listOf(row.copy(dateOverrides = mapOf(source to null))), source, "Tuesday").isEmpty())
        val changed = row.copy(dateOverrides = mapOf(source to ScheduleDateOverride(source, "18:00")))
        assertEquals("18:00", scheduleRowsOnDate(listOf(changed), source, "Tuesday").single().time)
    }

    @Test fun incomingSessionsSortWithRecurringSessionsAndKeepWithinSessionOrder() {
        val moved = row.copy(dateOverrides = mapOf(source to ScheduleDateOverride(destination, "08:15")))
        val second = moved.copy(exercise = "Press", workoutRowId = 2)
        val morning = row.copy(day = "Wednesday", time = "06:00", exercise = "Walk", workoutRowId = 3)
        assertEquals(listOf("Walk", "Squat", "Press"), buildWorkoutTransfer(listOf(moved, second, morning), destination, "Wednesday").exercises.map { it.exercise })
    }

    @Test fun cacheRoundTripPreservesNullSkipsAndMovesWhileLegacyRowsRemainValid() {
        val changed = row.copy(dateOverrides = mapOf(source to null, "2026-10-20" to ScheduleDateOverride("2026-10-21", "08:15")))
        val cached = CachedSchedule(rows = listOf(changed, row.copy(workoutRowId = 2)))
        assertEquals(cached, Json.decodeFromString(CachedSchedule.serializer(), Json.encodeToString(cached)))
        val legacy = Json.decodeFromString(ScheduleRow.serializer(), """{"day":"Tuesday","time":"07:00","exercise":"Squat","sets":2,"reps":"8","rest":30}""")
        assertTrue(legacy.dateOverrides.isEmpty())
        assertTrue(validScheduleDateOverrides(legacy))
    }

    @Test fun malformedOverridesAndQuestOverridesAreRejected() {
        for ((sourceDate, target) in listOf(
            "2026-02-30" to null,
            source to ScheduleDateOverride("2026-02-30", "07:00"),
            source to ScheduleDateOverride(destination, "24:00"),
            "2026-1-3" to null,
        )) assertFalse(validScheduleDateOverrides(row.copy(dateOverrides = mapOf(sourceDate to target))))
        assertFalse(validScheduleDateOverrides(row.copy(questId = "quest", dateOverrides = mapOf(source to null))))
    }
}
