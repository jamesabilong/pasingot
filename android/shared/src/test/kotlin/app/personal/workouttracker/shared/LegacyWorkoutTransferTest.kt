package app.personal.workouttracker.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LegacyWorkoutTransferTest {
    private val payload = WorkoutSetPayload(date = "2026-10-14", exercises = listOf(WorkoutExercise("Squat", "8", 3, 60, workoutRowId = 42)))
    private val explicitJson = Json { encodeDefaults = true }
    private val raw get() = Json.encodeToString(payload)
    private fun rejects(value: String) = assertTrue(runCatching { decodeLegacyWorkoutTransfer(value) }.isFailure)

    @Test fun currentFlatPayloadAndOldOmittedDefaultVersionArePreserved() {
        assertEquals(payload, decodeLegacyWorkoutTransfer(raw))
        assertEquals(payload, decodeLegacyWorkoutTransfer(explicitJson.encodeToString(payload).replace("\"schemaVersion\":$CURRENT_SCHEMA_VERSION,", "")))
    }

    @Test fun groupedMarkersCannotBeDiscardedByUnknownKeyDecoding() {
        for (marker in listOf("{\"id\":\"group\",\"restSeconds\":30}", "null", "{}", "false")) {
            rejects(raw.replace("\"exercise\":", "\"circuit\":$marker,\"exercise\":"))
        }
    }

    @Test fun nestedGroupedEnvelopeAndSessionMetadataCannotUseLegacyPath() {
        rejects(raw.replace("\"exercise\":", "\"prescription\":{},\"exercise\":"))
        rejects(raw.replace("\"exercise\":", "\"sessionTime\":\"07:00\",\"exercise\":"))
    }

    @Test fun futureVersionsAreRejectedBeforeAnyDownloadPolicyCanRun() {
        val explicit = explicitJson.encodeToString(payload)
        for (value in listOf("null", "\"$CURRENT_SCHEMA_VERSION\"", "\"future\"")) rejects(explicit.replace("\"schemaVersion\":$CURRENT_SCHEMA_VERSION", "\"schemaVersion\":$value"))
        for (version in listOf(0, 2, CURRENT_SCHEMA_VERSION + 1)) rejects(explicit.replace("\"schemaVersion\":$CURRENT_SCHEMA_VERSION", "\"schemaVersion\":$version"))
    }

    @Test fun unrelatedOptionalMetadataStillLoadsLegacyRecords() {
        assertEquals(payload, decodeLegacyWorkoutTransfer(raw.replace("\"date\":", "\"note\":\"optional\",\"date\":")))
    }

    @Test fun malformedOrOversizePacketsAreRejected() {
        for (value in listOf("null", "[]", "{}", "{", raw.replace("\"exercises\":[", "\"exercises\":{\"items\":["), " ".repeat(MAX_LEGACY_WORKOUT_JSON_LENGTH + 1))) rejects(value)
    }
}
