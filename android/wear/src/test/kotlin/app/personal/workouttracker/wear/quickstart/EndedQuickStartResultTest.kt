package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.session.WorkoutEndedSummary
import app.personal.workouttracker.wear.session.ApplyWorkoutOutcomeResult
import app.personal.workouttracker.wear.session.FreezeWorkoutOutcomeResult
import app.personal.workouttracker.wear.session.WatchWorkoutOutcomeStore
import app.personal.workouttracker.wear.session.WorkoutExerciseOutcomePlan
import app.personal.workouttracker.wear.session.WorkoutOutcomePersistence
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransition
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionResultCode
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import app.personal.workouttracker.wear.session.toCompletionSummaryOrNull
import app.personal.workouttracker.wear.session.toProgressSnapshot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EndedQuickStartResultTest {
    @Test
    fun `zero and partial set endings remain frozen offline until an exact phone receipt`() = runTest {
        for (completedSets in listOf(0, 1)) {
            val fixture = fixture(completedSets)
            val result = fixture.endedResult()
            assertTrue(fixture.coordinator.saveFinal(result, NOW) is SaveCompletedQuickStartResult.Stored)
            val restoredOutcomes = WatchWorkoutOutcomeStore(fixture.outcomePersistence)
            val restoredResults = WatchQuickStartResultStore(fixture.resultPersistence)
            val restoredPackages = WatchSessionPackageStore(fixture.packagePersistence)
            val restoredCoordinator = QuickStartResultRetentionCoordinator(restoredPackages, restoredOutcomes, restoredResults)
            assertEquals(result, restoredResults.pendingResult())
            assertEquals(result, restoredOutcomes.frozenResult())
            assertEquals(completedSets, result.snapshot.completedSets)
            assertEquals(1, result.snapshot.progress.pending)
            assertEquals(
                ApplyWorkoutOutcomeResult.Finalized(result),
                restoredOutcomes.apply(REQUEST_ID, set(completedSets + 1L)),
            )
            assertEquals(
                AcknowledgeQuickStartCompletionResult.RECEIPT_MISMATCH,
                restoredCoordinator.acknowledgeAndPrune(fixture.receipt(result), "other-phone"),
            )
            assertEquals(result, restoredResults.pendingResult())
            assertEquals(
                AcknowledgeQuickStartCompletionResult.PRUNED,
                restoredCoordinator.acknowledgeAndPrune(fixture.receipt(result), PHONE_NODE),
            )
            assertNull(restoredResults.pendingResult())
            assertNull(restoredOutcomes.current())
            assertNull(restoredOutcomes.frozenResult())
            assertNull(restoredPackages.current(NOW))
            assertEquals(
                AcknowledgeQuickStartCompletionResult.ALREADY_PRUNED,
                restoredCoordinator.acknowledgeAndPrune(fixture.receipt(result), PHONE_NODE),
            )
        }
    }

    @Test
    fun `freeze failures recover only committed terminal metadata`() = runTest {
        for (afterCommit in listOf(false, true)) {
            val fixture = fixture(1)
            val result = fixture.endedResult()
            if (afterCommit) fixture.outcomePersistence.afterWrite = { throw IOException("lost freeze response") }
            else fixture.outcomePersistence.beforeWrite = { throw IOException("freeze rejected") }
            expectFailure<IOException> { fixture.coordinator.saveFinal(result, NOW) }
            fixture.outcomePersistence.beforeWrite = {}
            fixture.outcomePersistence.afterWrite = {}
            assertNull(fixture.results.pendingResult())
            if (afterCommit) {
                assertEquals(result, WatchWorkoutOutcomeStore(fixture.outcomePersistence).frozenResult())
                assertTrue(fixture.coordinator.resumeFinalization(NOW + 100_000) is SaveCompletedQuickStartResult.Stored)
            } else {
                assertNull(fixture.outcomes.frozenResult())
                assertNull(fixture.coordinator.resumeFinalization(NOW + 100_000))
                assertTrue(fixture.coordinator.saveFinal(result, NOW) is SaveCompletedQuickStartResult.Stored)
            }
            assertEquals(result, fixture.results.pendingResult())
        }
    }

    @Test
    fun `result enqueue failures resume using the exact frozen IDs and time`() = runTest {
        for (afterCommit in listOf(false, true)) {
            val fixture = fixture(1)
            val result = fixture.endedResult()
            if (afterCommit) fixture.resultPersistence.afterWrite = { throw IOException("lost enqueue response") }
            else fixture.resultPersistence.beforeWrite = { throw IOException("enqueue rejected") }
            expectFailure<IOException> { fixture.coordinator.saveFinal(result, NOW) }
            fixture.resultPersistence.beforeWrite = {}
            fixture.resultPersistence.afterWrite = {}
            assertEquals(result, fixture.outcomes.frozenResult())
            val restored = QuickStartResultRetentionCoordinator(
                WatchSessionPackageStore(fixture.packagePersistence),
                WatchWorkoutOutcomeStore(fixture.outcomePersistence),
                WatchQuickStartResultStore(fixture.resultPersistence),
            )
            assertTrue(restored.resumeFinalization(NOW + 100_000) is SaveCompletedQuickStartResult.Stored)
            assertEquals(result, fixture.results.pendingResult())
            assertEquals(result.endedSummary, fixture.results.pendingResult()!!.endedSummary)
            assertEquals(1, fixture.resultPersistence.writes)
        }
    }

    @Test
    fun `concurrent set and freeze either reject a stale snapshot or block the set`() = runTest {
        for (freezeFirst in listOf(false, true)) {
            val fixture = fixture()
            val result = fixture.endedResult()
            val writing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.outcomePersistence.beforeWrite = { writing.complete(Unit); release.await() }
            if (freezeFirst) {
                val frozen = async { fixture.outcomes.freeze(result) }
                writing.await()
                val applied = async { WatchWorkoutOutcomeStore(fixture.outcomePersistence).apply(REQUEST_ID, set(1)) }
                runCurrent()
                assertFalse(applied.isCompleted)
                release.complete(Unit)
                assertEquals(FreezeWorkoutOutcomeResult.Frozen(result), frozen.await())
                assertEquals(ApplyWorkoutOutcomeResult.Finalized(result), applied.await())
                assertEquals(0L, fixture.outcomes.current()!!.lastAppliedRevision)
            } else {
                val applied = async { fixture.outcomes.apply(REQUEST_ID, set(1)) }
                writing.await()
                val frozen = async { WatchWorkoutOutcomeStore(fixture.outcomePersistence).freeze(result) }
                runCurrent()
                assertFalse(frozen.isCompleted)
                release.complete(Unit)
                assertEquals(
                    WorkoutOutcomeTransitionResultCode.APPLIED,
                    (applied.await() as ApplyWorkoutOutcomeResult.Reduced).result.code,
                )
                assertEquals(FreezeWorkoutOutcomeResult.OutcomeMismatch, frozen.await())
                assertNull(fixture.outcomes.frozenResult())
                assertEquals(1L, fixture.outcomes.current()!!.lastAppliedRevision)
            }
        }
    }

    @Test
    fun `retries cannot replace frozen final identity timestamps or duration`() = runTest {
        val fixture = fixture(1)
        val result = fixture.endedResult()
        assertEquals(FreezeWorkoutOutcomeResult.Frozen(result), fixture.outcomes.freeze(result))
        val changed = listOf(
            result.copy(resultId = "different-result"),
            result.copy(phoneNodeId = "different-phone"),
            result.copy(endedSummary = result.endedSummary!!.copy(endedAtEpochMillis = NOW + 9_000)),
            result.copy(endedSummary = result.endedSummary!!.copy(
                snapshot = result.snapshot.copy(elapsedActiveSeconds = 999),
            )),
        )
        val writes = fixture.outcomePersistence.writes to fixture.resultPersistence.writes
        for (replacement in changed) {
            assertEquals(
                SaveCompletedQuickStartResult.FinalizedConflict(result),
                fixture.coordinator.saveFinal(replacement, NOW),
            )
        }
        assertEquals(result, fixture.outcomes.frozenResult())
        assertNull(fixture.results.pendingResult())
        assertEquals(writes, fixture.outcomePersistence.writes to fixture.resultPersistence.writes)
        assertTrue(fixture.coordinator.resumeFinalization(NOW) is SaveCompletedQuickStartResult.Stored)
        assertEquals(result, fixture.results.pendingResult())
    }

    @Test
    fun `both and neither terminal summary variants are rejected without writes`() = runTest {
        val fixture = fixture()
        val ended = fixture.endedResult()
        repeat(3) { fixture.outcomes.apply(REQUEST_ID, set(it + 1L)) }
        val completed = fixture.outcomes.current()!!.toCompletionSummaryOrNull(NOW + 1_000, 60)!!
        for (invalid in listOf(ended.copy(endedSummary = null), ended.copy(summary = completed))) {
            expectFailure<IllegalArgumentException> { fixture.results.save(invalid) }
            expectFailure<IllegalArgumentException> { fixture.outcomes.freeze(invalid) }
        }
        assertEquals(0, fixture.resultPersistence.writes)
        assertNull(fixture.outcomes.frozenResult())
    }

    @Test
    fun `legacy completed JSON remains readable and freezing upgrades the outcome envelope`() = runTest {
        val fixture = fixture(3)
        val state = fixture.outcomes.current()!!
        val completed = state.toCompletionSummaryOrNull(NOW + 1_000, 60)!!
        val result = FinalQuickStartResult(REQUEST_ID, RESULT_ID, 3, PHONE_NODE, completed)
        fixture.outcomePersistence.raw = "{\"schemaVersion\":1,\"state\":${Json.encodeToString(state)}}"
        fixture.resultPersistence.raw = "{\"schemaVersion\":1,\"finalResult\":${Json.encodeToString(result)}}"
        assertFalse(fixture.outcomePersistence.raw!!.contains("frozenResult"))
        assertFalse(fixture.resultPersistence.raw!!.contains("endedSummary"))
        assertEquals(state, fixture.outcomes.current())
        assertNull(fixture.outcomes.frozenResult())
        assertEquals(result, fixture.results.pendingResult())
        assertNull(fixture.coordinator.resumeFinalization(NOW))
        assertEquals(
            SaveCompletedQuickStartResult.Stored(SaveQuickStartResult.Existing(result)),
            fixture.coordinator.saveCompleted(result, NOW),
        )
        assertTrue(fixture.outcomePersistence.raw!!.contains("\"schemaVersion\":2"))
        assertEquals(result, WatchWorkoutOutcomeStore(fixture.outcomePersistence).frozenResult())
    }

    @Test
    fun `a saved legacy completion rejects regenerated identity before freezing outcomes`() = runTest {
        val fixture = fixture(3)
        val state = fixture.outcomes.current()!!
        val result = FinalQuickStartResult(
            REQUEST_ID, RESULT_ID, 3, PHONE_NODE, state.toCompletionSummaryOrNull(NOW + 1_000, 60)!!,
        )
        fixture.outcomePersistence.raw = "{\"schemaVersion\":1,\"state\":${Json.encodeToString(state)}}"
        fixture.resultPersistence.raw = "{\"schemaVersion\":1,\"finalResult\":${Json.encodeToString(result)}}"
        val writes = fixture.outcomePersistence.writes to fixture.resultPersistence.writes
        assertEquals(
            SaveCompletedQuickStartResult.Stored(SaveQuickStartResult.Conflict(result)),
            fixture.coordinator.saveFinal(result.copy(resultId = "regenerated-result"), NOW),
        )
        assertNull(fixture.outcomes.frozenResult())
        assertEquals(result, fixture.results.pendingResult())
        assertEquals(writes, fixture.outcomePersistence.writes to fixture.resultPersistence.writes)
        assertEquals(
            AcknowledgeQuickStartCompletionResult.PRUNED,
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(result), PHONE_NODE),
        )
        assertNull(fixture.outcomes.current())
        assertNull(fixture.packages.current(NOW))
    }

    @Test
    fun `cleanup resumes from its persisted receipt without a phone reconnect`() = runTest {
        val fixture = fixture(1)
        val result = fixture.endedResult()
        fixture.coordinator.saveFinal(result, NOW)
        fixture.packagePersistence.beforeWrite = { throw IOException("package release failed") }
        expectFailure<IOException> {
            fixture.coordinator.acknowledgeAndPrune(fixture.receipt(result), PHONE_NODE)
        }
        fixture.packagePersistence.beforeWrite = {}
        assertNull(fixture.results.confirmed())
        assertNull(fixture.results.pendingResult())
        assertNull(fixture.outcomes.current())
        assertEquals(fixture.request, fixture.packages.current(NOW)!!.request)
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
    fun `tampered frozen metadata blocks all progress operations without erasing bytes`() = runTest {
        val fixture = fixture(1)
        val result = fixture.endedResult()
        fixture.outcomes.freeze(result)
        val raw = fixture.outcomePersistence.raw!!.replace(
            "\"endedAtEpochMillis\":${NOW + 1_000}", "\"endedAtEpochMillis\":-1",
        )
        assertTrue(raw.contains("\"endedAtEpochMillis\":-1"))
        fixture.outcomePersistence.raw = raw
        val writes = fixture.outcomePersistence.writes
        expectFailure<IllegalStateException> { fixture.outcomes.current() }
        expectFailure<IllegalStateException> { fixture.outcomes.frozenResult() }
        expectFailure<IllegalStateException> { fixture.outcomes.apply(REQUEST_ID, set(2)) }
        expectFailure<IllegalStateException> { fixture.outcomes.freeze(result) }
        assertEquals(raw, fixture.outcomePersistence.raw)
        assertEquals(writes, fixture.outcomePersistence.writes)
    }

    private suspend fun fixture(completedSets: Int = 0) = Fixture().also { fixture ->
        fixture.packages.accept(fixture.request, NOW)
        fixture.packages.markStarting(REQUEST_ID, 1, NOW)
        fixture.outcomes.initialize(REQUEST_ID, "Workout", listOf(WorkoutExerciseOutcomePlan("item", "exercise", "Squat", 3)))
        repeat(completedSets) { fixture.outcomes.apply(REQUEST_ID, set(it + 1L)) }
    }

    private class Fixture {
        val packagePersistence = MemoryPersistence()
        val outcomePersistence = MemoryPersistence()
        val resultPersistence = MemoryPersistence()
        val packages = WatchSessionPackageStore(packagePersistence)
        val outcomes = WatchWorkoutOutcomeStore(outcomePersistence)
        val results = WatchQuickStartResultStore(resultPersistence)
        val coordinator = QuickStartResultRetentionCoordinator(packages, outcomes, results)
        val request = QuickStartRequest(
            requestId = REQUEST_ID, createdAtMillis = NOW, expiresAtMillis = NOW + 300_000,
            targetNodeId = "watch-node", source = QuickStartSource.SINGLE,
            exercises = listOf(QuickStartExercise("item", "exercise", "Squat", 3, "10 reps", 60)),
        )

        suspend fun endedResult(): FinalQuickStartResult {
            val state = outcomes.current()!!
            return FinalQuickStartResult(
                REQUEST_ID, RESULT_ID, state.lastAppliedRevision, PHONE_NODE,
                endedSummary = WorkoutEndedSummary(
                    endedAtEpochMillis = NOW + 1_000,
                    snapshot = state.toProgressSnapshot(60, 180),
                ),
            )
        }

        fun receipt(result: FinalQuickStartResult) =
            QuickStartResultReceipt(REQUEST_ID, RESULT_ID, result.outcomeRevision, PHONE_NODE, NOW + 2_000)
    }

    private class MemoryPersistence(var raw: String? = null) :
        QuickStartPackagePersistence, WorkoutOutcomePersistence, QuickStartResultPersistence {
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

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            assertTrue("Expected ${T::class}, got $failure", failure is T)
            return
        }
        fail("Expected ${T::class} to be thrown")
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val RESULT_ID = "123e4567-e89b-12d3-a456-426614174001"
        const val PHONE_NODE = "phone-node"
        fun set(revision: Long) = WorkoutOutcomeTransition(revision, "item", WorkoutOutcomeTransitionType.SET_COMPLETED)
    }
}
