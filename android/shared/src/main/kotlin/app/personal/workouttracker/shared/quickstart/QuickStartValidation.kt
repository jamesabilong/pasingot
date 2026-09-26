package app.personal.workouttracker.shared.quickstart

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

private const val MAX_ID_LENGTH = 128
private const val MAX_NODE_ID_LENGTH = 256
private const val MAX_TITLE_LENGTH = 80
private const val MAX_EXERCISE_NAME_LENGTH = 120
private const val MAX_PRESCRIPTION_LENGTH = 64
private const val MAX_SETS = 99
private const val MAX_REST_SECONDS = 3_600
private const val MAX_LOAD_WEIGHT = 2_000.0

enum class QuickStartValidationCode {
    UNSUPPORTED_SCHEMA,
    INVALID_REQUEST_ID,
    INVALID_REVISION,
    INVALID_TARGET_NODE,
    INVALID_SOURCE_NODE,
    INVALID_TIME_WINDOW,
    EXPIRED,
    INVALID_TITLE,
    INVALID_EXERCISE_COUNT,
    DUPLICATE_ITEM_ID,
    INVALID_ITEM_ID,
    INVALID_EXERCISE_ID,
    INVALID_EXERCISE_NAME,
    INVALID_SETS,
    INVALID_PRESCRIPTION,
    INVALID_REST,
    INVALID_LOAD,
    INVALID_SOURCE_REFERENCE,
    INVALID_ACKNOWLEDGEMENT,
}

data class QuickStartValidationIssue(
    val code: QuickStartValidationCode,
    val itemIndex: Int? = null,
    val field: String? = null,
)

sealed interface QuickStartValidationResult {
    data class Valid(val sessionPackage: WatchSessionPackage) : QuickStartValidationResult
    data class Invalid(val issue: QuickStartValidationIssue) : QuickStartValidationResult
}

