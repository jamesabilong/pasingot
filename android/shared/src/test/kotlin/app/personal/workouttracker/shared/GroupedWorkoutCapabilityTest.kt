package app.personal.workouttracker.shared

import org.junit.Assert.*
import org.junit.Test

class GroupedWorkoutCapabilityTest {
    private val capability = GroupedWorkoutCapability(nodeId = "watch", supportedWorkoutSchemaVersions = listOf(2))
    private val raw get() = encodeGroupedWorkoutCapability(capability)

    @Test fun exactReachableWatchCanSupportTheNewContract() {
        assertTrue(supportsGroupedWorkoutTransfer(raw, "watch", true))
        assertTrue(supportsGroupedWorkoutTransfer(encodeGroupedWorkoutCapability(capability.copy(supportedWorkoutSchemaVersions = listOf(1, 2, 3))), "watch", true))
    }

    @Test fun missingDisconnectedWrongNodeAndOldWatchDoNotAuthorizeSend() {
        assertFalse(supportsGroupedWorkoutTransfer(null, "watch", true))
        assertFalse(supportsGroupedWorkoutTransfer(raw, "watch", false))
        assertFalse(supportsGroupedWorkoutTransfer(raw, "other", true))
        assertFalse(supportsGroupedWorkoutTransfer(encodeGroupedWorkoutCapability(capability.copy(supportedWorkoutSchemaVersions = listOf(1))), "watch", true))
    }

    @Test fun malformedRoleVersionAndUnboundedAdvertisementsFailClosed() {
        for (invalid in listOf("{}", "[]", raw.replace("\"watch\"", "\"phone\""), " ".repeat(4097),
            raw.replace("[2]", "[2,2]"), raw.replace("[2]", "[0]"), raw.replace("[2]", "[1,2,3,4,5,6,7,8,9]"))) {
            assertFalse(supportsGroupedWorkoutTransfer(invalid, "watch", true))
        }
        assertFalse(supportsGroupedWorkoutTransfer("{\"capabilitySchemaVersion\":2,\"nodeId\":\"watch\",\"supportedWorkoutSchemaVersions\":[2]}", "watch", true))
    }

    @Test fun invalidLocalAdvertisementsCannotEncode() {
        for (invalid in listOf(capability.copy(nodeId = ""), capability.copy(nodeId = "watch\n"),
            capability.copy(role = "phone"), capability.copy(supportedWorkoutSchemaVersions = emptyList()),
            capability.copy(supportedWorkoutSchemaVersions = listOf(2, 2)))) {
            assertTrue(runCatching { encodeGroupedWorkoutCapability(invalid) }.isFailure)
        }
    }
}
