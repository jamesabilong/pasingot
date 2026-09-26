package app.personal.workouttracker.wear.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreWorkoutOutcomePersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `real preferences file recovers outcomes and revision after closing its scope`() = runTest {
        val file = temporaryFolder.root.resolve("outcomes.preferences_pb")
        val firstJob = SupervisorJob()
        val firstPersistence = DataStoreWorkoutOutcomePersistence(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file },
        )
        val transition = WorkoutOutcomeTransition(1, "squat", WorkoutOutcomeTransitionType.SET_COMPLETED)
        val expected = try {
            val store = WatchWorkoutOutcomeStore(firstPersistence)
            store.initialize("session-1", "Workout", listOf(WorkoutExerciseOutcomePlan("squat", "squat-id", "Squat", 2)))
            store.apply("session-1", transition)
            store.current()
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        try {
            val persistence = DataStoreWorkoutOutcomePersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file },
            )
            val restored = WatchWorkoutOutcomeStore(persistence)
            assertEquals(expected, restored.current())
            val replay = restored.apply("session-1", transition) as ApplyWorkoutOutcomeResult.Reduced
            assertEquals(WorkoutOutcomeTransitionResultCode.DUPLICATE, replay.result.code)
            assertEquals(1, replay.result.state.exercises[0].completedSets)

            persistence.write("malformed")
            assertNull(restored.current())
            assertNull(persistence.read())
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
