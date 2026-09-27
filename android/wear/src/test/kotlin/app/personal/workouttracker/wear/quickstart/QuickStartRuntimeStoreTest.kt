package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickStartRuntimeStoreTest {
    @Test
    fun `initialization atomically saves runtime outcomes and exact Started receipt across recreation`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = QuickStartRuntimeStore(disk)
        val result = store.initialize(runtimePackage(), initialSession(), NOW)
            as InitializeQuickStartRuntimeResult.Initialized
        assertEquals(1, disk.writes)
        assertEquals(QuickStartStatus.STARTED, result.state.startedAcknowledgement.status)
        assertEquals(2L, result.state.startedAcknowledgement.revision)
        assertEquals(NOW, result.state.startedAcknowledgement.watchUpdatedAtMillis)
        assertEquals(listOf("item-a", "item-b"), result.state.outcomes.exercises.map { it.itemId })
        assertEquals(0L, result.state.runtimeRevision)
        assertEquals(0L, result.state.outcomes.lastAppliedRevision)
        val restored = QuickStartRuntimeStore(disk)
        assertEquals(result.state, restored.current())
        assertEquals(InitializeQuickStartRuntimeResult.Existing(result.state),
            restored.initialize(runtimePackage(), initialSession(NOW + 700_000), NOW + 700_000))
        assertEquals(1, disk.writes)
    }

    @Test
    fun `ready missing source invalid session and malformed package cannot initialize`() = runTest {
        val invalidPackages = listOf(
            runtimePackage().copy(state = QuickStartPackageState.READY),
            runtimePackage().copy(sourcePhoneNodeId = null),
            runtimePackage().copy(sourcePhoneNodeId = WATCH),
            runtimePackage().copy(sourcePhoneNodeId = "\nphone"),
            runtimePackage().copy(expiresLocallyAtMillis = NOW),
            runtimePackage().let { it.copy(request = it.request.copy(schemaVersion = 999)) },
            runtimePackage().let { it.copy(request = it.request.copy(revision = Long.MAX_VALUE)) },
        )
        for (sessionPackage in invalidPackages) {
            val disk = RuntimeMemoryPersistence()
            assertTrue(QuickStartRuntimeStore(disk).initialize(sessionPackage, initialSession(), NOW)
                is InitializeQuickStartRuntimeResult.Invalid)
            assertNull(disk.raw)
        }
        val invalidSessions = listOf(
            initialSession().copy(workoutEntryId = "wrong"),
            initialSession().copy(currentSet = 2),
            initialSession().copy(exerciseIndex = 1),
            initialSession().copy(status = SessionStatus.PAUSED),
            initialSession().copy(accumulatedElapsedMillis = 100),
            initialSession().copy(elapsedStartedAtEpochMillis = NOW + 1),
        )
        for (session in invalidSessions) {
            val disk = RuntimeMemoryPersistence()
            assertTrue(QuickStartRuntimeStore(disk).initialize(runtimePackage(), session, NOW)
                is InitializeQuickStartRuntimeResult.Invalid)
            assertNull(disk.raw)
        }
    }

    @Test
    fun `immutable request or source conflicts preserve existing runtime`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val original = store.current()!!
        val packageValue = runtimePackage()
        val conflicts = listOf(
            packageValue.copy(sourcePhoneNodeId = "other-phone"),
            packageValue.copy(request = packageValue.request.copy(title = "Changed")),
            packageValue.copy(request = packageValue.request.copy(exercises = packageValue.request.exercises.reversed())),
            packageValue.copy(request = packageValue.request.copy(exercises = packageValue.request.exercises.map {
                it.copy(sets = it.sets + 1)
            })),
        )
        for (conflict in conflicts) {
            assertEquals(InitializeQuickStartRuntimeResult.Conflict(original),
                store.initialize(conflict, initialSession(), NOW))
        }
        assertEquals(original, store.current())
        assertEquals(1, disk.writes)
    }

    @Test
    fun `failed and uncertain initialization only expose committed Started state`() = runTest {
        for (afterCommit in listOf(false, true)) {
            val disk = RuntimeMemoryPersistence()
            if (afterCommit) disk.afterWrite = { throw IOException("lost write response") }
            else disk.beforeWrite = { throw IOException("write failed") }
            expectFailure<IOException> {
                QuickStartRuntimeStore(disk).initialize(runtimePackage(), initialSession(), NOW)
            }
            disk.beforeWrite = {}
            disk.afterWrite = {}
            val restored = QuickStartRuntimeStore(disk)
            if (afterCommit) {
                assertEquals(NOW, restored.current()!!.startedAcknowledgement.watchUpdatedAtMillis)
                assertTrue(restored.initialize(runtimePackage(), initialSession(NOW + 1), NOW + 1)
                    is InitializeQuickStartRuntimeResult.Existing)
            } else {
                assertNull(restored.current())
                assertTrue(restored.initialize(runtimePackage(), initialSession(NOW + 1), NOW + 1)
                    is InitializeQuickStartRuntimeResult.Initialized)
            }
            assertEquals(1, disk.writes)
        }
    }

    @Test
    fun `runtime and semantic outcome commit atomically and stale taps cannot double count`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val next = before.session.copy(currentSet = 2, status = SessionStatus.RESTING,
            restUntilEpochMillis = NOW + 60_000)
        val applied = store.transition(ID, 0, before.session, next, complete(0), NOW + 1_000)
            as ApplyQuickStartRuntimeResult.Applied
        assertEquals(1, applied.state.outcomes.exercises[0].completedSets)
        assertEquals(1L, applied.state.outcomes.lastAppliedRevision)
        assertEquals(1L, applied.state.runtimeRevision)
        assertEquals(next, applied.state.session)
        assertEquals(ApplyQuickStartRuntimeResult.Stale(applied.state),
            QuickStartRuntimeStore(disk).transition(ID, 0, before.session, next, complete(0), NOW + 2_000))
        assertEquals(2, disk.writes)
    }

    @Test
    fun `failed set transition preserves both prior values and lost response preserves both next values`() = runTest {
        for (afterCommit in listOf(false, true)) {
            val disk = RuntimeMemoryPersistence()
            val store = initialized(disk)
            val before = store.current()!!
            val next = before.session.copy(currentSet = 2)
            if (afterCommit) disk.afterWrite = { throw IOException("lost response") }
            else disk.beforeWrite = { throw IOException("rejected") }
            expectFailure<IOException> { store.transition(ID, 0, before.session, next, complete(0), NOW + 1) }
            disk.beforeWrite = {}
            disk.afterWrite = {}
            val restored = QuickStartRuntimeStore(disk)
            assertEquals(if (afterCommit) next else before.session, restored.current()!!.session)
            assertEquals(if (afterCommit) 1 else 0, restored.current()!!.outcomes.exercises[0].completedSets)
            val retry = restored.transition(ID, 0, before.session, next, complete(0), NOW + 2)
            assertTrue(if (afterCommit) retry is ApplyQuickStartRuntimeResult.Stale
                else retry is ApplyQuickStartRuntimeResult.Applied)
            assertEquals(1, restored.current()!!.outcomes.exercises[0].completedSets)
        }
    }

    @Test
    fun `parallel store instances permit one semantic transition from same revision`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        disk.beforeWrite = { entered.complete(Unit); release.await() }
        val first = async { store.transition(ID, 0, before.session, before.session.copy(currentSet = 2), complete(0), NOW) }
        entered.await()
        val second = async { QuickStartRuntimeStore(disk).transition(ID, 0, before.session,
            before.session.copy(currentSet = 2), complete(0), NOW) }
        runCurrent()
        assertFalse(second.isCompleted)
        release.complete(Unit)
        assertTrue(first.await() is ApplyQuickStartRuntimeResult.Applied)
        assertTrue(second.await() is ApplyQuickStartRuntimeResult.Stale)
        assertEquals(2, disk.writes)
        assertEquals(1, store.current()!!.outcomes.exercises[0].completedSets)
    }

    @Test
    fun `invalid action cursor plan and premature completion cannot corrupt outcomes`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val attempts = listOf(
            before.session.copy(currentSet = 2) to null,
            before.session.copy(exerciseIndex = 1) to complete(1),
            before.session.copy(currentSet = 3) to complete(0),
            before.session.copy(workoutEntryId = "other") to null,
            before.session.copy(status = SessionStatus.COMPLETED, elapsedStartedAtEpochMillis = null) to null,
        )
        for ((next, action) in attempts) {
            assertTrue(store.transition(ID, 0, before.session, next, action, NOW)
                is ApplyQuickStartRuntimeResult.Invalid)
        }
        assertEquals(before, store.current())
        assertEquals(1, disk.writes)
    }

    @Test
    fun `pause and resume increment runtime revision without changing outcomes`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val paused = before.session.copy(status = SessionStatus.PAUSED,
            elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 1_000)
        val pause = store.transition(ID, 0, before.session, paused, nowEpochMillis = NOW + 1_000)
            as ApplyQuickStartRuntimeResult.Applied
        assertEquals(before.outcomes, pause.state.outcomes)
        assertTrue(store.transition(ID, 1, paused, paused.copy(currentSet = 2), complete(0), NOW)
            is ApplyQuickStartRuntimeResult.Invalid)
        val resumed = paused.copy(status = SessionStatus.ACTIVE, elapsedStartedAtEpochMillis = NOW + 2_000)
        val resume = store.transition(ID, 1, paused, resumed, nowEpochMillis = NOW + 2_000)
            as ApplyQuickStartRuntimeResult.Applied
        assertEquals(2L, resume.state.runtimeRevision)
        assertEquals(0L, resume.state.outcomes.lastAppliedRevision)
        assertEquals(ApplyQuickStartRuntimeResult.Unchanged(resume.state),
            store.transition(ID, 2, resumed, resumed, nowEpochMillis = NOW + 3_000))
        assertEquals(3, disk.writes)
    }

    @Test
    fun `completed and skipped exercise results freeze with terminal session in one write`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        advance(store, initialSession().copy(currentSet = 2), complete(0))
        advance(store, initialSession().copy(exerciseIndex = 1), complete(0))
        val before = store.current()!!
        val ended = before.session.copy(status = SessionStatus.COMPLETED,
            elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 7_001)
        val final = advance(store, ended, skip(1)).finalResult!!
        assertEquals(2, final.snapshot.completedSets)
        assertEquals(1, final.snapshot.progress.completed)
        assertEquals(1, final.snapshot.progress.skipped)
        assertEquals(0, final.snapshot.progress.pending)
        assertEquals(8, final.snapshot.elapsedActiveSeconds)
        assertEquals(3L, final.outcomeRevision)
        assertEquals("runtime-$ID", final.resultId)
        assertEquals(NOW + 10_000, final.summary!!.completedAtEpochMillis)
        assertEquals(final, QuickStartRuntimeStore(disk).current()!!.finalResult)
        assertTrue(store.transition(ID, 3, ended, initialSession(), nowEpochMillis = NOW + 20_000)
            is ApplyQuickStartRuntimeResult.Finalized)
    }

    @Test
    fun `zero and partially completed endings retain pending outcomes`() = runTest {
        for (sets in listOf(0, 1)) {
            val disk = RuntimeMemoryPersistence()
            val store = initialized(disk)
            if (sets == 1) advance(store, initialSession().copy(currentSet = 2), complete(0))
            val before = store.current()!!
            val ended = before.session.copy(status = SessionStatus.ENDED,
                elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 1_000)
            val result = advance(store, ended).finalResult!!
            assertNull(result.summary)
            assertNotNull(result.endedSummary)
            assertEquals(sets, result.snapshot.completedSets)
            assertEquals(2, result.snapshot.progress.pending)
            assertEquals(ExerciseOutcomeStatus.PENDING, result.snapshot.exercises[0].status)
        }
    }

    @Test
    fun `uncertain terminal commit recovers same result identity timestamp and elapsed time`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val ended = before.session.copy(status = SessionStatus.ENDED,
            elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 6_000)
        disk.afterWrite = { throw IOException("lost terminal response") }
        expectFailure<IOException> { store.transition(ID, 0, before.session, ended, nowEpochMillis = NOW + 6_000) }
        disk.afterWrite = {}
        val recovered = QuickStartRuntimeStore(disk).current()!!
        val result = recovered.finalResult!!
        assertEquals(NOW + 6_000, result.endedSummary!!.endedAtEpochMillis)
        assertEquals(6, result.snapshot.elapsedActiveSeconds)
        assertEquals(ApplyQuickStartRuntimeResult.Stale(recovered),
            store.transition(ID, 0, before.session, ended, nowEpochMillis = NOW + 50_000))
        assertEquals(result, store.current()!!.finalResult)
        assertEquals(2, disk.writes)
    }

    @Test
    fun `exact receipt cleanup is replayable blocks resurrection and protects newer runtime`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val before = store.current()!!
        val final = advance(store, before.session.copy(status = SessionStatus.ENDED,
            elapsedStartedAtEpochMillis = null)).finalResult!!
        val receipt = QuickStartResultReceipt(ID, final.resultId, final.outcomeRevision, PHONE, NOW + 20_000)
        assertEquals(ClearQuickStartRuntimeResult.MISMATCH, store.clearAcknowledged(receipt, "other-phone"))
        assertEquals(ClearQuickStartRuntimeResult.MISMATCH,
            store.clearAcknowledged(receipt.copy(outcomeRevision = 2), PHONE))
        assertEquals(ClearQuickStartRuntimeResult.CLEARED, store.clearAcknowledged(receipt, PHONE))
        assertNull(store.current())
        assertEquals(ClearQuickStartRuntimeResult.ALREADY_CLEARED,
            QuickStartRuntimeStore(disk).clearAcknowledged(receipt, PHONE))
        assertEquals(InitializeQuickStartRuntimeResult.AlreadyAcknowledged(receipt),
            store.initialize(runtimePackage(), initialSession(), NOW))
        val newId = "123e4567-e89b-12d3-a456-426614174001"
        val sessionPackage = runtimePackage().let { it.copy(request = it.request.copy(requestId = newId)) }
        assertTrue(store.initialize(sessionPackage, initialSession().copy(workoutEntryId = newId), NOW)
            is InitializeQuickStartRuntimeResult.Initialized)
        assertEquals(ClearQuickStartRuntimeResult.MISMATCH, store.clearAcknowledged(receipt, PHONE))
        assertEquals(newId, store.current()!!.session.workoutEntryId)
    }

    @Test
    fun `unreadable future and internally inconsistent records fail closed without replacing bytes`() = runTest {
        val disk = RuntimeMemoryPersistence()
        val store = initialized(disk)
        val valid = disk.raw!!
        val root = Json.parseToJsonElement(valid).jsonObject
        val runtime = root.getValue("runtime").jsonObject
        val session = runtime.getValue("session").jsonObject
        val corrupted = JsonObject(root + ("runtime" to JsonObject(runtime +
            ("session" to JsonObject(session + ("currentSet" to JsonPrimitive(2))))))).toString()
        val invalid = listOf("broken json", "{}", valid.replace("\"schemaVersion\":1", "\"schemaVersion\":999"), corrupted)
        for (raw in invalid) {
            disk.raw = raw
            val writes = disk.writes
            expectFailure<IllegalStateException> { store.current() }
            expectFailure<IllegalStateException> { store.initialize(runtimePackage(), initialSession(), NOW) }
            assertEquals(raw, disk.raw)
            assertEquals(writes, disk.writes)
        }
    }

    private suspend fun initialized(disk: RuntimeMemoryPersistence): QuickStartRuntimeStore =
        QuickStartRuntimeStore(disk).also {
            assertTrue(it.initialize(runtimePackage(), initialSession(), NOW) is InitializeQuickStartRuntimeResult.Initialized)
        }

    private suspend fun advance(
        store: QuickStartRuntimeStore,
        next: SessionState,
        action: QuickStartRuntimeAction? = null,
    ): QuickStartRuntimeState {
        val before = store.current()!!
        return (store.transition(ID, before.runtimeRevision, before.session, next, action, NOW + 10_000)
            as ApplyQuickStartRuntimeResult.Applied).state
    }

    private fun complete(index: Int) = QuickStartRuntimeAction(WorkoutOutcomeTransitionType.SET_COMPLETED, index)
    private fun skip(index: Int) = QuickStartRuntimeAction(WorkoutOutcomeTransitionType.EXERCISE_SKIPPED, index)

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}

