package app.personal.workouttracker.shared.quickstart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val QUICK_START_SCHEMA_VERSION: Int = 1
const val QUICK_START_MAX_EXERCISES: Int = 24
const val QUICK_START_TTL_MILLIS: Long = 5 * 60 * 1_000L
const val QUICK_START_CLOCK_SKEW_MILLIS: Long = 30 * 1_000L

object QuickStartDataLayerPaths {
    const val REQUEST_PREFIX = "/quick-start/request/"
    const val ACKNOWLEDGEMENT_PREFIX = "/quick-start/ack/"
    const val CANCELLATION_PREFIX = "/quick-start/cancel/"
    const val CAPABILITY = "/quick-start/capability"
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
)

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

/** Keeps a newer receipt without allowing a terminal outcome to regress. */
fun reconcileQuickStartAcknowledgement(
    current: QuickStartAcknowledgement?,
    incoming: QuickStartAcknowledgement,
): QuickStartAcknowledgement {
    if (current == null) return incoming
    if (current.requestId != incoming.requestId) return current
    if (incoming.revision < current.revision) return current
    if (incoming.revision == current.revision && incoming == current) return current
    return if (canTransitionQuickStartStatus(current.status, incoming.status)) incoming else current
}
