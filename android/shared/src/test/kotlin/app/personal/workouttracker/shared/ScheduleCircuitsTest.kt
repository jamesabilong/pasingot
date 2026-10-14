package app.personal.workouttracker.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCircuitsTest {
    private val circuit = WorkoutCircuit("fixture_group", 45)
    private val first = ScheduleRow("Tuesday", "07:00", "Squat", 3, "8", 60, workoutRowId = 1, circuit = circuit)
    private val second = first.copy(exercise = "Press", workoutRowId = 2)
    private val group = listOf(first, second)

    @Test fun cachedSerializationRetainsCircuitMetadataAndLegacyRowsRemainFlat() {
        val encoded = Json.encodeToString(CachedSchedule(rows = group))
        val cached = Json.decodeFromString(CachedSchedule.serializer(), encoded)
        assertEquals(group, cached.rows)
        assertTrue(validScheduleCircuits(cached.rows))
        val legacy = Json.decodeFromString(CachedSchedule.serializer(), """{"rows":[{"day":"Tuesday","time":"07:00","exercise":"Squat","sets":3,"reps":"8","rest":60}]}""")
        assertEquals(null, legacy.rows.single().circuit)
        assertTrue(validScheduleCircuits(legacy.rows))
    }

    @Test fun groupedDayRejectsEntireLegacyTransferInsteadOfFlatteningOrDroppingMembers() {
        val rows = group + first.copy(exercise = "Walk", circuit = null)
        val before = rows.toList()
        val failure = runCatching { buildWorkoutTransfer(rows, "2026-10-13", "Tuesday") }.exceptionOrNull()
        assertTrue(failure is CircuitRequiresPhoneException)
        assertEquals("Today's supersets and circuits require the phone workout player.", failure?.message)
        assertEquals(before, rows)
    }

    @Test fun flatOtherDatesCanStillTransferWithGroupsElsewhereInCache() {
        val flat = first.copy(day = "Wednesday", circuit = null)
        val payload = buildWorkoutTransfer(group + flat, "2026-10-14", "Wednesday")
        assertEquals(listOf("Squat"), payload.exercises.map { it.exercise })
        assertEquals(1L, payload.exercises.single().workoutRowId)
    }

    @Test fun dateProjectionGateFollowsMovedAndSkippedCircuitOccurrences() {
        val moved = group.map { it.copy(dateOverrides = mapOf("2026-10-13" to ScheduleDateOverride("2026-10-14", "08:00"))) }
        assertTrue(buildWorkoutTransfer(moved, "2026-10-13", "Tuesday").exercises.isEmpty())
        assertTrue(runCatching { buildWorkoutTransfer(moved, "2026-10-14", "Wednesday") }.exceptionOrNull() is CircuitRequiresPhoneException)
        val skipped = group.map { it.copy(dateOverrides = mapOf("2026-10-13" to null)) }
        assertTrue(buildWorkoutTransfer(skipped, "2026-10-13", "Tuesday").exercises.isEmpty())
    }

    @Test fun malformedGroupIdsAndRoundRestAreRejected() {
        for (invalid in listOf(WorkoutCircuit("", 45), WorkoutCircuit("has space", 45), WorkoutCircuit("x".repeat(101), 45), WorkoutCircuit("valid", -1), WorkoutCircuit("valid", 3601))) {
            assertFalse(validScheduleCircuits(group.map { it.copy(circuit = invalid) }))
        }
        assertTrue(validScheduleCircuits(group.map { it.copy(circuit = circuit.copy(restSeconds = 0)) }))
        assertTrue(validScheduleCircuits(group.map { it.copy(circuit = circuit.copy(restSeconds = 3600)) }))
    }

    @Test fun groupsRequireMatchingRoundsRestAndAtLeastTwoMembers() {
        assertFalse(validScheduleCircuits(listOf(first)))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(sets = 2))))
        assertFalse(validScheduleCircuits(group.map { it.copy(sets = 0) }))
        assertFalse(validScheduleCircuits(group.map { it.copy(sets = 101) }))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(circuit = circuit.copy(restSeconds = 46)))))
        assertFalse(validScheduleCircuits((1..25).map { first.copy(workoutRowId = it.toLong()) }))
    }

    @Test fun splitBlocksAndQuestOwnedMembersAreRejected() {
        assertFalse(validScheduleCircuits(group + first.copy(circuit = null) + group))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(questId = "quest"))))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(dateOverrides = mapOf("2026-10-13" to null)))))
    }

    @Test fun groupIdentityCanRepeatAcrossDifferentSlotsButCannotJoinSlots() {
        assertTrue(validScheduleCircuits(group + group.map { it.copy(time = "18:00") }))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(time = "18:00"))))
        assertFalse(validScheduleCircuits(listOf(first, second.copy(day = "Wednesday"))))
    }
    @Test fun questGroupsPreserveOwnershipAndStillRequirePhoneExecution() {
        val quest = group.map { it.copy(questId = "custom:fixture", questDayIndex = 1, questLevel = "beginner") }
        assertTrue(validScheduleCircuits(quest))
        assertEquals(quest, Json.decodeFromString(CachedSchedule.serializer(), Json.encodeToString(CachedSchedule(rows = quest))).rows)
        assertTrue(runCatching { buildWorkoutTransfer(quest, "2026-10-13", "Tuesday") }.exceptionOrNull() is CircuitRequiresPhoneException)
        assertFalse(validScheduleCircuits(listOf(quest[0], quest[1].copy(questDayIndex = 2))))
        assertFalse(validScheduleCircuits(listOf(quest[0], quest[1].copy(questLevel = "advanced"))))
        assertFalse(validScheduleCircuits(listOf(quest[0], quest[1].copy(questId = "other"))))
    }

}
