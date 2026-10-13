package app.personal.workouttracker.quickstart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneQuickStartRequestTest {
    private val exercise = """{"itemId":"item","exerciseId":"1","exerciseName":"Squat","sets":3,"prescription":"8","restSeconds":60}"""
    private fun request(item: String = exercise, extra: String = "") = """{"requestId":"request","schemaVersion":1,"revision":1,"createdAtMillis":1,"expiresAtMillis":300001,"targetNodeId":"watch","source":"single","exercises":[$item]$extra}"""

    @Test fun ordinaryRequestsDecodeWithoutChangingPrescription() {
        val decoded = decodePhoneQuickStartRequest(request())
        assertEquals("Squat", decoded.exercises.single().exerciseName)
        assertEquals(3, decoded.exercises.single().sets)
        assertEquals(60, decoded.exercises.single().restSeconds)
    }

    @Test fun directGroupedNativeRequestsRejectBeforeUnknownFieldsCanBeLost() {
        for (marker in listOf("null", "{}", """{"id":"group","restSeconds":45}""")) {
            val grouped = exercise.dropLast(1) + ",\"circuit\":$marker}"
            val error = runCatching { decodePhoneQuickStartRequest(request(grouped)) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals("Supersets and circuits require the phone workout player.", error?.message)
        }
    }

    @Test fun topLevelGroupingMarkerRejectsAndOtherForwardCompatibleFieldsRemainAccepted() {
        assertTrue(runCatching { decodePhoneQuickStartRequest(request(extra = ",\"circuit\":null")) }.isFailure)
        assertEquals("Squat", decodePhoneQuickStartRequest(request(extra = ",\"futureDisplayHint\":true")).exercises.single().exerciseName)
    }
}
