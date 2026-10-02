package app.personal.workouttracker.wear.quickstart

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.personal.workouttracker.shared.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreQuickStartRuntimePersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `runtime Started result and cleanup tombstone survive independent real file scopes`() = runTest {
        val file = temporaryFolder.root.resolve("quick_start_runtime.preferences_pb")
        suspend fun <T> withStore(block: suspend (QuickStartRuntimeStore) -> T): T {
            val job = SupervisorJob()
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
                return block(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(dataStore)))
            } finally { job.cancelAndJoin() }
        }
        val initialized = withStore { store ->
            (store.initialize(runtimePackage(), initialSession(), NOW)
                as InitializeQuickStartRuntimeResult.Initialized).state
        }
        val final = withStore { store ->
            assertEquals(initialized, store.current())
            val ended = initialized.session.copy(status = SessionStatus.ENDED,
                elapsedStartedAtEpochMillis = null, accumulatedElapsedMillis = 13_000)
            (store.transition(ID, 0, initialized.session, ended, nowEpochMillis = NOW + 13_000)
                as ApplyQuickStartRuntimeResult.Applied).state
        }
        val receipt = QuickStartResultReceipt(ID, final.finalResult!!.resultId, 0, PHONE, NOW + 14_000)
        withStore { store ->
            assertEquals(final, store.current())
            assertEquals(initialized.startedAcknowledgement, store.current()!!.startedAcknowledgement)
            assertEquals(false, store.observePhoneReceipt(ID).first())
            val confirmed = async(start = CoroutineStart.UNDISPATCHED) {
                store.observePhoneReceipt(ID).first { it }
            }
            assertEquals(ClearQuickStartRuntimeResult.CLEARED, store.clearAcknowledged(receipt, PHONE))
            assertEquals(true, confirmed.await())
            assertEquals(false, store.observePhoneReceipt("different-request").first())
        }
        withStore { store ->
            assertNull(store.current())
            assertEquals(true, store.observePhoneReceipt(ID).first())
            assertEquals(ClearQuickStartRuntimeResult.ALREADY_CLEARED, store.clearAcknowledged(receipt, PHONE))
            assertEquals(InitializeQuickStartRuntimeResult.AlreadyAcknowledged(receipt),
                store.initialize(runtimePackage(), initialSession(), NOW))
        }
    }
}
