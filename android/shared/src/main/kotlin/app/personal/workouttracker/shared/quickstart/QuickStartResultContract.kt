package app.personal.workouttracker.shared.quickstart

import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.WorkoutEndedSummary
import app.personal.workouttracker.shared.session.WorkoutProgressSnapshot
import app.personal.workouttracker.shared.session.validateWorkoutCompletionSummary
import app.personal.workouttracker.shared.session.validateWorkoutEndedSummary
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val QUICK_START_RESULT_SCHEMA_VERSION: Int = 1
const val QUICK_START_MAX_RESULT_PAYLOAD_CHARS: Int = 131_072

private val resultWireJson = Json { ignoreUnknownKeys = true }
private val resultIdPattern = Regex("[A-Za-z0-9_-]{1,128}")

/** Stored fields remain compatible with the original Wear result record. */
@Serializable
data class FinalQuickStartResult(
    val requestId: String,
    val resultId: String,
    val outcomeRevision: Long,
    val phoneNodeId: String,
    val summary: WorkoutCompletionSummary? = null,
    val endedSummary: WorkoutEndedSummary? = null,
) {
    val snapshot: WorkoutProgressSnapshot get() = summary?.snapshot ?: requireNotNull(endedSummary).snapshot
}

/** Persist once after phone import, then resend this exact receipt on every retry. */
@Serializable
data class QuickStartResultReceipt(
    val requestId: String,
    val resultId: String,
    val outcomeRevision: Long,
    val phoneNodeId: String,
    val receivedAtMillis: Long,
)

@Serializable
data class QuickStartResultEnvelope(
    val schemaVersion: Int,
    val watchNodeId: String,
    val result: FinalQuickStartResult,
)

@Serializable
enum class QuickStartResultReceiptStatus {
    @SerialName("persisted")
    PERSISTED,
}

@Serializable
data class QuickStartResultReceiptEnvelope(
    val schemaVersion: Int,
    val watchNodeId: String,
    val status: QuickStartResultReceiptStatus,
    val receipt: QuickStartResultReceipt,
)

enum class QuickStartResultDecodeRejection {
    MALFORMED_PAYLOAD,
    UNSUPPORTED_SCHEMA,
    INVALID_RESULT,
    INVALID_RECEIPT,
    NODE_MISMATCH,
    PATH_MISMATCH,
    REQUEST_MISMATCH,
}

sealed interface QuickStartResultDecodeResult<out T> {
    data class Accepted<T>(val value: T) : QuickStartResultDecodeResult<T>
    data class Rejected(val reason: QuickStartResultDecodeRejection) : QuickStartResultDecodeResult<Nothing>
}

/** Historical disk validation stays broader than the path-safe wire identity rules. */
fun isValidFinalQuickStartResult(result: FinalQuickStartResult): Boolean = with(result) {
    requestId.isBoundedText(128) && resultId.isBoundedText(128) && phoneNodeId.isBoundedText(256) &&
        outcomeRevision >= 0 && ((summary == null) != (endedSummary == null)) &&
        (summary == null || validateWorkoutCompletionSummary(summary) == null) &&
        (endedSummary == null || validateWorkoutEndedSummary(endedSummary) == null) &&
        outcomeRevision == snapshot.exercises.sumOf {
            it.completedSets.toLong() + if (it.status == ExerciseOutcomeStatus.SKIPPED) 1L else 0L
        }
}

fun isValidQuickStartResultReceipt(receipt: QuickStartResultReceipt): Boolean = with(receipt) {
    requestId.isBoundedText(128) && resultId.isBoundedText(128) && phoneNodeId.isBoundedText(256) &&
        outcomeRevision >= 0 && receivedAtMillis >= 0
}

fun receiptMatchesQuickStartResult(
    receipt: QuickStartResultReceipt,
    result: FinalQuickStartResult,
    observedPhoneNodeId: String,
): Boolean = isValidQuickStartResultReceipt(receipt) &&
    receipt.requestId == result.requestId && receipt.resultId == result.resultId &&
    receipt.outcomeRevision == result.outcomeRevision && receipt.phoneNodeId == result.phoneNodeId &&
    observedPhoneNodeId == result.phoneNodeId

