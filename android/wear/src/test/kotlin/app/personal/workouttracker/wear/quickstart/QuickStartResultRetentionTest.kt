package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.wear.session.InitializeWorkoutOutcomeResult
import app.personal.workouttracker.wear.session.WatchWorkoutOutcomeStore
import app.personal.workouttracker.wear.session.WorkoutExerciseOutcomePlan
import app.personal.workouttracker.wear.session.WorkoutOutcomePersistence
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransition
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import app.personal.workouttracker.wear.session.toCompletionSummaryOrNull
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QuickStartResultRetentionTest {
    @Test
    fun `final result cannot change the observed offer phone owner`() = runTest {
        val fixture = fixture()
        fixture.outcomes.apply(REQUEST_ID,
            WorkoutOutcomeTransition(1, "item", WorkoutOutcomeTransitionType.SET_COMPLETED))
        assertEquals(
            SaveCompletedQuickStartResult.OutcomeMismatch,
            fixture.coordinator.saveFinal(fixture.syntheticResult().copy(phoneNodeId = "other-phone"), NOW),
        )
        assertNull(fixture.outcomes.frozenResult())
        assertNull(fixture.results.pendingResult())
    }
    @Test
    fun `transport acceptance leaves result and progress until exact phone receipt`() = runTest {
        val fixture = fixture()
        val result = fixture.finishAndSave()
        assertEquals(result, fixture.results.pendingResult())
        assertNotNull(fixture.packages.current(NOW + 1))
        assertNotNull(fixture.outcomes.current())

        val receipt = fixture.receipt()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
            fixture.coordinator.acknowledgeAndPrune(receipt, "other-phone"),
        )
        assertEquals(
            AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
            fixture.coordinator.acknowledgeAndPrune(receipt.copy(outcomeRevision = 2), PHONE_NODE),
        )
        assertEquals(
            AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
            fixture.coordinator.acknowledgeAndPrune(receipt.copy(resultId = "other"), PHONE_NODE),
        )
        assertEquals(result, fixture.results.pendingResult())
        assertNotNull(fixture.packages.current(NOW + 1))
        assertNotNull(fixture.outcomes.current())

        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE),
        )
        assertNull(fixture.results.pendingResult())
        assertNull(fixture.packages.current(NOW + 1))
        assertNull(fixture.outcomes.current())
        assertEquals(
            AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED,
            fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE),
        )
        assertTrue(fixture.packages.accept(fixture.request, NOW) is AcceptQuickStartResult.PreviouslyAcknowledged)
        assertTrue(fixture.results.save(result) is SaveQuickStartResult.AlreadyAcknowledged)
    }

    @Test
    fun `a ready offer or unfinished outcomes cannot be saved as a completed result`() = runTest {
        val fixture = fixture(start = false)
        assertEquals(
            SaveCompletedQuickStartResult.NotStarting,
            fixture.coordinator.saveCompleted(fixture.syntheticResult(), NOW),
        )
        fixture.packages.markStarting(REQUEST_ID, 1, NOW)
        assertEquals(
            SaveCompletedQuickStartResult.OutcomeMismatch,
            fixture.coordinator.saveCompleted(fixture.syntheticResult(), NOW),
        )
        assertNull(fixture.results.pendingResult())
    }

    @Test
    fun `a different package exercise plan cannot label completed outcomes`() = runTest {
        for (field in listOf("itemId", "exerciseId", "exerciseName", "sets")) {
            val fixture = fixture()
            fixture.outcomes.apply(
                REQUEST_ID,
                WorkoutOutcomeTransition(1, "item", WorkoutOutcomeTransitionType.SET_COMPLETED),
            )
            val result = fixture.syntheticResult()
            val originalExercise = fixture.request.exercises.single()
            val changedExercise = when (field) {
                "itemId" -> originalExercise.copy(itemId = "different-item")
                "exerciseId" -> originalExercise.copy(exerciseId = "different-exercise")
                "exerciseName" -> originalExercise.copy(exerciseName = "Lunge")
                else -> originalExercise.copy(sets = 2)
            }
            val changedPackage = fixture.packages.current(NOW)!!.copy(
                request = fixture.request.copy(exercises = listOf(changedExercise)),
            )
            fixture.packagePersistence.raw =
                "{\"schemaVersion\":1,\"sessionPackage\":${Json.encodeToString(changedPackage)}}"

            assertEquals(
                SaveCompletedQuickStartResult.OutcomeMismatch,
                fixture.coordinator.saveCompleted(result, NOW),
            )
            assertNull(fixture.results.pendingResult())
            assertEquals(0, fixture.resultPersistence.writes)
            assertNotNull(fixture.outcomes.current())
        }
    }

    @Test
    fun `result store survives recreation and duplicate receipts do not rewrite`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        val recreated = WatchQuickStartResultStore(fixture.resultPersistence)
        assertNotNull(recreated.pendingResult())
        val writes = fixture.resultPersistence.writes
        val existingResult = recreated.save(fixture.syntheticResult())
        assertTrue(existingResult is SaveQuickStartResult.Existing)
        assertEquals(writes, fixture.resultPersistence.writes)

        val receipt = fixture.receipt()
        assertTrue(recreated.acceptReceipt(receipt, PHONE_NODE) is AcceptQuickStartResultReceipt.Recorded)
        val afterReceipt = fixture.resultPersistence.writes
        assertTrue(
            WatchQuickStartResultStore(fixture.resultPersistence).acceptReceipt(receipt, PHONE_NODE) is
                AcceptQuickStartResultReceipt.Existing,
        )
        assertEquals(afterReceipt, fixture.resultPersistence.writes)
    }

    @Test
    fun `partial cleanup resumes after write responses are lost`() = runTest {
        for (failedStore in listOf("result", "package", "outcome")) {
            val fixture = fixture()
            fixture.finishAndSave()
            val receipt = fixture.receipt()
            val persistence = when (failedStore) {
                "result" -> fixture.resultPersistence
                "package" -> fixture.packagePersistence
                else -> fixture.outcomePersistence
            }
            persistence.afterWrite = { throw IOException("response lost") }
            expectIOException { fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE) }
            persistence.afterWrite = {}
            assertEquals(
                if (failedStore == "package") AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED
                else AcknowledgeQuickStartCompletionResult.PRUNED,
                fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE),
            )
            assertNull(fixture.results.pendingResult())
            assertNull(fixture.packages.current(NOW + 1))
            assertNull(fixture.outcomes.current())
        }
    }

    @Test
    fun `failed receipt write keeps the unsynced result available`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        fixture.resultPersistence.beforeWrite = { throw IOException("disk full") }
        expectIOException { fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE) }
        assertNotNull(fixture.results.pendingResult())
        assertNotNull(fixture.packages.current(NOW + 1))
        assertNotNull(fixture.outcomes.current())
    }

    @Test
    fun `lost response to final compaction leaves an acknowledged replay tombstone`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        val receipt = fixture.receipt()
        fixture.resultPersistence.afterWrite = {
            if (fixture.resultPersistence.writes == 3) throw IOException("response lost after compact")
        }
        expectIOException { fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE) }
        fixture.resultPersistence.afterWrite = {}
        assertNull(fixture.results.pendingResult())
        assertNotNull(fixture.packages.current(NOW + 1))
        assertNull(fixture.outcomes.current())
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(receipt, PHONE_NODE),
        )
        assertNull(fixture.packages.current(NOW + 1))
        assertTrue(fixture.packages.accept(fixture.request, NOW) is AcceptQuickStartResult.PreviouslyAcknowledged)
    }

    @Test
    fun `failed result compaction keeps the starting package until cleanup can finish`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        fixture.resultPersistence.beforeWrite = {
            if (fixture.resultPersistence.writes == 2) throw IOException("cannot compact")
        }
        expectIOException { fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE) }
        assertNotNull(fixture.results.confirmed())
        assertNull(fixture.outcomes.current())
        assertNotNull(fixture.packages.current(NOW + 1))
        val next = fixture.request.copy(requestId = "123e4567-e89b-12d3-a456-426614174002")
        assertTrue(fixture.packages.accept(next, NOW + 1) is AcceptQuickStartResult.RejectedPending)

        fixture.resultPersistence.beforeWrite = {}
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        assertTrue(fixture.packages.accept(next, NOW + 2) is AcceptQuickStartResult.Accepted)
    }

    @Test
    fun `cleanup retry preserves a newer outcome created after an interrupted removal`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        fixture.outcomePersistence.afterWrite = { throw IOException("removal response lost") }
        expectIOException { fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE) }
        fixture.outcomePersistence.afterWrite = {}
        assertNull(fixture.outcomes.current())
        val nextSession = "123e4567-e89b-12d3-a456-426614174002"
        fixture.outcomes.initialize(
            nextSession,
            "Next workout",
            listOf(WorkoutExerciseOutcomePlan("next-item", "next-exercise", "Lunge", 2)),
        )
        val nextState = fixture.outcomes.current()
        assertNotNull(nextState)

        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        assertEquals(nextState, fixture.outcomes.current())
        assertNull(fixture.packages.current(NOW + 1))
        assertNull(fixture.results.pendingResult())
    }

    @Test
    fun `release retry recognizes an acknowledged request in replay history`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        val next = fixture.request.copy(requestId = "123e4567-e89b-12d3-a456-426614174002")
        assertTrue(fixture.packages.accept(next, NOW + 1) is AcceptQuickStartResult.Accepted)
        assertTrue(
            fixture.packages.releaseAcknowledged(fixture.receipt()) is
                ReleaseAcknowledgedQuickStartResult.AlreadyReleased,
        )
        assertEquals(next, fixture.packages.current(NOW + 1)?.request)
        assertEquals(
            AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        assertEquals(next, fixture.packages.current(NOW + 1)?.request)
    }

    @Test
    fun `an acknowledged session cannot be initialized again after store recreation`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        val writes = fixture.outcomePersistence.writes
        val restored = WatchWorkoutOutcomeStore(fixture.outcomePersistence)
        assertEquals(
            InitializeWorkoutOutcomeResult.AlreadyAcknowledged(fixture.receipt()),
            restored.initialize(
                REQUEST_ID,
                "Workout",
                listOf(WorkoutExerciseOutcomePlan("item", "exercise", "Squat", 1)),
            ),
        )
        assertNull(restored.current())
        assertEquals(writes, fixture.outcomePersistence.writes)
    }

    @Test
    fun `acknowledged request remains replay safe after a later offer is dismissed`() = runTest {
        val fixture = fixture()
        fixture.finishAndSave()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        val next = fixture.request.copy(requestId = "123e4567-e89b-12d3-a456-426614174002")
        assertTrue(fixture.packages.accept(next, NOW + 1) is AcceptQuickStartResult.Accepted)
        assertTrue(fixture.packages.dismiss(next.requestId, 2, NOW + 2) is TerminateQuickStartResult.Terminated)
        val restored = WatchSessionPackageStore(fixture.packagePersistence)
        assertTrue(restored.accept(fixture.request, NOW + 3) is AcceptQuickStartResult.PreviouslyAcknowledged)
    }

    @Test
    fun `replay history refuses eviction until old request windows expire`() = runTest {
        val records = (0..63).map { index ->
            AcknowledgedQuickStartRecord("old-$index", "result-$index", 1, NOW, NOW + 330_000)
        }
        val raw = "{\"schemaVersion\":1,\"acknowledged\":${Json.encodeToString(records.last())}," +
            "\"acknowledgedHistory\":${Json.encodeToString(records.dropLast(1))}}"
        val persistence = MemoryPersistence(raw)
        val store = WatchSessionPackageStore(persistence)
        val incoming = Fixture().request
        assertEquals(AcceptQuickStartResult.ReplayHistoryFull, store.accept(incoming, NOW + 1))
        assertEquals(raw, persistence.raw)
        assertEquals(0, persistence.writes)

        val later = incoming.copy(createdAtMillis = NOW + 330_001, expiresAtMillis = NOW + 630_001)
        assertTrue(store.accept(later, NOW + 330_001) is AcceptQuickStartResult.Accepted)
    }

    @Test
    fun `future-dated request remains protected through the last clock-skew-valid replay`() = runTest {
        val fixture = fixture(createdAtMillis = NOW + 30_000)
        fixture.finishAndSave()
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(), PHONE_NODE),
        )
        assertTrue(
            WatchSessionPackageStore(fixture.packagePersistence).accept(fixture.request, NOW + 360_000) is
                AcceptQuickStartResult.PreviouslyAcknowledged,
        )
    }

    @Test
    fun `malformed or future result records are preserved and block replacement`() = runTest {
        for (raw in listOf("broken", "{\"schemaVersion\":99}")) {
            val persistence = MemoryPersistence(raw)
            val store = WatchQuickStartResultStore(persistence)
            try {
                store.pendingResult()
                fail("Expected unreadable result")
            } catch (_: IllegalStateException) {
                assertEquals(raw, persistence.raw)
                assertEquals(0, persistence.writes)
            }
        }
    }

    @Test
    fun `ready and starting recovery never launch unseen after expiry or reboot`() = runTest {
        val fixture = fixture(start = false)
        val ready = fixture.packages.current(NOW)!!
        assertEquals(QuickStartRecoveryDecision.READY, recoverQuickStartPackage(ready, NOW))
        assertEquals(
            QuickStartRecoveryDecision.EXPIRED,
            recoverQuickStartPackage(ready, ready.expiresLocallyAtMillis + 1),
        )
        assertNull(WatchSessionPackageStore(fixture.packagePersistence).current(ready.expiresLocallyAtMillis + 1))

        val started = fixture()
        val starting = WatchSessionPackageStore(started.packagePersistence).current(Long.MAX_VALUE)
        assertEquals(QuickStartRecoveryDecision.RESUME_STARTING, recoverQuickStartPackage(starting, Long.MAX_VALUE))
        assertEquals(QuickStartRecoveryDecision.NONE, recoverQuickStartPackage(null, NOW))
    }

    private suspend fun fixture(
        start: Boolean = true,
        createdAtMillis: Long = NOW,
    ): Fixture = Fixture(createdAtMillis).also { it.setup(start) }

    private class Fixture(createdAtMillis: Long = NOW) {
        val packagePersistence = MemoryPersistence()
        val outcomePersistence = MemoryPersistence()
        val resultPersistence = MemoryPersistence()
        val packages = WatchSessionPackageStore(packagePersistence)
        val outcomes = WatchWorkoutOutcomeStore(outcomePersistence)
        val results = WatchQuickStartResultStore(resultPersistence)
        val coordinator = QuickStartResultRetentionCoordinator(packages, outcomes, results)
        val request = QuickStartRequest(
            requestId = REQUEST_ID,
            createdAtMillis = createdAtMillis,
            expiresAtMillis = createdAtMillis + 300_000,
            targetNodeId = "watch-node",
            source = QuickStartSource.SINGLE,
            exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 1, "10 reps", 60)),
        )

        suspend fun setup(start: Boolean = true) {
            packages.accept(request, NOW, PHONE_NODE)
            if (start) packages.markStarting(REQUEST_ID, 1, NOW)
            outcomes.initialize(REQUEST_ID, "Workout", listOf(WorkoutExerciseOutcomePlan("item", "exercise", "Squat", 1)))
        }

        suspend fun finishAndSave(): FinalQuickStartResult {
            outcomes.apply(REQUEST_ID, WorkoutOutcomeTransition(1, "item", WorkoutOutcomeTransitionType.SET_COMPLETED))
            val result = syntheticResult()
            assertTrue(coordinator.saveCompleted(result, NOW) is SaveCompletedQuickStartResult.Stored)
            return result
        }

        suspend fun syntheticResult(): FinalQuickStartResult {
            val state = outcomes.current()!!
            val complete = state.toCompletionSummaryOrNull(NOW + 1_000, 10)
            val summary = complete ?: state.copy(
                exercises = state.exercises.map { it.copy(
                    status = app.personal.workouttracker.shared.session.ExerciseOutcomeStatus.COMPLETED,
                    completedSets = it.plannedSets,
                ) },
                lastAppliedRevision = 1,
            ).toCompletionSummaryOrNull(NOW + 1_000, 10)!!
            return FinalQuickStartResult(REQUEST_ID, RESULT_ID, 1, PHONE_NODE, summary)
        }

        fun receipt() = QuickStartResultReceipt(REQUEST_ID, RESULT_ID, 1, PHONE_NODE, NOW + 2_000)
    }

    private class MemoryPersistence(var raw: String? = null) :
        QuickStartResultPersistence, QuickStartPackagePersistence, WorkoutOutcomePersistence {
        var writes = 0
        var beforeWrite: suspend () -> Unit = {}
        var afterWrite: suspend () -> Unit = {}
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) {
            beforeWrite()
            this.raw = raw
            writes++
            afterWrite()
        }
    }

    private suspend fun expectIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected I/O failure")
        } catch (_: IOException) {
            // The caller must retry from persisted state.
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val RESULT_ID = "123e4567-e89b-12d3-a456-426614174001"
        const val PHONE_NODE = "phone-node"
    }
}