internal const val ID = "123e4567-e89b-12d3-a456-426614174000"
internal const val NOW = 1_000_000L
internal const val PHONE = "phone-node"
internal const val WATCH = "watch-node"

internal fun initialSession(now: Long = NOW) = SessionState(
    workoutEntryId = ID, exerciseIndex = 0, currentSet = 1,
    status = SessionStatus.ACTIVE, elapsedStartedAtEpochMillis = now,
)

internal fun runtimePackage(): WatchSessionPackage {
    val request = QuickStartRequest(
        requestId = ID, createdAtMillis = NOW, expiresAtMillis = NOW + 300_000,
        targetNodeId = WATCH, title = "Quick workout", source = QuickStartSource.LIBRARY_PLAYLIST,
        exercises = listOf(
            QuickStartExercise("item-a", "squat", "Squat", 2, "10", 60),
            QuickStartExercise("item-b", "hold", "Hold", 1, "30 sec", 0),
        ),
    )
    return (validateQuickStartRequest(request, NOW) as QuickStartValidationResult.Valid).sessionPackage.copy(
        state = QuickStartPackageState.STARTING, sourcePhoneNodeId = PHONE,
    )
}

internal class RuntimeMemoryPersistence : QuickStartRuntimePersistence {
    var raw: String? = null
    var writes = 0
    var beforeWrite: suspend () -> Unit = {}
    var afterWrite: suspend () -> Unit = {}
    override suspend fun read(): String? = raw
    override suspend fun write(raw: String) {
        beforeWrite()
        this.raw = raw
        writes++
        afterWrite()
    }
}
