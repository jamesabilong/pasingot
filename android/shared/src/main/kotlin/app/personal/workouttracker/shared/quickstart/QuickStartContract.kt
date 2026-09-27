package app.personal.workouttracker.shared.quickstart

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val QUICK_START_SCHEMA_VERSION: Int = 1
const val QUICK_START_MAX_EXERCISES: Int = 24
const val QUICK_START_TTL_MILLIS: Long = 5 * 60 * 1_000L
const val QUICK_START_CLOCK_SKEW_MILLIS: Long = 30 * 1_000L
const val QUICK_START_MAX_CANCELLATION_PAYLOAD_CHARS: Int = 8_192

private val cancellationJson = Json { ignoreUnknownKeys = true }

object QuickStartDataLayerPaths {
    const val REQUEST_PREFIX = "/quick-start/request/"
    const val ACKNOWLEDGEMENT_PREFIX = "/quick-start/ack/"
    const val CANCELLATION_PREFIX = "/quick-start/cancel/"
    const val CAPABILITY = "/quick-start/capability"
    const val RESULT_PREFIX = "/quick-start/result/"
    const val RESULT_RECEIPT_PREFIX = "/quick-start/result-receipt/"
}

@Serializable
enum class QuickStartSource {
    @SerialName("single")
    SINGLE,

    @SerialName("library_playlist")
    LIBRARY_PLAYLIST,

    @SerialName("library_selection")
    LIBRARY_SELECTION,

    @SerialName("today_row")
    TODAY_ROW,
}

@Serializable
enum class QuickStartStatus {
    @SerialName("ready")
    READY,

    @SerialName("started")
    STARTED,

    @SerialName("dismissed")
    DISMISSED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("rejected")
    REJECTED,

    @SerialName("expired")
    EXPIRED,
}

@Serializable
enum class QuickStartRejectionReason {
    @SerialName("active_session")
    ACTIVE_SESSION,

    @SerialName("pending_request")
    PENDING_REQUEST,

    @SerialName("invalid_payload")
    INVALID_PAYLOAD,

    @SerialName("unsupported_schema")
    UNSUPPORTED_SCHEMA,

    @SerialName("storage_error")
    STORAGE_ERROR,
}

@Serializable
enum class QuickStartPackageState {
    @SerialName("ready")
    READY,

    @SerialName("starting")
    STARTING,
}

@Serializable
data class QuickStartExercise(
    val itemId: String,
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val prescription: String,
    val restSeconds: Int,
    val loadWeight: Double? = null,
    val loadUnit: String? = null,
    val sourceDate: String? = null,
    val sourceWorkoutRowId: Long? = null,
)

@Serializable
data class QuickStartRequest(
    val requestId: String,
    val schemaVersion: Int = QUICK_START_SCHEMA_VERSION,
    val revision: Long = 1,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val targetNodeId: String,
    val title: String? = null,
    val source: QuickStartSource,
    val exercises: List<QuickStartExercise>,
)

@Serializable
data class QuickStartAcknowledgement(
    val requestId: String,
    val revision: Long,
    val targetNodeId: String,
    val status: QuickStartStatus,
    val reason: QuickStartRejectionReason? = null,
    val watchUpdatedAtMillis: Long,
)

/**
 * The only Quick Start workout definition retained by the watch. It is
 * independent from the permanent, date-keyed downloaded-workout store.
 */
@Serializable
data class WatchSessionPackage(
    val request: QuickStartRequest,
    val receivedAtMillis: Long,
    val expiresLocallyAtMillis: Long,
    val state: QuickStartPackageState = QuickStartPackageState.READY,
    /** Set from the observed Data Layer sender, never from request JSON. */
    val sourcePhoneNodeId: String? = null,
)

/** Phone -> watch request to terminate a still-READY offer. */
@Serializable
data class QuickStartCancellation(
    val requestId: String,
    val revision: Long,
    val targetNodeId: String,
    val phoneNodeId: String,
    val requestedAtMillis: Long,
)

enum class QuickStartCancellationRejection {
    MALFORMED_PAYLOAD,
    INVALID_CANCELLATION,
    NODE_MISMATCH,
    PATH_MISMATCH,
    REQUEST_MISMATCH,
}

sealed interface QuickStartCancellationDecodeResult {
    data class Accepted(val cancellation: QuickStartCancellation) : QuickStartCancellationDecodeResult
    data class Rejected(val reason: QuickStartCancellationRejection) : QuickStartCancellationDecodeResult
}

fun quickStartCancellationPath(requestId: String): String {
    require(requestId.isCanonicalRequestId()) { "Invalid cancellation request ID" }
    return QuickStartDataLayerPaths.CANCELLATION_PREFIX + requestId
}

