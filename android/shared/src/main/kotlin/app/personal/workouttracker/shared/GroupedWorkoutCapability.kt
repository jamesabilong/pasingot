package app.personal.workouttracker.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Future executor advertisement. No current app publishes this capability. */
@Serializable
data class GroupedWorkoutCapability(
    val capabilitySchemaVersion: Int = 1,
    val nodeId: String,
    val supportedWorkoutSchemaVersions: List<Int>,
    val role: String = "watch",
)

private val groupedCapabilityJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private fun validGroupedCapability(value: GroupedWorkoutCapability) =
    value.capabilitySchemaVersion == 1 && value.role == "watch" && value.nodeId.isNotBlank() &&
        value.nodeId.length <= 256 && value.nodeId.none { it.isISOControl() } &&
        value.supportedWorkoutSchemaVersions.size in 1..8 &&
        value.supportedWorkoutSchemaVersions.all { it > 0 } &&
        value.supportedWorkoutSchemaVersions.distinct().size == value.supportedWorkoutSchemaVersions.size

fun encodeGroupedWorkoutCapability(value: GroupedWorkoutCapability): String {
    require(validGroupedCapability(value)) { "Invalid grouped workout capability" }
    return groupedCapabilityJson.encodeToString(value)
}

/** Missing, stale, disconnected and wrong-node advertisements never authorize sending. */
fun supportsGroupedWorkoutTransfer(raw: String?, selectedNodeId: String, reachable: Boolean): Boolean {
    if (!reachable || raw == null || raw.length > 4096) return false
    val envelope = runCatching { groupedCapabilityJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return false
    val version = envelope["capabilitySchemaVersion"] as? JsonPrimitive
    if (version == null || version.isString || version.intOrNull != 1 ||
        (envelope["role"] as? JsonPrimitive)?.content != "watch") return false
    val remote = runCatching { groupedCapabilityJson.decodeFromString(GroupedWorkoutCapability.serializer(), raw) }.getOrNull()
        ?: return false
    return validGroupedCapability(remote) && remote.nodeId == selectedNodeId &&
        GroupedWorkoutProtocol.VERSION in remote.supportedWorkoutSchemaVersions
}
