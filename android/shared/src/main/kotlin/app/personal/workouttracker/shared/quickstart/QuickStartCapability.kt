package app.personal.workouttracker.shared.quickstart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val QUICK_START_CAPABILITY_SCHEMA_VERSION: Int = 1

private const val MAX_NODE_ID_LENGTH = 256
private const val MAX_CAPABILITY_LENGTH = 4_096
private const val MAX_ADVERTISED_SCHEMAS = 8
private val capabilityJson = Json { ignoreUnknownKeys = true }

@Serializable
enum class QuickStartNodeRole {
    @SerialName("phone")
    PHONE,

    @SerialName("watch")
    WATCH,
}

/** Payload for [QuickStartDataLayerPaths.CAPABILITY] when native publication is wired. */
@Serializable
data class QuickStartCapability(
    val capabilitySchemaVersion: Int,
    val nodeId: String,
    val role: QuickStartNodeRole,
    val supportedRequestSchemaVersions: List<Int>,
)

/** Construct a v1 advertisement for the request codec implemented today. */
fun localQuickStartCapability(nodeId: String, role: QuickStartNodeRole): QuickStartCapability =
    QuickStartCapability(
        capabilitySchemaVersion = QUICK_START_CAPABILITY_SCHEMA_VERSION,
        nodeId = nodeId,
        role = role,
        supportedRequestSchemaVersions = listOf(QUICK_START_SCHEMA_VERSION),
    )

fun encodeQuickStartCapability(capability: QuickStartCapability): String {
    require(capability.isWellFormed()) { "Invalid Quick Start capability" }
    require(capability.capabilitySchemaVersion == QUICK_START_CAPABILITY_SCHEMA_VERSION) {
        "Unsupported Quick Start capability schema"
    }
    return capabilityJson.encodeToString(capability)
}

enum class QuickStartCapabilityUnavailableReason {
    UNREACHABLE,
    MISSING_CAPABILITY,
    MALFORMED_CAPABILITY,
    UNSUPPORTED_CAPABILITY_SCHEMA,
    NODE_MISMATCH,
    ROLE_MISMATCH,
    NO_COMMON_REQUEST_SCHEMA,
    INVALID_LOCAL_CAPABILITY,
}

sealed interface QuickStartCapabilityDecision {
    data class Compatible(val nodeId: String, val requestSchemaVersion: Int) :
        QuickStartCapabilityDecision

    data class Unavailable(val reason: QuickStartCapabilityUnavailableReason) :
        QuickStartCapabilityDecision
}

/**
 * Evaluates one explicitly selected, currently reachable peer. The caller
 * obtains reachability and node identity from the Wear Data Layer; a cached
 * capability alone is never proof of a connected or current watch.
 *
 * Versions are selected by intersection, never by app version or guesswork.
 * Unknown capability envelopes fail closed. Optional fields added within the
 * v1 envelope may be ignored, but a new required meaning needs a new envelope
 * version. Both sides must advertise an implemented request codec before send.
 */
fun negotiateQuickStartCapability(
    local: QuickStartCapability,
    remotePayload: String?,
    selectedRemoteNodeId: String,
    remoteReachable: Boolean,
): QuickStartCapabilityDecision {
    fun unavailable(reason: QuickStartCapabilityUnavailableReason) =
        QuickStartCapabilityDecision.Unavailable(reason)

    if (!local.isWellFormed() ||
        local.capabilitySchemaVersion != QUICK_START_CAPABILITY_SCHEMA_VERSION
    ) {
        return unavailable(QuickStartCapabilityUnavailableReason.INVALID_LOCAL_CAPABILITY)
    }
    if (!remoteReachable) return unavailable(QuickStartCapabilityUnavailableReason.UNREACHABLE)
    if (remotePayload == null) {
        return unavailable(QuickStartCapabilityUnavailableReason.MISSING_CAPABILITY)
    }
    if (remotePayload.length > MAX_CAPABILITY_LENGTH) {
        return unavailable(QuickStartCapabilityUnavailableReason.MALFORMED_CAPABILITY)
    }
    val remote = try {
        capabilityJson.decodeFromString<QuickStartCapability>(remotePayload)
    } catch (_: SerializationException) {
        return unavailable(QuickStartCapabilityUnavailableReason.MALFORMED_CAPABILITY)
    } catch (_: IllegalArgumentException) {
        return unavailable(QuickStartCapabilityUnavailableReason.MALFORMED_CAPABILITY)
    }
    if (remote.capabilitySchemaVersion != QUICK_START_CAPABILITY_SCHEMA_VERSION) {
        return unavailable(QuickStartCapabilityUnavailableReason.UNSUPPORTED_CAPABILITY_SCHEMA)
    }
    if (!remote.isWellFormed()) {
        return unavailable(QuickStartCapabilityUnavailableReason.MALFORMED_CAPABILITY)
    }
    if (!selectedRemoteNodeId.isValidNodeId() ||
        remote.nodeId != selectedRemoteNodeId || remote.nodeId == local.nodeId
    ) {
        return unavailable(QuickStartCapabilityUnavailableReason.NODE_MISMATCH)
    }
    if (remote.role == local.role) {
        return unavailable(QuickStartCapabilityUnavailableReason.ROLE_MISMATCH)
    }
    val agreedSchema = local.supportedRequestSchemaVersions
        .intersect(remote.supportedRequestSchemaVersions.toSet())
        .maxOrNull()
        ?: return unavailable(QuickStartCapabilityUnavailableReason.NO_COMMON_REQUEST_SCHEMA)
    return QuickStartCapabilityDecision.Compatible(remote.nodeId, agreedSchema)
}

private fun QuickStartCapability.isWellFormed(): Boolean =
    nodeId.isValidNodeId() &&
        supportedRequestSchemaVersions.isNotEmpty() &&
        supportedRequestSchemaVersions.size <= MAX_ADVERTISED_SCHEMAS &&
        supportedRequestSchemaVersions.all { it > 0 } &&
        supportedRequestSchemaVersions.distinct().size == supportedRequestSchemaVersions.size

private fun String.isValidNodeId(): Boolean =
    isNotBlank() && length <= MAX_NODE_ID_LENGTH && none { it.isISOControl() }
