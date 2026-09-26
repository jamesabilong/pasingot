package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchWorkoutOutcomeStoreTest {
    @Test
    fun `partial and skipped outcomes survive restart with replay-safe revision`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        store.initialize(SESSION_ID, "Workout", plan())
        store.apply(SESSION_ID, set(1))
        store.apply(SESSION_ID, skip(2))

        val restored = WatchWorkoutOutcomeStore(persistence)
        val state = restored.current()!!
        assertEquals(2L, state.lastAppliedRevision)
        assertEquals(listOf("squat", "row"), state.exercises.map { it.itemId })
        assertEquals(ExerciseOutcomeStatus.SKIPPED, state.exercises[0].status)
        assertEquals(1, state.exercises[0].completedSets)
        assertEquals(1, state.toProgressSnapshot(45).progress.pending)
        val writes = persistence.writes
        assertEquals(WorkoutOutcomeTransitionResultCode.DUPLICATE, restored.apply(SESSION_ID, skip(2)).code())
        assertEquals(writes, persistence.writes)

        restored.apply(SESSION_ID, set(3, "row"))
        val complete = WatchWorkoutOutcomeStore(persistence).current()!!.toCompletionSummaryOrNull(2_000, 60)!!
        assertEquals(1, complete.snapshot.progress.completed)
        assertEquals(1, complete.snapshot.progress.skipped)
        assertEquals(0, complete.snapshot.progress.pending)
        assertEquals(2, complete.snapshot.completedSets)
    }

    @Test
    fun `initialization retries preserve progress and competing identities or plans cannot replace it`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        assertTrue(store.initialize(SESSION_ID, "Workout", plan()) is InitializeWorkoutOutcomeResult.Initialized)
        store.apply(SESSION_ID, set(1))
        val before = store.current()
        val writes = persistence.writes
        assertEquals(InitializeWorkoutOutcomeResult.Existing(before!!), store.initialize(SESSION_ID, "Workout", plan()))
        assertTrue(store.initialize("other", "Workout", plan()) is InitializeWorkoutOutcomeResult.Conflict)
        assertTrue(store.initialize(SESSION_ID, "Renamed", plan()) is InitializeWorkoutOutcomeResult.Conflict)
        assertTrue(store.initialize(SESSION_ID, "Workout", plan().reversed()) is InitializeWorkoutOutcomeResult.Conflict)
        assertTrue(store.initialize(SESSION_ID, "Workout", listOf(plan()[0].copy(plannedSets = 3), plan()[1])) is InitializeWorkoutOutcomeResult.Conflict)
        assertEquals(before, store.current())
        assertEquals(writes, persistence.writes)

        store.apply(SESSION_ID, skip(2))
        store.apply(SESSION_ID, skip(3, "row"))
        assertTrue(store.initialize("next", null, plan()) is InitializeWorkoutOutcomeResult.Conflict)
        assertEquals(0, store.current()!!.toProgressSnapshot(0).progress.pending)
    }

    @Test
    fun `invalid initial plan never touches retained storage`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        store.initialize(SESSION_ID, null, plan())
        val before = persistence.raw
        val writes = persistence.writes
        expectFailure<IllegalArgumentException> { store.initialize("other", null, emptyList()) }
        assertEquals(before, persistence.raw)
        assertEquals(writes, persistence.writes)
    }

    @Test
    fun `missing wrong-session and rejected transitions never write`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        assertEquals(ApplyWorkoutOutcomeResult.Missing, store.apply(SESSION_ID, set(1)))
        store.initialize(SESSION_ID, null, plan())
        val writes = persistence.writes
        assertEquals(ApplyWorkoutOutcomeResult.SessionMismatch, store.apply("other", set(1)))
        assertEquals(WorkoutOutcomeTransitionResultCode.REVISION_GAP, store.apply(SESSION_ID, set(2)).code())
        assertEquals(WorkoutOutcomeTransitionResultCode.UNKNOWN_ITEM, store.apply(SESSION_ID, set(1, "missing")).code())
        assertEquals(writes, persistence.writes)
        store.apply(SESSION_ID, skip(1))
        assertEquals(WorkoutOutcomeTransitionResultCode.ALREADY_RESOLVED, store.apply(SESSION_ID, set(2)).code())
        assertEquals(WorkoutOutcomeTransitionResultCode.DUPLICATE, store.apply(SESSION_ID, set(1)).code())
        assertEquals(writes + 1, persistence.writes)
    }

    @Test
    fun `malformed unsupported and inconsistent records are cleared before recovery`() = runTest {
        val initial = newWorkoutOutcomeState(SESSION_ID, null, plan())
        val partial = reduceWorkoutOutcome(initial, set(1)).state
        val invalid = listOf(
            "not json", "{}", "null",
            record(initial, schema = 99),
            "{\"state\":${json.encodeToString(initial)}}",
            record(initial.copy(sessionId = "")),
            record(initial.copy(exercises = emptyList())),
            record(initial.copy(exercises = listOf(initial.exercises[0], initial.exercises[0]))),
            record(initial.copy(exercises = listOf(initial.exercises[0].copy(plannedSets = 0)))),
            record(initial.copy(exercises = listOf(initial.exercises[0].copy(completedSets = -1)))),
            record(initial.copy(exercises = listOf(initial.exercises[0].copy(status = ExerciseOutcomeStatus.COMPLETED)))),
            record(initial.copy(lastAppliedRevision = -1)),
            record(initial.copy(lastAppliedRevision = Long.MAX_VALUE)),
            record(partial.copy(lastAppliedRevision = 0)),
            record(partial).replace("\"lastAppliedRevision\":1", "\"lastAppliedRevision\":2"),
            record(initial).replace("\"pending\"", "\"unknown\""),
        )
        invalid.forEach { raw ->
            val persistence = MemoryPersistence(raw)
            val store = WatchWorkoutOutcomeStore(persistence)
            assertNull("Expected cleanup for $raw", store.current())
            assertNull(persistence.raw)
            assertEquals(1, persistence.writes)
            assertNull(store.current())
            assertEquals(1, persistence.writes)
            assertTrue(store.initialize(SESSION_ID, null, plan()) is InitializeWorkoutOutcomeResult.Initialized)
        }
    }

    @Test
    fun `unknown additive fields preserve a supported valid record`() = runTest {
        val initial = newWorkoutOutcomeState(SESSION_ID, null, plan())
        val persistence = MemoryPersistence(record(initial).replaceFirst("{", "{\"futureMetadata\":true,"))
        assertEquals(initial, WatchWorkoutOutcomeStore(persistence).current())
        assertEquals(0, persistence.writes)
    }

    @Test
    fun `simultaneous transition retries across instances commit one set`() = runTest {
        val persistence = MemoryPersistence()
        WatchWorkoutOutcomeStore(persistence).initialize(SESSION_ID, null, plan())
        val results = List(12) {
            async { WatchWorkoutOutcomeStore(persistence).apply(SESSION_ID, set(1)).code() }
        }.awaitAll()
        assertEquals(1, results.count { it == WorkoutOutcomeTransitionResultCode.APPLIED })
        assertEquals(11, results.count { it == WorkoutOutcomeTransitionResultCode.DUPLICATE })
        assertEquals(2, persistence.writes)
        val restored = WatchWorkoutOutcomeStore(persistence).current()!!
        assertEquals(1L, restored.lastAppliedRevision)
        assertEquals(1, restored.exercises[0].completedSets)
    }

    @Test
    fun `simultaneous initializations retain exactly one session`() = runTest {
        val persistence = MemoryPersistence()
        val results = listOf("one", "two").map { id ->
            async { WatchWorkoutOutcomeStore(persistence).initialize(id, null, plan()) }
        }.awaitAll()
        assertEquals(1, results.count { it is InitializeWorkoutOutcomeResult.Initialized })
        assertEquals(1, results.count { it is InitializeWorkoutOutcomeResult.Conflict })
        assertEquals(1, persistence.writes)
        val winner = (results.first { it is InitializeWorkoutOutcomeResult.Initialized } as InitializeWorkoutOutcomeResult.Initialized).state
        assertEquals(winner, WatchWorkoutOutcomeStore(persistence).current())
    }

    @Test
    fun `transition returns only after persistence and following revision reads committed state`() = runTest {
        val persistence = MemoryPersistence()
        val firstStore = WatchWorkoutOutcomeStore(persistence)
        firstStore.initialize(SESSION_ID, null, plan())
        val before = persistence.raw
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        persistence.beforeWrite = { writing.complete(Unit); release.await() }
        val first = async { firstStore.apply(SESSION_ID, set(1)) }
        writing.await()
        val next = async { WatchWorkoutOutcomeStore(persistence).apply(SESSION_ID, set(2)) }
        runCurrent()
        assertFalse(first.isCompleted)
        assertFalse(next.isCompleted)
        assertEquals(before, persistence.raw)
        release.complete(Unit)
        assertEquals(WorkoutOutcomeTransitionResultCode.APPLIED, first.await().code())
        assertEquals(WorkoutOutcomeTransitionResultCode.APPLIED, next.await().code())
        val restored = WatchWorkoutOutcomeStore(persistence).current()!!
        assertEquals(2L, restored.lastAppliedRevision)
        assertEquals(ExerciseOutcomeStatus.COMPLETED, restored.exercises[0].status)
    }

    @Test
    fun `failed write leaves prior revision retryable without optimistic progress`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        store.initialize(SESSION_ID, null, plan())
        val before = persistence.raw
        persistence.beforeWrite = { throw IOException("disk full") }
        expectFailure<IOException> { store.apply(SESSION_ID, set(1)) }
        assertEquals(before, persistence.raw)
        persistence.beforeWrite = {}
        assertEquals(0L, WatchWorkoutOutcomeStore(persistence).current()!!.lastAppliedRevision)
        assertEquals(WorkoutOutcomeTransitionResultCode.APPLIED, store.apply(SESSION_ID, set(1)).code())
    }

    @Test
    fun `uncertain post-commit failure is reconciled as a duplicate on retry`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        store.initialize(SESSION_ID, null, plan())
        persistence.afterWrite = { throw IOException("response lost after commit") }
        expectFailure<IOException> { store.apply(SESSION_ID, set(1)) }
        persistence.afterWrite = {}
        assertEquals(WorkoutOutcomeTransitionResultCode.DUPLICATE, store.apply(SESSION_ID, set(1)).code())
        assertEquals(1, store.current()!!.exercises[0].completedSets)
        assertEquals(2, persistence.writes)
    }

    @Test
    fun `read failures cancellation and failed cleanup do not silently erase state`() = runTest {
        val persistence = MemoryPersistence(record(newWorkoutOutcomeState(SESSION_ID, null, plan())))
        val store = WatchWorkoutOutcomeStore(persistence)
        val before = persistence.raw
        persistence.readFailure = IOException("unreadable")
        expectFailure<IOException> { store.current() }
        persistence.readFailure = CancellationException("cancelled")
        expectFailure<CancellationException> { store.current() }
        assertEquals(before, persistence.raw)
        assertEquals(0, persistence.writes)
        persistence.readFailure = null
        persistence.raw = "corrupt"
        persistence.beforeWrite = { throw IOException("cannot clear") }
        expectFailure<IOException> { store.current() }
        assertEquals("corrupt", persistence.raw)
        persistence.beforeWrite = {}
        assertNull(store.current())
    }

    @Test
    fun `cancelled transition releases gate and leaves durable revision authoritative`() = runTest {
        val persistence = MemoryPersistence()
        val store = WatchWorkoutOutcomeStore(persistence)
        store.initialize(SESSION_ID, null, plan())
        val writing = CompletableDeferred<Unit>()
        persistence.beforeWrite = { writing.complete(Unit); CompletableDeferred<Unit>().await() }
        val cancelled = async { store.apply(SESSION_ID, set(1)) }
        writing.await()
        cancelled.cancelAndJoin()
        persistence.beforeWrite = {}
        assertEquals(0L, store.current()!!.lastAppliedRevision)
        assertEquals(WorkoutOutcomeTransitionResultCode.APPLIED, store.apply(SESSION_ID, set(1)).code())
    }

    private class MemoryPersistence(var raw: String? = null) : WorkoutOutcomePersistence {
        var writes = 0
        var readFailure: Throwable? = null
        var beforeWrite: suspend () -> Unit = {}
        var afterWrite: suspend () -> Unit = {}

        override suspend fun read(): String? {
            readFailure?.let { throw it }
            val snapshot = raw
            yield() // Expose lost updates if different instances stop sharing the gate.
            return snapshot
        }

        override suspend fun write(raw: String?) {
            beforeWrite()
            this.raw = raw
            writes++
            afterWrite()
        }
    }

    private fun plan() = listOf(
        WorkoutExerciseOutcomePlan("squat", "exercise-squat", "Squat", 2),
        WorkoutExerciseOutcomePlan("row", "exercise-row", "Row", 1),
    )

    private fun set(revision: Long, itemId: String = "squat") =
        WorkoutOutcomeTransition(revision, itemId, WorkoutOutcomeTransitionType.SET_COMPLETED)

    private fun skip(revision: Long, itemId: String = "squat") =
        WorkoutOutcomeTransition(revision, itemId, WorkoutOutcomeTransitionType.EXERCISE_SKIPPED)

    private fun ApplyWorkoutOutcomeResult.code() = (this as ApplyWorkoutOutcomeResult.Reduced).result.code

    private fun record(state: WorkoutOutcomeState, schema: Int = 1) =
        "{\"schemaVersion\":$schema,\"state\":${json.encodeToString(state)}}"

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
        const val SESSION_ID = "session-1"
        val json = Json { encodeDefaults = true }
    }
}
