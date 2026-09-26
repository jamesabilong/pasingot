package app.personal.workouttracker.wear.quickstart

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.personal.workouttracker.wear.session.WorkoutExerciseOutcomePlan
import app.personal.workouttracker.wear.session.newWorkoutOutcomeState
import app.personal.workouttracker.wear.session.reduceWorkoutOutcome
import app.personal.workouttracker.wear.session.toCompletionSummaryOrNull
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransition
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreQuickStartResultPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `final result and exact receipt survive separate DataStore file scopes`() = runTest {
        val file = temporaryFolder.root.resolve("quick_start_result.preferences_pb")
        val requestId = "123e4567-e89b-12d3-a456-426614174000"
        val initial = newWorkoutOutcomeState(
            requestId, null,
            listOf(WorkoutExerciseOutcomePlan("item", "exercise", "Squat", 1)),
        )
        val complete = reduceWorkoutOutcome(
            initial, WorkoutOutcomeTransition(1, "item", WorkoutOutcomeTransitionType.SET_COMPLETED),
        ).state.toCompletionSummaryOrNull(2_000, 40)!!
        val result = FinalQuickStartResult(requestId, "result-1", 1, "phone-node", complete)
        val receipt = QuickStartResultReceipt(requestId, "result-1", 1, "phone-node", 3_000)

        val firstJob = SupervisorJob()
        try {
            val persistence = DataStoreQuickStartResultPersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file },
            )
            val store = WatchQuickStartResultStore(persistence)
            assertEquals(SaveQuickStartResult.Saved(result), store.save(result))
            assertEquals(result, store.pendingResult())
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        try {
            val persistence = DataStoreQuickStartResultPersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file },
            )
            val store = WatchQuickStartResultStore(persistence)
            assertEquals(result, store.pendingResult())
            assertNotNull((store.acceptReceipt(receipt, "phone-node") as AcceptQuickStartResultReceipt.Recorded).confirmed)
        } finally {
            secondJob.cancelAndJoin()
        }

        val thirdJob = SupervisorJob()
        try {
            val persistence = DataStoreQuickStartResultPersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(thirdJob + Dispatchers.IO)) { file },
            )
            val store = WatchQuickStartResultStore(persistence)
            assertNull(store.pendingResult())
            val confirmed = store.confirmed()!!
            assertEquals(result, confirmed.result)
            assertEquals(receipt, confirmed.receipt)
            assertEquals(CompactQuickStartResult.Compacted, store.compact(confirmed))
            assertEquals(
                AcceptQuickStartResultReceipt.AlreadyAcknowledged(receipt),
                store.acceptReceipt(receipt, "phone-node"),
            )
        } finally {
            thirdJob.cancelAndJoin()
        }
    }
}
