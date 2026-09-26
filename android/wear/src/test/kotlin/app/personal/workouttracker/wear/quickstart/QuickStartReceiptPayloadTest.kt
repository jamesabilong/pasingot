package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptStatus
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.encodeQuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.quickStartResultReceiptPath
import app.personal.workouttracker.shared.session.WorkoutEndedSummary
import app.personal.workouttracker.wear.session.WatchWorkoutOutcomeStore
import app.personal.workouttracker.wear.session.WorkoutExerciseOutcomePlan
import app.personal.workouttracker.wear.session.WorkoutOutcomePersistence
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransition
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import app.personal.workouttracker.wear.session.toCompletionSummaryOrNull
import app.personal.workouttracker.wear.session.toProgressSnapshot
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickStartReceiptPayloadTest {
    @Test
    fun `shared encoded receipts persist and prune completed and ended results idempotently`() = runTest {
        for (completed in listOf(false, true)) {
            val fixture = fixture(completed)
            assertTrue(fixture.coordinator.saveFinal(fixture.result(), NOW) is SaveCompletedQuickStartResult.Stored)
            val payload = fixture.payload()
            assertTrue(
                fixture.results.acceptReceiptPayload(payload, fixture.path, PHONE_NODE, WATCH_NODE) is
                    AcceptQuickStartResultReceipt.Recorded,
            )
            assertEquals(fixture.receipt(), fixture.results.storedReceipt())
            assertNotNull(fixture.outcomes.current())
            assertNotNull(fixture.packages.current(NOW))
            assertEquals(
                AcknowledgeQuickStartCompletionResult.PRUNED,
                fixture.coordinator.acknowledgePayloadAndPrune(payload, fixture.path, PHONE_NODE, WATCH_NODE),
            )
            assertNull(fixture.results.pendingResult())
            assertNull(fixture.outcomes.current())
            assertNull(fixture.packages.current(NOW))
            val writes = fixture.writes()
            assertEquals(
                AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED,
                fixture.coordinator.acknowledgePayloadAndPrune(payload, fixture.path, PHONE_NODE, WATCH_NODE),
            )
            assertEquals(writes, fixture.writes())
        }
    }

    @Test
    fun `malformed unsupported misrouted and mismatched receipts never write or prune`() = runTest {
        val fixture = fixture(completed = false)
        val result = fixture.result()
        fixture.coordinator.saveFinal(result, NOW)
        val payload = fixture.payload()
        val envelope = Json.parseToJsonElement(payload).jsonObject
        val failures = listOf(
            Incoming("not json"),
            Incoming(JsonObject(envelope + ("schemaVersion" to JsonPrimitive(99))).toString()),
            Incoming(JsonObject(envelope.filterKeys { it != "schemaVersion" }).toString()),
            Incoming(JsonObject(envelope + ("status" to JsonPrimitive("transport_accepted"))).toString()),
            Incoming(payload, path = fixture.path + "/extra"),
            Incoming(payload, observedPhoneNodeId = "other-phone"),
            Incoming(payload, localWatchNodeId = "other-watch"),
            Incoming(fixture.payload(watchNodeId = "other-watch")),
            Incoming(fixture.payload(receipt = fixture.receipt().copy(phoneNodeId = "other-phone"))),
            Incoming(fixture.payload(receipt = fixture.receipt().copy(outcomeRevision = 99))),
            Incoming(fixture.payload(receipt = fixture.receipt().copy(resultId = "different-result"))),
        )
        val writes = fixture.writes()
        val outcomeRaw = fixture.outcomePersistence.raw
        val packageRaw = fixture.packagePersistence.raw
        val resultRaw = fixture.resultPersistence.raw
        for (incoming in failures) {
            assertEquals(
                AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
                fixture.coordinator.acknowledgePayloadAndPrune(
                    incoming.payload, incoming.path ?: fixture.path,
                    incoming.observedPhoneNodeId, incoming.localWatchNodeId,
                ),
            )
            assertEquals(writes, fixture.writes())
            assertEquals(outcomeRaw, fixture.outcomePersistence.raw)
            assertEquals(packageRaw, fixture.packagePersistence.raw)
            assertEquals(resultRaw, fixture.resultPersistence.raw)
            assertEquals(result, fixture.results.pendingResult())
        }
    }

    @Test
    fun `receipt replay cannot change import time before or after result compaction`() = runTest {
        val fixture = fixture(completed = true)
        fixture.coordinator.saveFinal(fixture.result(), NOW)
        val original = fixture.payload()
        assertTrue(
            fixture.results.acceptReceiptPayload(original, fixture.path, PHONE_NODE, WATCH_NODE) is
                AcceptQuickStartResultReceipt.Recorded,
        )
        val changed = fixture.payload(receipt = fixture.receipt().copy(receivedAtMillis = NOW + 3_000))
        val beforePruning = fixture.writes()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
            fixture.coordinator.acknowledgePayloadAndPrune(changed, fixture.path, PHONE_NODE, WATCH_NODE),
        )
        assertEquals(beforePruning, fixture.writes())
        assertNotNull(fixture.outcomes.current())
        assertNotNull(fixture.packages.current(NOW))
        assertEquals(fixture.receipt(), fixture.results.storedReceipt())

        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgePayloadAndPrune(original, fixture.path, PHONE_NODE, WATCH_NODE),
        )
        val afterPruning = fixture.writes()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
            fixture.coordinator.acknowledgePayloadAndPrune(changed, fixture.path, PHONE_NODE, WATCH_NODE),
        )
        assertEquals(afterPruning, fixture.writes())
        assertEquals(fixture.receipt(), fixture.results.storedReceipt())
    }

    @Test
    fun `encoded receipt survives interrupted release and resumes cleanup offline`() = runTest {
        val fixture = fixture(completed = false)
        fixture.coordinator.saveFinal(fixture.result(), NOW)
        fixture.packagePersistence.beforeWrite = { throw IOException("cannot release package") }
        try {
            fixture.coordinator.acknowledgePayloadAndPrune(fixture.payload(), fixture.path, PHONE_NODE, WATCH_NODE)
            fail("Expected package release failure")
        } catch (_: IOException) {
            // Receipt and completed cleanup steps remain durable.
        }
        fixture.packagePersistence.beforeWrite = {}
        assertEquals(fixture.receipt(), fixture.results.storedReceipt())
        assertNull(fixture.results.confirmed())
        assertNull(fixture.outcomes.current())
        assertNotNull(fixture.packages.current(NOW))
        val restored = QuickStartResultRetentionCoordinator(
            WatchSessionPackageStore(fixture.packagePersistence),
            WatchWorkoutOutcomeStore(fixture.outcomePersistence),
            WatchQuickStartResultStore(fixture.resultPersistence),
        )
        assertEquals(AcknowledgeQuickStartCompletionResult.PRUNED, restored.resumeAcknowledgedCleanup())
        assertNull(fixture.packages.current(NOW))
        assertEquals(AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED, restored.resumeAcknowledgedCleanup())
    }

    @Test
    fun `an explicit package title must match before final metadata freezes`() = runTest {
        val fixture = fixture(completed = false, outcomeTitle = "Different workout")
        val writes = fixture.writes()
        assertEquals(
            SaveCompletedQuickStartResult.OutcomeMismatch,
            fixture.coordinator.saveFinal(fixture.result(), NOW),
        )
        assertEquals(writes, fixture.writes())
        assertNull(fixture.outcomes.frozenResult())
        assertNull(fixture.results.pendingResult())
        assertNotNull(fixture.outcomes.current())
    }

    @Test
    fun `new finalization rejects unsafe result identities and a phone equal to the target watch`() = runTest {
        val fixture = fixture(completed = false)
        val result = fixture.result()
        val writes = fixture.writes()
        try {
            fixture.coordinator.saveFinal(result.copy(resultId = "contains space"), NOW)
            fail("Expected invalid wire identity")
        } catch (_: IllegalArgumentException) {
            // Unsafe path identity must never become durable final metadata.
        }
        assertEquals(writes, fixture.writes())
        assertNull(fixture.outcomes.frozenResult())
        assertNull(fixture.results.pendingResult())
        assertEquals(
            SaveCompletedQuickStartResult.OutcomeMismatch,
            fixture.coordinator.saveFinal(result.copy(phoneNodeId = WATCH_NODE), NOW),
        )
        assertEquals(writes, fixture.writes())
        assertNull(fixture.outcomes.frozenResult())
        assertNull(fixture.results.pendingResult())
        assertNotNull(fixture.outcomes.current())
    }

    private suspend fun fixture(completed: Boolean, outcomeTitle: String = TITLE) = Fixture(completed).also { fixture ->
        fixture.packages.accept(fixture.request, NOW)
        fixture.packages.markStarting(REQUEST_ID, 1, NOW)
        fixture.outcomes.initialize(
            REQUEST_ID, outcomeTitle, listOf(WorkoutExerciseOutcomePlan("item", "exercise", "Squat", 1)),
        )
        if (completed) fixture.outcomes.apply(
            REQUEST_ID, WorkoutOutcomeTransition(1, "item", WorkoutOutcomeTransitionType.SET_COMPLETED),
        )
    }

    private class Fixture(private val completed: Boolean) {
        val packagePersistence = MemoryPersistence()
        val outcomePersistence = MemoryPersistence()
        val resultPersistence = MemoryPersistence()
        val packages = WatchSessionPackageStore(packagePersistence)
        val outcomes = WatchWorkoutOutcomeStore(outcomePersistence)
        val results = WatchQuickStartResultStore(resultPersistence)
        val coordinator = QuickStartResultRetentionCoordinator(packages, outcomes, results)
        val path = quickStartResultReceiptPath(REQUEST_ID, RESULT_ID)
        val request = QuickStartRequest(
            requestId = REQUEST_ID, createdAtMillis = NOW, expiresAtMillis = NOW + 300_000,
            targetNodeId = WATCH_NODE, title = TITLE, source = QuickStartSource.SINGLE,
            exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 1, "10 reps", 60)),
        )

        suspend fun result(): FinalQuickStartResult {
            val state = outcomes.current()!!
            return if (completed) {
                FinalQuickStartResult(
                    REQUEST_ID, RESULT_ID, state.lastAppliedRevision, PHONE_NODE,
                    summary = state.toCompletionSummaryOrNull(NOW + 1_000, 32)!!,
                )
            } else {
                FinalQuickStartResult(
                    REQUEST_ID, RESULT_ID, state.lastAppliedRevision, PHONE_NODE,
                    endedSummary = WorkoutEndedSummary(
                        endedAtEpochMillis = NOW + 1_000, snapshot = state.toProgressSnapshot(32),
                    ),
                )
            }
        }

        fun receipt() = QuickStartResultReceipt(REQUEST_ID, RESULT_ID, if (completed) 1L else 0L, PHONE_NODE, NOW + 2_000)

        fun payload(receipt: QuickStartResultReceipt = receipt(), watchNodeId: String = WATCH_NODE) =
            encodeQuickStartResultReceiptEnvelope(QuickStartResultReceiptEnvelope(
                schemaVersion = 1, watchNodeId = watchNodeId,
                status = QuickStartResultReceiptStatus.PERSISTED, receipt = receipt,
            ))

        fun writes() = listOf(packagePersistence.writes, outcomePersistence.writes, resultPersistence.writes)
    }

    private data class Incoming(
        val payload: String,
        val path: String? = null,
        val observedPhoneNodeId: String = PHONE_NODE,
        val localWatchNodeId: String = WATCH_NODE,
    )

    private class MemoryPersistence(var raw: String? = null) :
        QuickStartPackagePersistence, WorkoutOutcomePersistence, QuickStartResultPersistence {
        var writes = 0
        var beforeWrite: suspend () -> Unit = {}
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) {
            beforeWrite()
            this.raw = raw
            writes++
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val RESULT_ID = "123e4567-e89b-12d3-a456-426614174001"
        const val PHONE_NODE = "phone-node"
        const val WATCH_NODE = "watch-node"
        const val TITLE = "Named workout"
    }
}