fun quickStartResultPath(requestId: String, resultId: String): String =
    resultPath(QuickStartDataLayerPaths.RESULT_PREFIX, requestId, resultId)

fun quickStartResultReceiptPath(requestId: String, resultId: String): String =
    resultPath(QuickStartDataLayerPaths.RESULT_RECEIPT_PREFIX, requestId, resultId)

fun encodeQuickStartResultEnvelope(envelope: QuickStartResultEnvelope): String {
    require(envelope.schemaVersion == QUICK_START_RESULT_SCHEMA_VERSION) { "Unsupported result schema" }
    require(isValidQuickStartResultForWire(envelope.result)) { "Invalid wire result" }
    require(envelope.watchNodeId.isBoundedText(256) && envelope.watchNodeId != envelope.result.phoneNodeId) {
        "Invalid result watch node"
    }
    return resultWireJson.encodeToString(envelope).also { require(it.length <= QUICK_START_MAX_RESULT_PAYLOAD_CHARS) }
}

fun encodeQuickStartResultReceiptEnvelope(envelope: QuickStartResultReceiptEnvelope): String {
    require(envelope.schemaVersion == QUICK_START_RESULT_SCHEMA_VERSION) { "Unsupported receipt schema" }
    require(envelope.receipt.isValidWireReceipt()) { "Invalid wire receipt" }
    require(envelope.watchNodeId.isBoundedText(256) && envelope.watchNodeId != envelope.receipt.phoneNodeId) {
        "Invalid receipt watch node"
    }
    return resultWireJson.encodeToString(envelope).also { require(it.length <= QUICK_START_MAX_RESULT_PAYLOAD_CHARS) }
}

/**
 * The phone passes its saved request and the sender obtained from Data Layer.
 * Result import intentionally has no offer TTL: a workout may finish offline.
 * Acceptance validates a payload; it does not establish durable phone import.
 */
fun decodeQuickStartResult(
    payload: String,
    path: String,
    observedWatchNodeId: String,
    expectedRequest: QuickStartRequest,
    localPhoneNodeId: String,
): QuickStartResultDecodeResult<FinalQuickStartResult> {
    val envelope = decodeWire<QuickStartResultEnvelope>(payload)
        ?: return rejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD)
    if (envelope.schemaVersion != QUICK_START_RESULT_SCHEMA_VERSION) {
        return rejected(QuickStartResultDecodeRejection.UNSUPPORTED_SCHEMA)
    }
    val result = envelope.result
    if (!isValidQuickStartResultForWire(result)) return rejected(QuickStartResultDecodeRejection.INVALID_RESULT)
    if (!localPhoneNodeId.isBoundedText(256) || !envelope.watchNodeId.isBoundedText(256) ||
        envelope.watchNodeId != observedWatchNodeId || envelope.watchNodeId != expectedRequest.targetNodeId ||
        result.phoneNodeId != localPhoneNodeId || envelope.watchNodeId == localPhoneNodeId
    ) return rejected(QuickStartResultDecodeRejection.NODE_MISMATCH)
    if (path != quickStartResultPath(result.requestId, result.resultId)) {
        return rejected(QuickStartResultDecodeRejection.PATH_MISMATCH)
    }
    val snapshot = result.snapshot
    if (validateQuickStartRequest(expectedRequest, expectedRequest.createdAtMillis) !is QuickStartValidationResult.Valid ||
        result.requestId != expectedRequest.requestId ||
        (expectedRequest.title != null && snapshot.title != expectedRequest.title) ||
        snapshot.exercises.size != expectedRequest.exercises.size ||
        snapshot.exercises.zip(expectedRequest.exercises).any { (actual, planned) ->
            actual.itemId != planned.itemId || actual.exerciseId != planned.exerciseId ||
                actual.exerciseName != planned.exerciseName || actual.plannedSets != planned.sets
        }
    ) return rejected(QuickStartResultDecodeRejection.REQUEST_MISMATCH)
    return QuickStartResultDecodeResult.Accepted(result)
}

