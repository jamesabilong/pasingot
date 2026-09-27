package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.wear.data.SessionOutcomeActionType
import app.personal.workouttracker.shared.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartSessionStoreTest {
    @Test
    fun `session adapter commits engine cursor and outcome in one runtime write`() = runTest {
        val persistence = RuntimeMemoryPersistence()
        val runtime = QuickStartRuntimeStore(persistence)
        runtime.initialize(runtimePackage(), initialSession(), NOW)
        val store = QuickStartSessionStore(ID, runtime) { NOW + 1_000 }
        val entry = requireNotNull(store.getEntry(ID))
        val updated = entry.copy(sessionState = requireNotNull(entry.sessionState).copy(currentSet = 2))

        assertTrue(store.commitSession(
            expected = entry,
            updated = updated,
            action = SessionOutcomeAction(SessionOutcomeActionType.SET_COMPLETED, 0),
        ))

        val committed = requireNotNull(runtime.current())
        assertEquals(2, committed.session.currentSet)
        assertEquals(1, committed.outcomes.exercises[0].completedSets)
        assertEquals(1L, committed.runtimeRevision)
    }

    @Test
    fun `session adapter rejects plan edits and stale entries`() = runTest {
        val persistence = RuntimeMemoryPersistence()
        val runtime = QuickStartRuntimeStore(persistence)
        runtime.initialize(runtimePackage(), initialSession(), NOW)
        val store = QuickStartSessionStore(ID, runtime) { NOW + 1_000 }
        val entry = requireNotNull(store.getEntry(ID))

        assertFalse(store.commitSession(
            expected = entry,
            updated = entry.copy(exercises = entry.exercises.map { it.copy(sets = it.sets + 1) }),
        ))

        val next = entry.copy(sessionState = requireNotNull(entry.sessionState).copy(currentSet = 2))
        assertTrue(store.commitSession(
            entry,
            next,
            action = SessionOutcomeAction(SessionOutcomeActionType.SET_COMPLETED, 0),
        ))
        assertFalse(store.commitSession(entry, next))
    }

    @Test
    fun `terminal commit is durable before result transport and survives transport failure`() = runTest {
        val persistence = RuntimeMemoryPersistence()
        val runtime = QuickStartRuntimeStore(persistence)
        runtime.initialize(runtimePackage(), initialSession(), NOW)
        var observed: FinalQuickStartResult? = null
        val store = QuickStartSessionStore(
            requestId = ID,
            runtimeStore = runtime,
            nowEpochMillis = { NOW + 1_000 },
            resultClient = QuickStartResultClient { result ->
                observed = requireNotNull(runtime.current()).finalResult
                assertEquals(result, observed)
                error("offline")
            },
        )
        val entry = requireNotNull(store.getEntry(ID))
        val ended = requireNotNull(entry.sessionState).copy(
            status = SessionStatus.ENDED,
            elapsedStartedAtEpochMillis = null,
            accumulatedElapsedMillis = 1_000,
            lastStopReason = "ended_by_user",
        )

        assertTrue(store.commitSession(entry, entry.copy(sessionState = ended)))
        assertEquals(runtime.current()?.finalResult, observed)
        assertEquals(SessionStatus.ENDED, runtime.current()?.session?.status)
    }
}