fun encodeQuickStartCancellation(cancellation: QuickStartCancellation): String {
    require(cancellation.isWellFormed()) { "Invalid Quick Start cancellation" }
    return cancellationJson.encodeToString(cancellation).also {
        require(it.length <= QUICK_START_MAX_CANCELLATION_PAYLOAD_CHARS)
    }
}

/** Bind cancellation JSON to its observed sender, local watch and retained request. */
fun decodeQuickStartCancellation(
    payload: String,
    path: String,
    observedPhoneNodeId: String,
    localWatchNodeId: String,
    expectedRequest: QuickStartRequest,
): QuickStartCancellationDecodeResult {
    val decoded = decodeQuickStartCancellationEnvelope(
        payload, path, observedPhoneNodeId, localWatchNodeId,
    )
    val cancellation = when (decoded) {
        is QuickStartCancellationDecodeResult.Accepted -> decoded.cancellation
        is QuickStartCancellationDecodeResult.Rejected -> return decoded
    }
    if (cancellation.requestId != expectedRequest.requestId ||
        cancellation.revision != expectedRequest.revision + 1 ||
        cancellation.targetNodeId != expectedRequest.targetNodeId
    ) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.REQUEST_MISMATCH,
        )
    }
    return QuickStartCancellationDecodeResult.Accepted(cancellation)
}

/** Decode identity/path first so a retained terminal record can validate an exact retry. */
fun decodeQuickStartCancellationEnvelope(
    payload: String,
    path: String,
    observedPhoneNodeId: String,
    localWatchNodeId: String,
): QuickStartCancellationDecodeResult {
    if (payload.length > QUICK_START_MAX_CANCELLATION_PAYLOAD_CHARS) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.MALFORMED_PAYLOAD,
        )
    }
    val cancellation = try {
        cancellationJson.decodeFromString<QuickStartCancellation>(payload)
    } catch (_: SerializationException) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.MALFORMED_PAYLOAD,
        )
    } catch (_: IllegalArgumentException) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.MALFORMED_PAYLOAD,
        )
    }
    if (!cancellation.isWellFormed()) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.INVALID_CANCELLATION,
        )
    }
    if (cancellation.phoneNodeId != observedPhoneNodeId ||
        cancellation.targetNodeId != localWatchNodeId ||
        cancellation.phoneNodeId == localWatchNodeId
    ) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.NODE_MISMATCH,
        )
    }
    if (path != quickStartCancellationPath(cancellation.requestId)) {
        return QuickStartCancellationDecodeResult.Rejected(
            QuickStartCancellationRejection.PATH_MISMATCH,
        )
    }
    return QuickStartCancellationDecodeResult.Accepted(cancellation)
}

fun QuickStartStatus.isTerminal(): Boolean = when (this) {
    QuickStartStatus.READY -> false
    QuickStartStatus.STARTED,
    QuickStartStatus.DISMISSED,
    QuickStartStatus.CANCELLED,
    QuickStartStatus.REJECTED,
    QuickStartStatus.EXPIRED,
    -> true
}

fun canTransitionQuickStartStatus(from: QuickStartStatus, to: QuickStartStatus): Boolean =
    from == to || when (from) {
        QuickStartStatus.READY -> to != QuickStartStatus.READY
        QuickStartStatus.STARTED,
        QuickStartStatus.DISMISSED,
        QuickStartStatus.CANCELLED,
        QuickStartStatus.REJECTED,
        QuickStartStatus.EXPIRED,
        -> false
    }

private fun QuickStartCancellation.isWellFormed(): Boolean =
    requestId.isCanonicalRequestId() && revision > 0 && targetNodeId.isValidCancellationNodeId() &&
        phoneNodeId.isValidCancellationNodeId() && targetNodeId != phoneNodeId && requestedAtMillis >= 0

private fun String.isCanonicalRequestId(): Boolean = length <= 128 && runCatching {
    UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)

private fun String.isValidCancellationNodeId(): Boolean =
    isNotBlank() && length <= 256 && none(Char::isISOControl)

/** Keeps a newer receipt without allowing a terminal outcome to regress. */
fun reconcileQuickStartAcknowledgement(
    current: QuickStartAcknowledgement?,
    incoming: QuickStartAcknowledgement,
): QuickStartAcknowledgement {
    if (current == null) return incoming
    if (current.requestId != incoming.requestId) return current
    // A revision represents one immutable watch decision. A conflicting replay
    // at the same revision cannot replace a state the phone already persisted.
    if (incoming.revision <= current.revision) return current
    return if (canTransitionQuickStartStatus(current.status, incoming.status)) incoming else current
}