fun validateQuickStartRequest(
    request: QuickStartRequest,
    receivedAtMillis: Long,
): QuickStartValidationResult {
    fun invalid(
        code: QuickStartValidationCode,
        itemIndex: Int? = null,
        field: String? = null,
    ) = QuickStartValidationResult.Invalid(QuickStartValidationIssue(code, itemIndex, field))

    if (request.schemaVersion != QUICK_START_SCHEMA_VERSION) {
        return invalid(QuickStartValidationCode.UNSUPPORTED_SCHEMA, field = "schemaVersion")
    }
    if (!request.requestId.isUuid()) {
        return invalid(QuickStartValidationCode.INVALID_REQUEST_ID, field = "requestId")
    }
    if (request.revision <= 0) {
        return invalid(QuickStartValidationCode.INVALID_REVISION, field = "revision")
    }
    if (!request.targetNodeId.isBoundedText(MAX_NODE_ID_LENGTH)) {
        return invalid(QuickStartValidationCode.INVALID_TARGET_NODE, field = "targetNodeId")
    }
    val ttl = request.expiresAtMillis - request.createdAtMillis
    if (
        request.createdAtMillis < 0 ||
        request.expiresAtMillis < 0 ||
        ttl <= 0 ||
        ttl > QUICK_START_TTL_MILLIS ||
        request.createdAtMillis > receivedAtMillis + QUICK_START_CLOCK_SKEW_MILLIS
    ) {
        return invalid(QuickStartValidationCode.INVALID_TIME_WINDOW, field = "expiresAtMillis")
    }
    if (receivedAtMillis > request.expiresAtMillis + QUICK_START_CLOCK_SKEW_MILLIS) {
        return invalid(QuickStartValidationCode.EXPIRED, field = "expiresAtMillis")
    }
    if (request.title != null && !request.title.isBoundedText(MAX_TITLE_LENGTH)) {
        return invalid(QuickStartValidationCode.INVALID_TITLE, field = "title")
    }
    if (request.exercises.isEmpty() || request.exercises.size > QUICK_START_MAX_EXERCISES) {
        return invalid(QuickStartValidationCode.INVALID_EXERCISE_COUNT, field = "exercises")
    }
    if (
        (request.source == QuickStartSource.SINGLE || request.source == QuickStartSource.TODAY_ROW) &&
        request.exercises.size != 1
    ) {
        return invalid(QuickStartValidationCode.INVALID_EXERCISE_COUNT, field = "exercises")
    }

    val itemIds = mutableSetOf<String>()
    request.exercises.forEachIndexed { index, exercise ->
        if (!exercise.itemId.isBoundedText(MAX_ID_LENGTH)) {
            return invalid(QuickStartValidationCode.INVALID_ITEM_ID, index, "itemId")
        }
        if (!itemIds.add(exercise.itemId)) {
            return invalid(QuickStartValidationCode.DUPLICATE_ITEM_ID, index, "itemId")
        }
        if (!exercise.exerciseId.isBoundedText(MAX_ID_LENGTH)) {
            return invalid(QuickStartValidationCode.INVALID_EXERCISE_ID, index, "exerciseId")
        }
        if (!exercise.exerciseName.isBoundedText(MAX_EXERCISE_NAME_LENGTH)) {
            return invalid(QuickStartValidationCode.INVALID_EXERCISE_NAME, index, "exerciseName")
        }
        if (exercise.sets !in 1..MAX_SETS) {
            return invalid(QuickStartValidationCode.INVALID_SETS, index, "sets")
        }
        if (!exercise.prescription.isBoundedText(MAX_PRESCRIPTION_LENGTH)) {
            return invalid(QuickStartValidationCode.INVALID_PRESCRIPTION, index, "prescription")
        }
        if (exercise.restSeconds !in 0..MAX_REST_SECONDS) {
            return invalid(QuickStartValidationCode.INVALID_REST, index, "restSeconds")
        }
        if (!exercise.hasValidLoad()) {
            return invalid(QuickStartValidationCode.INVALID_LOAD, index, "loadWeight")
        }
        if (
            (exercise.sourceDate == null) != (exercise.sourceWorkoutRowId == null) ||
            (exercise.sourceDate != null && !exercise.sourceDate.isIsoDate()) ||
            (exercise.sourceWorkoutRowId != null && exercise.sourceWorkoutRowId <= 0)
        ) {
            return invalid(QuickStartValidationCode.INVALID_SOURCE_REFERENCE, index, "sourceDate")
        }
        if (request.source == QuickStartSource.TODAY_ROW && exercise.sourceWorkoutRowId == null) {
            return invalid(QuickStartValidationCode.INVALID_SOURCE_REFERENCE, index, "sourceWorkoutRowId")
        }
    }

    return QuickStartValidationResult.Valid(
        WatchSessionPackage(
            request = request,
            receivedAtMillis = receivedAtMillis,
            expiresLocallyAtMillis = receivedAtMillis + ttl,
        ),
    )
}

fun validateQuickStartAcknowledgement(
    acknowledgement: QuickStartAcknowledgement,
    expectedRequestId: String,
    expectedTargetNodeId: String,
): QuickStartValidationIssue? {
    if (
        acknowledgement.requestId != expectedRequestId ||
        acknowledgement.targetNodeId != expectedTargetNodeId ||
        !acknowledgement.requestId.isUuid() ||
        acknowledgement.revision <= 0 ||
        acknowledgement.watchUpdatedAtMillis < 0
    ) {
        return QuickStartValidationIssue(QuickStartValidationCode.INVALID_ACKNOWLEDGEMENT)
    }
    val reasonIsValid = when (acknowledgement.status) {
        QuickStartStatus.REJECTED -> acknowledgement.reason != null
        else -> acknowledgement.reason == null
    }
    return if (reasonIsValid) null else {
        QuickStartValidationIssue(QuickStartValidationCode.INVALID_ACKNOWLEDGEMENT, field = "reason")
    }
}

private fun String.isUuid(): Boolean =
    length <= MAX_ID_LENGTH && runCatching {
        UUID.fromString(this).toString().equals(this, ignoreCase = true)
    }.getOrDefault(false)

private fun String.isBoundedText(maxLength: Int): Boolean =
    isNotBlank() && length <= maxLength && none { it.isISOControl() }

private fun QuickStartExercise.hasValidLoad(): Boolean = when {
    loadWeight == null -> loadUnit == null
    !loadWeight.isFinite() || loadWeight <= 0 || loadWeight > MAX_LOAD_WEIGHT -> false
    loadUnit != "kg" && loadUnit != "lb" -> false
    else -> true
}

private fun String.isIsoDate(): Boolean = try {
    LocalDate.parse(this)
    length == 10
} catch (_: DateTimeParseException) {
    false
}
