package app.personal.workouttracker.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GroupedWorkoutTransferTest {
    private val wireJson = Json { encodeDefaults = true }
    private val group = WorkoutCircuit("pair", 30)
    private fun row(id: Long, circuit: WorkoutCircuit? = group, sets: Int = 2, time: String = "07:00") =
        ScheduleRow("Wednesday", time, "Exercise $id", sets, "8", 60, loadWeight = 20.0,
            loadUnit = "kg", workoutRowId = id, circuit = circuit)
    private val rows = listOf(row(1), row(2))
    private fun payload(rows: List<ScheduleRow> = this.rows) = buildGroupedWorkoutTransfer(rows, "2026-10-14", "Wednesday")
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)

    @Test fun roundTripsExactPrescriptionsAndUsesSeparateVersionAndPath() {
        val grouped = payload(listOf(row(1), row(2).copy(reps = "10 sec", loadWeight = 25.0, loadUnit = "lb")))
        assertEquals(grouped, decodeGroupedWorkoutTransfer(encodeGroupedWorkoutTransfer(grouped)))
        assertEquals(2, grouped.schemaVersion)
        assertTrue(encodeGroupedWorkoutTransfer(grouped).contains("\"schemaVersion\":2"))
        assertNotEquals(DataLayerPaths.WORKOUT_SET, GroupedWorkoutProtocol.DATA_PATH)
        assertEquals("10 sec", grouped.exercises[1].prescription.reps)
        assertEquals("lb", grouped.exercises[1].prescription.loadUnit)
        assertEquals(listOf(1L, 2L), grouped.exercises.map { it.prescription.workoutRowId })
    }

    @Test fun mixedPlanHasRoundOrderSharedRoundAndBlockRestAndNoFinalRest() {
        val compiled = compileGroupedWorkoutSteps(payload(listOf(row(3, null, sets = 1), row(1), row(2), row(4, null, sets = 1))))
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1, 1 to 2, 2 to 2, 3 to 1), compiled.map { it.exerciseIndex to it.setNumber })
        assertEquals(listOf(60, 0, 30, 0, 30, 0), compiled.map { it.restSeconds })
    }

    @Test fun sameGroupIdentityAtDifferentTimesStaysTwoCompleteBlocks() {
        val compiled = compileGroupedWorkoutSteps(payload(rows + listOf(row(3, time = "18:00"), row(4, time = "18:00"))))
        assertEquals(listOf(0, 1, 0, 1, 2, 3, 2, 3), compiled.map { it.exerciseIndex })
        assertEquals(listOf(0, 30, 0, 30, 0, 30, 0, 0), compiled.map { it.restSeconds })
    }

    @Test fun threeMemberCircuitRepeatsFullRoundsAndZeroRestWorks() {
        val zero = group.copy(restSeconds = 0)
        val compiled = compileGroupedWorkoutSteps(payload((1L..3L).map { row(it, zero, 3) }))
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1, 2), compiled.map { it.exerciseIndex })
        assertTrue(compiled.all { it.restSeconds == 0 })
    }

    @Test fun projectionsKeepTimeOrderAndResolveDateMovesBeforeCompiling() {
        val moved = rows.map { it.copy(day = "Tuesday", dateOverrides = mapOf("2026-10-13" to ScheduleDateOverride("2026-10-14", "18:00"))) }
        val transfer = payload(moved.reversed() + listOf(row(3, null, sets = 1)))
        assertEquals(listOf(3L, 2L, 1L), transfer.exercises.map { it.prescription.workoutRowId })
        assertEquals(listOf("07:00", "18:00", "18:00"), transfer.exercises.map { it.sessionTime })
        assertEquals(5, compileGroupedWorkoutSteps(transfer).size)
    }

    @Test fun questDayIdentityAndLoadTargetsArePreserved() {
        val quest = rows.map { it.copy(questId = "custom:fixture", questDayIndex = 2, questDayLabel = "Day 2", questLevel = "advanced") }
        assertTrue(payload(quest).exercises.all { it.prescription.questDayIndex == 2 && it.prescription.questLevel == "advanced" })
        rejects { payload(listOf(quest[0], quest[1].copy(questDayIndex = 3))) }
        rejects { payload(listOf(quest[0], quest[1].copy(questId = "other"))) }
    }

    @Test fun malformedGroupsNeverCompileOrEncodeAsFlatSets() {
        for (invalid in listOf(listOf(row(1)), listOf(row(1), row(2, sets = 3)),
            listOf(row(1), row(3, null), row(2)), listOf(row(1), row(2, group.copy(restSeconds = 31))),
            listOf(row(1), row(2, time = "18:00")), (1L..25L).map { row(it) })) rejects { payload(invalid) }
        val base = payload()
        val malformed = base.copy(exercises = base.exercises.dropLast(1))
        assertFalse(validGroupedWorkoutTransfer(malformed))
        rejects { compileGroupedWorkoutSteps(malformed) }
        rejects { encodeGroupedWorkoutTransfer(malformed) }
    }

    @Test fun versionAndFormatChangesCannotSilentlyDecode() {
        val base = payload()
        for (invalid in listOf(base.copy(schemaVersion = 1), base.copy(schemaVersion = 3), base.copy(format = "other"))) {
            assertFalse(validGroupedWorkoutTransfer(invalid))
            rejects { decodeGroupedWorkoutTransfer(wireJson.encodeToString(invalid)) }
        }
        rejects { decodeGroupedWorkoutTransfer(encodeGroupedWorkoutTransfer(base).replace("\"schemaVersion\":2,", "")) }
        rejects { decodeGroupedWorkoutTransfer(encodeGroupedWorkoutTransfer(base).replace("\"format\":\"pasingot-grouped-workout\",", "")) }
        rejects { decodeGroupedWorkoutTransfer(encodeGroupedWorkoutTransfer(base).replace("\"date\"", "\"futureExecution\":true,\"date\"")) }
    }

    @Test fun duplicateOrAbsentLoggingIdentityIsRejected() {
        rejects { payload(listOf(row(1), row(1))) }
        rejects { payload(listOf(row(1), row(2).copy(workoutRowId = null))) }
        rejects { payload(listOf(row(0), row(2))) }
        // Repeated exercise names remain distinct when their scheduled row IDs differ.
        assertTrue(validGroupedWorkoutTransfer(payload(rows.map { it.copy(exercise = "Same exercise") })))
    }

    @Test fun invalidDatesTimesTargetsAndLoadsAreRejectedBeforeCompilation() {
        val base = payload()
        for (date in listOf("2026-02-30", "2026-1-01", "1899-01-01")) assertFalse(validGroupedWorkoutTransfer(base.copy(date = date)))
        for (invalid in listOf(row(1).copy(time = "24:00"), row(1).copy(reps = ""), row(1).copy(rest = 3601),
            row(1).copy(loadWeight = Double.NaN), row(1).copy(loadWeight = -1.0), row(1).copy(loadUnit = "stone"))) rejects { payload(listOf(invalid, row(2))) }
        assertFalse(validGroupedWorkoutTransfer(base.copy(exercises = base.exercises.reversed().mapIndexed { index, item -> item.copy(sessionTime = if (index == 0) "18:00" else "07:00") })))
    }

    @Test fun boundIsLargeEnoughForMultipleDailySessionsButCannotOverflowSteps() {
        val maximum = (0 until 10).flatMap { block -> (1L..24L).map { member ->
            row(block * 24L + member, group.copy(id = "group$block", restSeconds = 3600), 100, "%02d:00".format(block))
        } }
        val transfer = payload(maximum)
        assertEquals(24000, compileGroupedWorkoutSteps(transfer).size)
        assertTrue(encodeGroupedWorkoutTransfer(payload(maximum.take(24))).length < GroupedWorkoutProtocol.MAX_JSON_LENGTH)
        rejects { payload(maximum + row(241, null, time = "18:00")) }
        rejects { decodeGroupedWorkoutTransfer(" ".repeat(GroupedWorkoutProtocol.MAX_JSON_LENGTH + 1)) }
    }

    @Test fun utf8WireSizeBoundCannotBeBypassedWithMultibyteNames() {
        val large = (0 until 5).flatMap { block -> (1L..24L).map { member ->
            row(block * 24L + member, group.copy(id = "group$block"), time = "%02d:00".format(block)).copy(exercise = "界".repeat(150))
        } }
        val transfer = payload(large)
        assertTrue(validGroupedWorkoutTransfer(transfer))
        rejects { encodeGroupedWorkoutTransfer(transfer) }
        val raw = wireJson.encodeToString(transfer)
        assertTrue(raw.length < GroupedWorkoutProtocol.MAX_JSON_LENGTH)
        assertTrue(raw.toByteArray(Charsets.UTF_8).size > GroupedWorkoutProtocol.MAX_UTF8_BYTES)
        rejects { decodeGroupedWorkoutTransfer(raw) }
    }

    @Test fun flatAndSkippedDatesDoNotCreateGroupedPackets() {
        rejects { payload(rows.map { it.copy(circuit = null) }) }
        rejects { payload(rows.map { it.copy(dateOverrides = mapOf("2026-10-14" to null)) }) }
    }

    @Test fun currentProductionTransferGuardStillRejectsGroupedDays() {
        assertTrue(runCatching { buildWorkoutTransfer(rows, "2026-10-14", "Wednesday") }.exceptionOrNull() is CircuitRequiresPhoneException)
        assertEquals(CURRENT_SCHEMA_VERSION, buildWorkoutTransfer(rows.map { it.copy(circuit = null) }, "2026-10-14", "Wednesday").schemaVersion)
    }
}