/** The watch binds the receipt to its retained result and the observed phone node. */
fun decodeQuickStartResultReceipt(
    payload: String,
    path: String,
    observedPhoneNodeId: String,
    expectedResult: FinalQuickStartResult,
    localWatchNodeId: String,
): QuickStartResultDecodeResult<QuickStartResultReceipt> {
    val decoded = decodeReceiptEnvelope(payload, path, observedPhoneNodeId, localWatchNodeId)
    val receipt = when (decoded) {
        is QuickStartResultDecodeResult.Rejected -> return decoded
        is QuickStartResultDecodeResult.Accepted -> decoded.value
    }
    if (!isValidQuickStartResultForWire(expectedResult) || !receiptMatchesQuickStartResult(receipt, expectedResult, observedPhoneNodeId)) {
        return rejected(QuickStartResultDecodeRejection.INVALID_RECEIPT)
    }
    return QuickStartResultDecodeResult.Accepted(receipt)
}

/** A compacted record permits only the original immutable receipt, including its timestamp. */
fun decodeQuickStartResultReceipt(
    payload: String,
    path: String,
    observedPhoneNodeId: String,
    expectedReceipt: QuickStartResultReceipt,
    localWatchNodeId: String,
): QuickStartResultDecodeResult<QuickStartResultReceipt> {
    val decoded = decodeReceiptEnvelope(payload, path, observedPhoneNodeId, localWatchNodeId)
    val receipt = when (decoded) {
        is QuickStartResultDecodeResult.Rejected -> return decoded
        is QuickStartResultDecodeResult.Accepted -> decoded.value
    }
    if (!expectedReceipt.isValidWireReceipt() || receipt != expectedReceipt) {
        return rejected(QuickStartResultDecodeRejection.INVALID_RECEIPT)
    }
    return QuickStartResultDecodeResult.Accepted(receipt)
}

private fun decodeReceiptEnvelope(
    payload: String,
    path: String,
    observedPhoneNodeId: String,
    localWatchNodeId: String,
): QuickStartResultDecodeResult<QuickStartResultReceipt> {
    val envelope = decodeWire<QuickStartResultReceiptEnvelope>(payload)
        ?: return rejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD)
    if (envelope.schemaVersion != QUICK_START_RESULT_SCHEMA_VERSION) {
        return rejected(QuickStartResultDecodeRejection.UNSUPPORTED_SCHEMA)
    }
    val receipt = envelope.receipt
    if (!receipt.isValidWireReceipt()) return rejected(QuickStartResultDecodeRejection.INVALID_RECEIPT)
    if (!localWatchNodeId.isBoundedText(256) || envelope.watchNodeId != localWatchNodeId ||
        receipt.phoneNodeId != observedPhoneNodeId || receipt.phoneNodeId == localWatchNodeId
    ) return rejected(QuickStartResultDecodeRejection.NODE_MISMATCH)
    if (path != quickStartResultReceiptPath(receipt.requestId, receipt.resultId)) {
        return rejected(QuickStartResultDecodeRejection.PATH_MISMATCH)
    }
    return QuickStartResultDecodeResult.Accepted(receipt)
}

private inline fun <reified T> decodeWire(payload: String): T? {
    if (payload.length > QUICK_START_MAX_RESULT_PAYLOAD_CHARS) return null
    return try {
        resultWireJson.decodeFromString<T>(payload)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun rejected(reason: QuickStartResultDecodeRejection) = QuickStartResultDecodeResult.Rejected(reason)

/** New finalizations must be deliverable even though historical disk identities stay readable. */
fun isValidQuickStartResultForWire(result: FinalQuickStartResult): Boolean = with(result) {
    isValidFinalQuickStartResult(this) && requestId.isRequestId() && resultIdPattern.matches(resultId) &&
        snapshot.sessionId == requestId && snapshot.exercises.size <= QUICK_START_MAX_EXERCISES
}

private fun QuickStartResultReceipt.isValidWireReceipt(): Boolean = isValidQuickStartResultReceipt(this) &&
    requestId.isRequestId() && resultIdPattern.matches(resultId)

private fun resultPath(prefix: String, requestId: String, resultId: String): String {
    require(requestId.isRequestId() && resultIdPattern.matches(resultId)) { "Invalid result path identity" }
    return "$prefix$requestId/$resultId"
}

private fun String.isRequestId(): Boolean = length <= 128 && runCatching {
    UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)

private fun String.isBoundedText(maxLength: Int): Boolean =
    isNotBlank() && length <= maxLength && none { it.isISOControl() }
