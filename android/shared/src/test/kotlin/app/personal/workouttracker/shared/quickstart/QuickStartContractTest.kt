package app.personal.workouttracker.shared.quickstart

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartContractTest {
    private val now = 1_800_000_000_000L

    @Test
    fun validRequestPreservesOrderAndCreatesReadyTransientPackage() {
        val request = request(
            source = QuickStartSource.LIBRARY_SELECTION,
            exercises = listOf(exercise("first"), exercise("second")),
        )

        val result = validateQuickStartRequest(request, now)

        assertTrue(result is QuickStartValidationResult.Valid)
        val sessionPackage = (result as QuickStartValidationResult.Valid).sessionPackage
        assertEquals(listOf("first", "second"), sessionPackage.request.exercises.map { it.itemId })
        assertEquals(QuickStartPackageState.READY, sessionPackage.state)
        assertEquals(now, sessionPackage.receivedAtMillis)
        assertEquals(now + QUICK_START_TTL_MILLIS, sessionPackage.expiresLocallyAtMillis)
    }

    @Test
    fun serializesProtocolNamesRatherThanKotlinEnumNames() {
        val json = Json.encodeToString(
            QuickStartAcknowledgement(
                requestId = REQUEST_ID,
                revision = 2,
                targetNodeId = "watch-node",
                status = QuickStartStatus.CANCELLED,
                reason = QuickStartRejectionReason.PENDING_REQUEST,
                watchUpdatedAtMillis = now,
            ),
        )

        assertTrue(json.contains("\"status\":\"cancelled\""))
        assertTrue(json.contains("\"reason\":\"pending_request\""))
    }

    @Test
    fun rejectsEmptyAndOversizedLists() {
        assertInvalid(request(exercises = emptyList()), QuickStartValidationCode.INVALID_EXERCISE_COUNT)
        assertInvalid(
            request(exercises = (1..25).map { exercise("item-$it") }),
            QuickStartValidationCode.INVALID_EXERCISE_COUNT,
        )
    }

    @Test
    fun singleAndTodaySourcesRequireExactlyOneExercise() {
        val exercises = listOf(exercise("first"), exercise("second"))

        assertInvalid(
            request(source = QuickStartSource.SINGLE, exercises = exercises),
            QuickStartValidationCode.INVALID_EXERCISE_COUNT,
        )
        assertInvalid(
            request(source = QuickStartSource.TODAY_ROW, exercises = exercises),
            QuickStartValidationCode.INVALID_EXERCISE_COUNT,
        )
        assertTrue(
            validateQuickStartRequest(
                request(source = QuickStartSource.LIBRARY_SELECTION, exercises = exercises),
                now,
            ) is QuickStartValidationResult.Valid,
        )
    }

    @Test
    fun rejectsWholeRequestWhenOneExerciseIsInvalid() {
        val request = request(
            source = QuickStartSource.LIBRARY_SELECTION,
            exercises = listOf(exercise("valid"), exercise("invalid").copy(sets = 0)),
        )

        val issue = assertInvalid(request, QuickStartValidationCode.INVALID_SETS)

        assertEquals(1, issue.itemIndex)
        assertEquals("sets", issue.field)
    }

    @Test
    fun rejectsDuplicateItemIdsWithoutChangingInputOrder() {
        val request = request(
            source = QuickStartSource.LIBRARY_SELECTION,
            exercises = listOf(exercise("same"), exercise("same")),
        )

        assertInvalid(request, QuickStartValidationCode.DUPLICATE_ITEM_ID)
        assertEquals(listOf("same", "same"), request.exercises.map { it.itemId })
    }

    @Test
    fun rejectsUnsupportedSchemaAndInvalidRevision() {
        assertInvalid(
            request().copy(schemaVersion = QUICK_START_SCHEMA_VERSION + 1),
            QuickStartValidationCode.UNSUPPORTED_SCHEMA,
        )
        assertInvalid(request().copy(revision = 0), QuickStartValidationCode.INVALID_REVISION)
        assertInvalid(request().copy(revision = Long.MAX_VALUE), QuickStartValidationCode.INVALID_REVISION)
        assertInvalid(
            request().copy(requestId = "1-1-1-1-1"),
            QuickStartValidationCode.INVALID_REQUEST_ID,
        )
    }

    @Test
    fun acceptsExpiryInsideClockSkewButRejectsAfterTolerance() {
        val expiredAt = request().copy(
            createdAtMillis = now - QUICK_START_TTL_MILLIS,
            expiresAtMillis = now,
        )

        assertTrue(
            validateQuickStartRequest(expiredAt, now + QUICK_START_CLOCK_SKEW_MILLIS) is
                QuickStartValidationResult.Valid,
        )
        assertInvalid(
            expiredAt,
            QuickStartValidationCode.EXPIRED,
            receivedAtMillis = now + QUICK_START_CLOCK_SKEW_MILLIS + 1,
        )
    }

    @Test
    fun rejectsOverlongTtlAndRequestTooFarInFuture() {
        assertInvalid(
            request().copy(expiresAtMillis = now + QUICK_START_TTL_MILLIS + 1),
            QuickStartValidationCode.INVALID_TIME_WINDOW,
        )
        assertInvalid(
            request().copy(
                createdAtMillis = now + QUICK_START_CLOCK_SKEW_MILLIS + 1,
                expiresAtMillis = now + QUICK_START_CLOCK_SKEW_MILLIS + 2,
            ),
            QuickStartValidationCode.INVALID_TIME_WINDOW,
        )
    }

    @Test
    fun todayRowRequiresBothDateAndStableRowIdentity() {
        assertInvalid(
            request(
                source = QuickStartSource.TODAY_ROW,
                exercises = listOf(exercise("today")),
            ),
            QuickStartValidationCode.INVALID_SOURCE_REFERENCE,
        )

        val valid = request(
            source = QuickStartSource.TODAY_ROW,
            exercises = listOf(
                exercise("today").copy(sourceDate = "2026-09-25", sourceWorkoutRowId = 42),
            ),
        )
        assertTrue(validateQuickStartRequest(valid, now) is QuickStartValidationResult.Valid)
        assertInvalid(
            valid.copy(exercises = listOf(valid.exercises.single().copy(sourceDate = "2026-99-25"))),
            QuickStartValidationCode.INVALID_SOURCE_REFERENCE,
        )
    }

    @Test
    fun validatesLoadAsFinitePositiveKgOrLbPair() {
        assertInvalid(
            request(exercises = listOf(exercise("load").copy(loadWeight = 20.0))),
            QuickStartValidationCode.INVALID_LOAD,
        )
        assertInvalid(
            request(exercises = listOf(exercise("load").copy(loadWeight = Double.NaN, loadUnit = "kg"))),
            QuickStartValidationCode.INVALID_LOAD,
        )
        assertInvalid(
            request(exercises = listOf(exercise("load").copy(loadWeight = 20.0, loadUnit = "stone"))),
            QuickStartValidationCode.INVALID_LOAD,
        )
    }

    @Test
    fun terminalStatusCannotRegressEvenAtHigherRevision() {
        val started = acknowledgement(QuickStartStatus.STARTED, revision = 2)
        val lateReady = acknowledgement(QuickStartStatus.READY, revision = 3)

        assertSame(started, reconcileQuickStartAcknowledgement(started, lateReady))
        assertTrue(QuickStartStatus.STARTED.isTerminal())
        assertFalse(canTransitionQuickStartStatus(QuickStartStatus.STARTED, QuickStartStatus.READY))
    }

    @Test
    fun readyCanAdvanceAndOlderReceiptCannotRegressIt() {
        val ready = acknowledgement(QuickStartStatus.READY, revision = 1)
        val started = acknowledgement(QuickStartStatus.STARTED, revision = 2)
        val olderReady = acknowledgement(QuickStartStatus.READY, revision = 1)

        val advanced = reconcileQuickStartAcknowledgement(ready, started)

        assertEquals(started, advanced)
        assertSame(advanced, reconcileQuickStartAcknowledgement(advanced, olderReady))
    }

    @Test
    fun rejectsClockArithmeticOverflowAndPreservesLastSafeBoundary() {
        assertInvalid(request(), QuickStartValidationCode.INVALID_TIME_WINDOW, receivedAtMillis = -1)
        assertInvalid(request(), QuickStartValidationCode.INVALID_TIME_WINDOW, receivedAtMillis = Long.MAX_VALUE)
        val lastSafeArrival = Long.MAX_VALUE - QUICK_START_TTL_MILLIS - 2 * QUICK_START_CLOCK_SKEW_MILLIS
        val boundary = request().copy(createdAtMillis = lastSafeArrival,
            expiresAtMillis = lastSafeArrival + QUICK_START_TTL_MILLIS)
        val valid = validateQuickStartRequest(boundary, lastSafeArrival) as QuickStartValidationResult.Valid
        assertEquals(Long.MAX_VALUE - 2 * QUICK_START_CLOCK_SKEW_MILLIS, valid.sessionPackage.expiresLocallyAtMillis)
        assertInvalid(boundary, QuickStartValidationCode.INVALID_TIME_WINDOW, lastSafeArrival + 1)
        assertInvalid(boundary.copy(createdAtMillis = Long.MAX_VALUE - 1, expiresAtMillis = Long.MAX_VALUE),
            QuickStartValidationCode.INVALID_TIME_WINDOW, lastSafeArrival)
    }

    @Test
    fun conflictingEqualRevisionCannotReplacePersistedDecision() {
        val ready = acknowledgement(QuickStartStatus.READY, revision = 1)
        val sameRevisionStarted = acknowledgement(QuickStartStatus.STARTED, revision = 1)
        val sameRevisionReadyWithDifferentTimestamp = ready.copy(watchUpdatedAtMillis = ready.watchUpdatedAtMillis + 1)

        assertSame(ready, reconcileQuickStartAcknowledgement(ready, sameRevisionStarted))
        assertSame(ready, reconcileQuickStartAcknowledgement(ready, sameRevisionReadyWithDifferentTimestamp))
    }

    @Test
    fun receiptForAnotherRequestCannotReplaceCurrentRequestStatus() {
        val current = acknowledgement(QuickStartStatus.READY, revision = 1)
        val unrelated = acknowledgement(QuickStartStatus.STARTED, revision = 2).copy(
            requestId = "123e4567-e89b-12d3-a456-426614174001",
        )

        assertSame(current, reconcileQuickStartAcknowledgement(current, unrelated))
    }

    @Test
    fun acknowledgementReasonIsRequiredOnlyForRejection() {
        val rejectionWithoutReason = acknowledgement(QuickStartStatus.REJECTED, revision = 1)
        val readyWithReason = acknowledgement(QuickStartStatus.READY, revision = 1).copy(
            reason = QuickStartRejectionReason.ACTIVE_SESSION,
        )
        val validRejection = rejectionWithoutReason.copy(
            reason = QuickStartRejectionReason.INVALID_PAYLOAD,
        )

        assertEquals(
            QuickStartValidationCode.INVALID_ACKNOWLEDGEMENT,
            validateQuickStartAcknowledgement(rejectionWithoutReason, REQUEST_ID, "watch-node")?.code,
        )
        assertEquals(
            QuickStartValidationCode.INVALID_ACKNOWLEDGEMENT,
            validateQuickStartAcknowledgement(readyWithReason, REQUEST_ID, "watch-node")?.code,
        )
        assertEquals(null, validateQuickStartAcknowledgement(validRejection, REQUEST_ID, "watch-node"))
    }

    private fun assertInvalid(
        request: QuickStartRequest,
        code: QuickStartValidationCode,
        receivedAtMillis: Long = now,
    ): QuickStartValidationIssue {
        val result = validateQuickStartRequest(request, receivedAtMillis)
        assertTrue("Expected invalid result, got $result", result is QuickStartValidationResult.Invalid)
        val issue = (result as QuickStartValidationResult.Invalid).issue
        assertEquals(code, issue.code)
        return issue
    }

    private fun request(
        source: QuickStartSource = QuickStartSource.SINGLE,
        exercises: List<QuickStartExercise> = listOf(exercise("item-1")),
    ) = QuickStartRequest(
        requestId = REQUEST_ID,
        createdAtMillis = now,
        expiresAtMillis = now + QUICK_START_TTL_MILLIS,
        targetNodeId = "watch-node",
        source = source,
        exercises = exercises,
    )

    private fun exercise(itemId: String) = QuickStartExercise(
        itemId = itemId,
        exerciseId = "exercise-$itemId",
        exerciseName = "Exercise $itemId",
        sets = 3,
        prescription = "8-12 reps",
        restSeconds = 60,
    )

    private fun acknowledgement(status: QuickStartStatus, revision: Long) =
        QuickStartAcknowledgement(
            requestId = REQUEST_ID,
            revision = revision,
            targetNodeId = "watch-node",
            status = status,
            watchUpdatedAtMillis = now,
        )

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
