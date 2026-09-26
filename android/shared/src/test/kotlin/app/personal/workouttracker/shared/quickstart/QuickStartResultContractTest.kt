package app.personal.workouttracker.shared.quickstart

import app.personal.workouttracker.shared.session.ExerciseOutcome
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.shared.session.WorkoutCompletionSummary
import app.personal.workouttracker.shared.session.WorkoutEndedSummary
import app.personal.workouttracker.shared.session.WorkoutProgressSnapshot
import app.personal.workouttracker.shared.session.summarizeExerciseProgress
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickStartResultContractTest {
    @Test
    fun `completed result and persisted receipt round trip with exact identities`() {
        val result = result()
        val resultPayload = encodeQuickStartResultEnvelope(QuickStartResultEnvelope(1, WATCH, result))
        val receipt = receipt(result)
        val receiptPayload = encodeQuickStartResultReceiptEnvelope(receiptEnvelope(receipt))

        assertEquals(QuickStartResultDecodeResult.Accepted(result), decodeResult(resultPayload))
        assertEquals(QuickStartResultDecodeResult.Accepted(receipt), decodeReceipt(receiptPayload))
        assertTrue(receiptPayload.contains("\"status\":\"persisted\""))
        assertEquals("/quick-start/result/$REQUEST_ID/result-1", quickStartResultPath(REQUEST_ID, "result-1"))
        assertEquals("/quick-start/result-receipt/$REQUEST_ID/result-1", quickStartResultReceiptPath(REQUEST_ID, "result-1"))
    }

    @Test
    fun `ended result supports zero and partially completed sets`() {
        for (completedSets in 0..2) {
            val result = result(ended = true, completedSets = completedSets)
            val payload = encodeQuickStartResultEnvelope(QuickStartResultEnvelope(1, WATCH, result))
            val receipt = receipt(result)

            assertEquals(QuickStartResultDecodeResult.Accepted(result), decodeResult(payload))
            assertEquals(
                QuickStartResultDecodeResult.Accepted(receipt),
                decodeReceipt(encodeQuickStartResultReceiptEnvelope(receiptEnvelope(receipt)), expected = result),
            )
        }
    }

    @Test
    fun `malformed payloads and missing envelope versions fail closed`() {
        for (payload in listOf("", "not JSON", "{}", "[]", without(resultPayload(), "schemaVersion"))) {
            assertRejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD, decodeResult(payload))
        }
        for (payload in listOf("", "not JSON", "{}", "[]", without(receiptPayload(), "schemaVersion"))) {
            assertRejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD, decodeReceipt(payload))
        }
    }

    @Test
    fun `unsupported envelope versions are rejected`() {
        assertRejected(
            QuickStartResultDecodeRejection.UNSUPPORTED_SCHEMA,
            decodeResult(Json.encodeToString(QuickStartResultEnvelope(2, WATCH, result()))),
        )
        assertRejected(
            QuickStartResultDecodeRejection.UNSUPPORTED_SCHEMA,
            decodeReceipt(Json.encodeToString(receiptEnvelope(receipt()).copy(schemaVersion = 2))),
        )
    }

    @Test
    fun `receipt status must explicitly mean persisted import`() {
        assertRejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD, decodeReceipt(without(receiptPayload(), "status")))
        for (status in listOf("accepted", "sent", "failed", "PERSISTED")) {
            assertRejected(
                QuickStartResultDecodeRejection.MALFORMED_PAYLOAD,
                decodeReceipt(receiptPayload().replace("\"persisted\"", "\"$status\"")),
            )
        }
    }

    @Test
    fun `additive optional fields are accepted at envelope and nested levels`() {
        val resultRaw = Json.parseToJsonElement(resultPayload()).jsonObject
        val resultWithExtra = JsonObject(resultRaw + mapOf(
            "futureEnvelopeField" to JsonPrimitive(true),
            "result" to JsonObject(resultRaw.getValue("result").jsonObject + ("futureResultField" to JsonPrimitive("ok"))),
        )).toString()
        val receiptRaw = Json.parseToJsonElement(receiptPayload()).jsonObject
        val receiptWithExtra = JsonObject(receiptRaw + mapOf(
            "futureEnvelopeField" to JsonPrimitive(true),
            "receipt" to JsonObject(receiptRaw.getValue("receipt").jsonObject + ("futureReceiptField" to JsonPrimitive("ok"))),
        )).toString()

        assertEquals(QuickStartResultDecodeResult.Accepted(result()), decodeResult(resultWithExtra))
        assertEquals(QuickStartResultDecodeResult.Accepted(receipt()), decodeReceipt(receiptWithExtra))
    }

    @Test
    fun `result binds observed sender advertised watch saved target and local phone`() {
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(), sender = "other-watch"))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(watch = "other-watch")))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(), expected = request().copy(targetNodeId = "other-watch")))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(), localPhone = "other-phone"))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(result().copy(phoneNodeId = "other-phone"))))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeResult(resultPayload(result().copy(phoneNodeId = WATCH)), localPhone = WATCH))
    }

    @Test
    fun `receipt binds observed phone and local watch`() {
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeReceipt(receiptPayload(), sender = "other-phone"))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeReceipt(receiptPayload(watch = "other-watch")))
        assertRejected(QuickStartResultDecodeRejection.NODE_MISMATCH, decodeReceipt(receiptPayload(), localWatch = "other-watch"))
        val impersonated = receipt().copy(phoneNodeId = "other-phone")
        assertRejected(
            QuickStartResultDecodeRejection.INVALID_RECEIPT,
            decodeReceipt(receiptPayload(impersonated), sender = "other-phone"),
        )
    }

    @Test
    fun `wire path must exactly match both identities and message kind`() {
        for (path in listOf(
            quickStartResultPath(OTHER_REQUEST_ID, "result-1"),
            quickStartResultPath(REQUEST_ID, "other-result"),
            quickStartResultPath(REQUEST_ID, "result-1") + "/",
            quickStartResultReceiptPath(REQUEST_ID, "result-1"),
        )) {
            assertRejected(QuickStartResultDecodeRejection.PATH_MISMATCH, decodeResult(resultPayload(), path = path))
        }
        for (path in listOf(
            quickStartResultReceiptPath(OTHER_REQUEST_ID, "result-1"),
            quickStartResultReceiptPath(REQUEST_ID, "other-result"),
            quickStartResultReceiptPath(REQUEST_ID, "result-1") + "/",
            quickStartResultPath(REQUEST_ID, "result-1"),
        )) {
            assertRejected(QuickStartResultDecodeRejection.PATH_MISMATCH, decodeReceipt(receiptPayload(), path = path))
        }
    }

    @Test
    fun `result rejects invalid revisions variants and mismatched session identity`() {
        val completed = result()
        val ended = result(ended = true, completedSets = 1)
        val invalid = listOf(
            completed.copy(outcomeRevision = -1),
            completed.copy(outcomeRevision = 2),
            completed.copy(summary = null),
            completed.copy(endedSummary = ended.endedSummary),
            completed.copy(summary = completed.summary!!.copy(snapshot = completed.snapshot.copy(sessionId = OTHER_REQUEST_ID))),
            ended.copy(endedSummary = ended.endedSummary!!.copy(snapshot = completed.snapshot)),
        )
        for (result in invalid) {
            assertRejected(QuickStartResultDecodeRejection.INVALID_RESULT, decodeResult(resultPayload(result)))
        }
    }

    @Test
    fun `receipt rejects wrong request result revision and invalid timestamp`() {
        for (invalid in listOf(
            receipt().copy(requestId = OTHER_REQUEST_ID),
            receipt().copy(resultId = "other-result"),
            receipt().copy(outcomeRevision = 2),
            receipt().copy(receivedAtMillis = -1),
        )) {
            assertRejected(
                QuickStartResultDecodeRejection.INVALID_RECEIPT,
                decodeReceipt(receiptPayload(invalid), path = quickStartResultReceiptPath(invalid.requestId, invalid.resultId)),
            )
        }
    }

    @Test
    fun `saved request identity title and exercise plan must match`() {
        val original = request()
        val exercise = original.exercises.single()
        for (expected in listOf(
            original.copy(requestId = OTHER_REQUEST_ID),
            original.copy(title = "Other title"),
            original.copy(exercises = listOf(exercise.copy(itemId = "other-item"))),
            original.copy(exercises = listOf(exercise.copy(exerciseId = "other-exercise"))),
            original.copy(exercises = listOf(exercise.copy(exerciseName = "Other name"))),
            original.copy(exercises = listOf(exercise.copy(sets = 4))),
        )) {
            assertRejected(QuickStartResultDecodeRejection.REQUEST_MISMATCH, decodeResult(resultPayload(), expected = expected))
        }
        assertEquals(
            QuickStartResultDecodeResult.Accepted(result()),
            decodeResult(resultPayload(), expected = original.copy(title = null)),
        )
    }

    @Test
    fun `exercise order remains part of request identity`() {
        val first = request().exercises.single()
        val second = first.copy(itemId = "second", exerciseId = "second-exercise", exerciseName = "Lunge")
        val expected = request().copy(source = QuickStartSource.LIBRARY_SELECTION, exercises = listOf(first, second))
        val secondOutcome = result().snapshot.exercises.single().copy(
            itemId = second.itemId, exerciseId = second.exerciseId, exerciseName = second.exerciseName,
        )
        val outcomes = listOf(result().snapshot.exercises.single(), secondOutcome)
        val snapshot = result().snapshot.copy(
            exercises = outcomes, progress = summarizeExerciseProgress(outcomes), completedSets = 6, plannedSets = 6,
        )
        val result = result().copy(outcomeRevision = 6, summary = result().summary!!.copy(snapshot = snapshot))

        assertEquals(QuickStartResultDecodeResult.Accepted(result), decodeResult(resultPayload(result), expected = expected))
        assertRejected(
            QuickStartResultDecodeRejection.REQUEST_MISMATCH,
            decodeResult(resultPayload(result), expected = expected.copy(exercises = expected.exercises.reversed())),
        )
    }

    @Test
    fun `result import accepts delayed offline completion after request expiry`() {
        val late = result().copy(summary = result().summary!!.copy(completedAtEpochMillis = NOW + 30L * 24 * 60 * 60 * 1_000))

        assertTrue(late.summary!!.completedAtEpochMillis > request().expiresAtMillis)
        assertEquals(QuickStartResultDecodeResult.Accepted(late), decodeResult(resultPayload(late)))
    }

    @Test
    fun `wire ids are path safe while historical stored ids remain readable`() {
        for (unsafeId in listOf("result/other", "..", "result?query", "result%2Fother", "a".repeat(129))) {
            val invalid = result().copy(resultId = unsafeId)
            assertRejected(QuickStartResultDecodeRejection.INVALID_RESULT, decodeResult(resultPayload(invalid)))
            expectInvalid { quickStartResultPath(REQUEST_ID, unsafeId) }
            expectInvalid { quickStartResultReceiptPath(REQUEST_ID, unsafeId) }
        }
        assertRejected(QuickStartResultDecodeRejection.INVALID_RESULT, decodeResult(resultPayload(result().copy(requestId = "legacy-request"))))
        val legacy = result().copy(requestId = "legacy-request", resultId = "legacy result")
        assertTrue(isValidFinalQuickStartResult(legacy))
        assertEquals(legacy, Json.decodeFromString<FinalQuickStartResult>(Json.encodeToString(legacy)))
        assertTrue(isValidQuickStartResultReceipt(receipt().copy(requestId = "legacy-request", resultId = "legacy result")))
    }

    @Test
    fun `payload size is bounded before JSON decoding`() {
        val payload = " ".repeat(QUICK_START_MAX_RESULT_PAYLOAD_CHARS + 1)

        assertRejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD, decodeResult(payload))
        assertRejected(QuickStartResultDecodeRejection.MALFORMED_PAYLOAD, decodeReceipt(payload))
    }

    @Test
    fun `wire results enforce Quick Start exercise limit within valid general snapshots`() {
        val base = result()
        val exercises = (0..QUICK_START_MAX_EXERCISES).map {
            base.snapshot.exercises.single().copy(itemId = "item-$it")
        }
        val totalSets = exercises.sumOf { it.plannedSets }
        val snapshot = base.snapshot.copy(
            exercises = exercises, progress = summarizeExerciseProgress(exercises),
            completedSets = totalSets, plannedSets = totalSets,
        )
        val oversized = base.copy(
            outcomeRevision = totalSets.toLong(), summary = base.summary!!.copy(snapshot = snapshot),
        )

        assertTrue(isValidFinalQuickStartResult(oversized))
        assertFalse(isValidQuickStartResultForWire(oversized))
        assertRejected(QuickStartResultDecodeRejection.INVALID_RESULT, decodeResult(resultPayload(oversized)))
    }

    @Test
    fun `compacted receipt replay requires the original immutable timestamp`() {
        val original = receipt()
        val accepted = decodeQuickStartResultReceipt(
            receiptPayload(), quickStartResultReceiptPath(REQUEST_ID, "result-1"), PHONE, original, WATCH,
        )
        val changed = decodeQuickStartResultReceipt(
            receiptPayload(original.copy(receivedAtMillis = original.receivedAtMillis + 1)),
            quickStartResultReceiptPath(REQUEST_ID, "result-1"), PHONE, original, WATCH,
        )

        assertEquals(QuickStartResultDecodeResult.Accepted(original), accepted)
        assertRejected(QuickStartResultDecodeRejection.INVALID_RECEIPT, changed)
    }

    @Test
    fun `encoders reject unsupported invalid or self-addressed envelopes`() {
        expectInvalid { encodeQuickStartResultEnvelope(QuickStartResultEnvelope(2, WATCH, result())) }
        expectInvalid { encodeQuickStartResultEnvelope(QuickStartResultEnvelope(1, PHONE, result())) }
        expectInvalid { encodeQuickStartResultEnvelope(QuickStartResultEnvelope(1, WATCH, result().copy(summary = null))) }
        expectInvalid { encodeQuickStartResultReceiptEnvelope(receiptEnvelope(receipt()).copy(schemaVersion = 2)) }
        expectInvalid { encodeQuickStartResultReceiptEnvelope(receiptEnvelope(receipt()).copy(watchNodeId = PHONE)) }
        expectInvalid { encodeQuickStartResultReceiptEnvelope(receiptEnvelope(receipt().copy(receivedAtMillis = -1))) }
        assertFalse(isValidFinalQuickStartResult(result().copy(summary = null)))
    }

    private fun decodeResult(
        payload: String,
        path: String = quickStartResultPath(REQUEST_ID, "result-1"),
        sender: String = WATCH,
        expected: QuickStartRequest = request(),
        localPhone: String = PHONE,
    ) = decodeQuickStartResult(payload, path, sender, expected, localPhone)

    private fun decodeReceipt(
        payload: String,
        path: String = quickStartResultReceiptPath(REQUEST_ID, "result-1"),
        sender: String = PHONE,
        expected: FinalQuickStartResult = result(),
        localWatch: String = WATCH,
    ) = decodeQuickStartResultReceipt(payload, path, sender, expected, localWatch)

    private fun resultPayload(result: FinalQuickStartResult = result(), watch: String = WATCH): String =
        Json.encodeToString(QuickStartResultEnvelope(1, watch, result))

    private fun receiptPayload(receipt: QuickStartResultReceipt = receipt(), watch: String = WATCH): String =
        Json.encodeToString(receiptEnvelope(receipt).copy(watchNodeId = watch))

    private fun receiptEnvelope(receipt: QuickStartResultReceipt) =
        QuickStartResultReceiptEnvelope(1, WATCH, QuickStartResultReceiptStatus.PERSISTED, receipt)

    private fun receipt(result: FinalQuickStartResult = result()) =
        QuickStartResultReceipt(result.requestId, result.resultId, result.outcomeRevision, PHONE, NOW + 10_000)

    private fun result(ended: Boolean = false, completedSets: Int = 3): FinalQuickStartResult {
        val exercises = listOf(ExerciseOutcome(
            itemId = "item", exerciseId = "exercise", exerciseName = "Squat",
            status = if (ended) ExerciseOutcomeStatus.PENDING else ExerciseOutcomeStatus.COMPLETED,
            completedSets = completedSets, plannedSets = 3,
        ))
        val snapshot = WorkoutProgressSnapshot(
            sessionId = REQUEST_ID, title = "Workout", progress = summarizeExerciseProgress(exercises),
            completedSets = completedSets, plannedSets = 3, elapsedActiveSeconds = 60, exercises = exercises,
        )
        return FinalQuickStartResult(
            REQUEST_ID, "result-1", completedSets.toLong(), PHONE,
            summary = if (ended) null else WorkoutCompletionSummary(completedAtEpochMillis = NOW + 6_000, snapshot = snapshot),
            endedSummary = if (ended) WorkoutEndedSummary(endedAtEpochMillis = NOW + 6_000, snapshot = snapshot) else null,
        )
    }

    private fun request() = QuickStartRequest(
        requestId = REQUEST_ID, createdAtMillis = NOW, expiresAtMillis = NOW + 5_000,
        targetNodeId = WATCH, title = "Workout", source = QuickStartSource.SINGLE,
        exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 3, "10 reps", 60)),
    )

    private fun without(payload: String, key: String): String =
        JsonObject(Json.parseToJsonElement(payload).jsonObject - key).toString()

    private fun assertRejected(reason: QuickStartResultDecodeRejection, actual: QuickStartResultDecodeResult<*>) {
        assertEquals(QuickStartResultDecodeResult.Rejected(reason), actual)
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid envelope or path")
        } catch (_: IllegalArgumentException) {
            // Expected contract rejection.
        }
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val OTHER_REQUEST_ID = "123e4567-e89b-12d3-a456-426614174001"
        const val WATCH = "watch-node"
        const val PHONE = "phone-node"
        const val NOW = 1_800_000_000_000L
    }
}
