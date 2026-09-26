package app.personal.workouttracker.shared.quickstart

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartCapabilityTest {
    private val phone = localQuickStartCapability("phone-node", QuickStartNodeRole.PHONE)
    private val watch = localQuickStartCapability("watch-node", QuickStartNodeRole.WATCH)

    @Test
    fun `current phone and watch agree on request schema one and bind selected node`() {
        val result = negotiateQuickStartCapability(
            phone, encodeQuickStartCapability(watch), "watch-node", remoteReachable = true,
        )

        assertEquals(QuickStartCapabilityDecision.Compatible("watch-node", 1), result)
        assertTrue(encodeQuickStartCapability(watch).contains("\"role\":\"watch\""))
        assertEquals(listOf(QUICK_START_SCHEMA_VERSION), watch.supportedRequestSchemaVersions)
    }

    @Test
    fun `disconnected and old watch with no capability have separate outcomes`() {
        assertUnavailable(
            phone, encodeQuickStartCapability(watch), reachable = false,
            expected = QuickStartCapabilityUnavailableReason.UNREACHABLE,
        )
        assertUnavailable(
            phone, null, expected = QuickStartCapabilityUnavailableReason.MISSING_CAPABILITY,
        )
    }

    @Test
    fun `node mismatch and same-role advertisement are refused`() {
        assertUnavailable(
            phone, encodeQuickStartCapability(watch), selectedNodeId = "other-watch",
            expected = QuickStartCapabilityUnavailableReason.NODE_MISMATCH,
        )
        assertUnavailable(
            phone, encodeQuickStartCapability(watch.copy(nodeId = "phone-node")),
            selectedNodeId = "phone-node",
            expected = QuickStartCapabilityUnavailableReason.NODE_MISMATCH,
        )
        assertUnavailable(
            phone, encodeQuickStartCapability(watch.copy(role = QuickStartNodeRole.PHONE)),
            expected = QuickStartCapabilityUnavailableReason.ROLE_MISMATCH,
        )
    }

    @Test
    fun `new phone can use old watch only when it still implements schema one`() {
        val newPhone = phone.copy(supportedRequestSchemaVersions = listOf(1, 2))
        assertEquals(
            QuickStartCapabilityDecision.Compatible("watch-node", 1),
            negotiateQuickStartCapability(newPhone, encodeQuickStartCapability(watch), "watch-node", true),
        )
        assertUnavailable(
            phone.copy(supportedRequestSchemaVersions = listOf(2)),
            encodeQuickStartCapability(watch),
            expected = QuickStartCapabilityUnavailableReason.NO_COMMON_REQUEST_SCHEMA,
        )
    }

    @Test
    fun `old phone can use new watch only when watch still accepts schema one`() {
        val newWatch = watch.copy(supportedRequestSchemaVersions = listOf(1, 2))
        assertEquals(
            QuickStartCapabilityDecision.Compatible("phone-node", 1),
            negotiateQuickStartCapability(newWatch, encodeQuickStartCapability(phone), "phone-node", true),
        )
        assertUnavailable(
            watch.copy(supportedRequestSchemaVersions = listOf(2)),
            encodeQuickStartCapability(phone),
            selectedNodeId = "phone-node",
            expected = QuickStartCapabilityUnavailableReason.NO_COMMON_REQUEST_SCHEMA,
        )
    }

    @Test
    fun `unknown capability envelope is rejected even with common request codec`() {
        assertUnavailable(
            phone, Json.encodeToString(watch.copy(capabilitySchemaVersion = 2)),
            expected = QuickStartCapabilityUnavailableReason.UNSUPPORTED_CAPABILITY_SCHEMA,
        )
    }

    @Test
    fun `compatible additive field is ignored in version one envelope`() {
        val encoded = encodeQuickStartCapability(watch)
            .replaceFirst("{", "{\"optionalFutureField\":true,")
        assertEquals(
            QuickStartCapabilityDecision.Compatible("watch-node", 1),
            negotiateQuickStartCapability(phone, encoded, "watch-node", true),
        )
    }

    @Test
    fun `missing fields malformed payloads and impossible version lists fail closed`() {
        val invalid = listOf(
            "", "{}", "null", "not-json", "{\"nodeId\":\"watch-node\"}",
            Json.encodeToString(watch.copy(nodeId = "")),
            Json.encodeToString(watch.copy(nodeId = "watch\nnode")),
            Json.encodeToString(watch.copy(supportedRequestSchemaVersions = emptyList())),
            Json.encodeToString(watch.copy(supportedRequestSchemaVersions = listOf(1, 1))),
            Json.encodeToString(watch.copy(supportedRequestSchemaVersions = listOf(0))),
            "x".repeat(4_097),
        )
        invalid.forEach { payload ->
            assertUnavailable(
                phone, payload,
                expected = QuickStartCapabilityUnavailableReason.MALFORMED_CAPABILITY,
            )
        }
    }

    @Test
    fun `local advertisement helper rejects invalid identities and versions`() {
        val invalidLocal = phone.copy(supportedRequestSchemaVersions = emptyList())
        assertUnavailable(
            invalidLocal, encodeQuickStartCapability(watch),
            expected = QuickStartCapabilityUnavailableReason.INVALID_LOCAL_CAPABILITY,
        )
        assertThrows(IllegalArgumentException::class.java) {
            encodeQuickStartCapability(invalidLocal)
        }
    }

    @Test
    fun `agreed version one still uses the validated request codec`() {
        val negotiated = negotiateQuickStartCapability(
            phone, encodeQuickStartCapability(watch), "watch-node", true,
        ) as QuickStartCapabilityDecision.Compatible
        val request = QuickStartRequest(
            requestId = "123e4567-e89b-12d3-a456-426614174000",
            schemaVersion = negotiated.requestSchemaVersion,
            createdAtMillis = 1_800_000_000_000,
            expiresAtMillis = 1_800_000_300_000,
            targetNodeId = negotiated.nodeId,
            source = QuickStartSource.SINGLE,
            exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 2, "10 reps", 60)),
        )
        assertTrue(validateQuickStartRequest(request, request.createdAtMillis) is QuickStartValidationResult.Valid)
        assertTrue(
            validateQuickStartRequest(request.copy(schemaVersion = 2), request.createdAtMillis) is
                QuickStartValidationResult.Invalid,
        )
    }

    private fun assertUnavailable(
        local: QuickStartCapability,
        payload: String?,
        selectedNodeId: String = "watch-node",
        reachable: Boolean = true,
        expected: QuickStartCapabilityUnavailableReason,
    ) {
        assertEquals(
            QuickStartCapabilityDecision.Unavailable(expected),
            negotiateQuickStartCapability(local, payload, selectedNodeId, reachable),
        )
    }
}
