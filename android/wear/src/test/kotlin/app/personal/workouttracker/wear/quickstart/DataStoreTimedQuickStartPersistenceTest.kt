package app.personal.workouttracker.wear.quickstart

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.wear.data.SessionOutcomeActionType
import app.personal.workouttracker.wear.session.updateTimedSetCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reopen real DataStore files with independent scopes, without touching app/user storage. */
class DataStoreTimedQuickStartPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `active deadline and exact frozen milliseconds survive independent file scopes`() = runTest {
        val active = withStore { initialize(it) }
        val paused = withStore { runtime ->
            assertEquals(active, runtime.current())
            val adapter = QuickStartSessionStore(ID, runtime) { NOW + 1_250 }
            val entry = adapter.getEntry(ID)!!
            val prior = entry.sessionState!!
            val next = updateTimedSetCountdown(entry, prior, prior.copy(
                status = SessionStatus.PAUSED, elapsedStartedAtEpochMillis = null,
                accumulatedElapsedMillis = 1_250,
            ), NOW + 1_250)
            assertTrue(adapter.commitSession(entry, entry.copy(sessionState = next)))
            runtime.current()!!
        }
        assertEquals(28_750L, paused.session.pausedTimedSetRemainingMillis)
        assertNull(paused.session.timedSetDeadlineEpochMillis)
        val resumed = withStore { runtime ->
            assertEquals(paused, runtime.current())
            assertEquals(InitializeQuickStartRuntimeResult.Existing(paused),
                runtime.initialize(timedPackage(), initialSession(NOW + 90_000), NOW + 90_000))
            val adapter = QuickStartSessionStore(ID, runtime) { NOW + 90_000 }
            val entry = adapter.getEntry(ID)!!
            val prior = entry.sessionState!!
            val next = updateTimedSetCountdown(entry, prior, prior.copy(
                status = SessionStatus.ACTIVE, elapsedStartedAtEpochMillis = NOW + 90_000,
            ), NOW + 90_000)
            assertTrue(adapter.commitSession(entry, entry.copy(sessionState = next)))
            runtime.current()!!
        }
        withStore { runtime ->
            assertEquals(resumed, runtime.current())
            assertEquals(NOW + 118_750, runtime.current()!!.session.timedSetDeadlineEpochMillis)
            assertNull(runtime.current()!!.session.pausedTimedSetRemainingMillis)
            assertEquals(active.startedAcknowledgement, runtime.current()!!.startedAcknowledgement)
            assertEquals(0, runtime.current()!!.outcomes.exercises.single().completedSets)
            assertEquals(2L, runtime.current()!!.runtimeRevision)
        }
    }

    @Test fun `timed outcomes final result and exact receipt tombstone survive file recreation`() = runTest {
        withStore { initialize(it) }
        val first = withStore { runtime ->
            val adapter = QuickStartSessionStore(ID, runtime) { NOW + 30_000 }
            val entry = adapter.getEntry(ID)!!
            val next = entry.sessionState!!.copy(currentSet = 2, timedSetDeadlineEpochMillis = NOW + 60_000)
            val action = SessionOutcomeAction(SessionOutcomeActionType.SET_COMPLETED, 0)
            assertTrue(adapter.commitSession(entry, entry.copy(sessionState = next), action = action))
            assertFalse(adapter.commitSession(entry, entry.copy(sessionState = next), action = action))
            runtime.current()!!
        }
        val final = withStore { runtime ->
            assertEquals(first, runtime.current())
            assertEquals(1, first.outcomes.exercises.single().completedSets)
            val adapter = QuickStartSessionStore(ID, runtime, { NOW + 60_000 },
                QuickStartResultClient { sent ->
                    assertEquals(sent, runtime.current()!!.finalResult)
                    error("offline")
                })
            val entry = adapter.getEntry(ID)!!
            val terminal = entry.sessionState!!.copy(status = SessionStatus.COMPLETED,
                elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 60_000,
                timedSetDeadlineEpochMillis = null)
            assertTrue(adapter.commitSession(entry, entry.copy(sessionState = terminal),
                action = SessionOutcomeAction(SessionOutcomeActionType.SET_COMPLETED, 0)))
            runtime.current()!!
        }
        assertEquals(2, final.outcomes.exercises.single().completedSets)
        assertEquals(2L, final.finalResult!!.outcomeRevision)
        val receipt = QuickStartResultReceipt(ID, final.finalResult!!.resultId,
            final.finalResult!!.outcomeRevision, PHONE, NOW + 61_000)
        withStore { runtime ->
            assertEquals(final, runtime.current())
            assertEquals(ClearQuickStartRuntimeResult.MISMATCH,
                runtime.clearAcknowledged(receipt.copy(outcomeRevision = 1), PHONE))
            assertEquals(final, runtime.current())
            assertEquals(ClearQuickStartRuntimeResult.CLEARED, runtime.clearAcknowledged(receipt, PHONE))
        }
        withStore { runtime ->
            assertNull(runtime.current())
            assertEquals(ClearQuickStartRuntimeResult.ALREADY_CLEARED, runtime.clearAcknowledged(receipt, PHONE))
            assertEquals(InitializeQuickStartRuntimeResult.AlreadyAcknowledged(receipt),
                runtime.initialize(timedPackage(), initialSession(), NOW))
        }
    }

    private fun timedPackage() = runtimePackage().copy(request = timedRequest())

    private suspend fun initialize(runtime: QuickStartRuntimeStore): QuickStartRuntimeState =
        (runtime.initialize(timedPackage(), initialSession().copy(timedSetDeadlineEpochMillis = NOW + 30_000), NOW)
            as InitializeQuickStartRuntimeResult.Initialized).state

    private suspend fun <T> withStore(block: suspend (QuickStartRuntimeStore) -> T): T {
        val file = temporaryFolder.root.resolve("timed_runtime.preferences_pb")
        val job = SupervisorJob()
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            return block(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(dataStore)))
        } finally { job.cancelAndJoin() }
    }
}
