package app.personal.workouttracker.wear.cues

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreWatchCuePersistenceTest {
    @Test fun `preferences and ledger survive separate DataStore scopes`() = runTest {
        val file = File.createTempFile("watch-cues", ".preferences_pb").also { it.delete() }
        val event = WatchCueEvent("session", 1, 0, 0, WatchCueKind.GO)
        val firstJob = SupervisorJob()
        try {
            val first = WatchCueStore(DataStoreWatchCuePersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file },
            ))
            val enabled = WatchCuePreferences(voiceEnabled = true, restAnnouncements = false)
            first.setPreferences(enabled)
            assertEquals(ReserveWatchCue.Reserved(enabled), first.reserve(event))
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        try {
            val restored = WatchCueStore(DataStoreWatchCuePersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file },
            ))
            assertEquals(true, restored.state().preferences.voiceEnabled)
            assertEquals(ReserveWatchCue.Duplicate, restored.reserve(event))
            restored.clearAcknowledgedSession(event.sessionId)
        } finally {
            secondJob.cancelAndJoin()
        }

        val thirdJob = SupervisorJob()
        try {
            val acknowledged = WatchCueStore(DataStoreWatchCuePersistence(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(thirdJob + Dispatchers.IO)) { file },
            ))
            assertEquals(true, acknowledged.state().preferences.voiceEnabled)
            assertEquals(null, acknowledged.state().ledgerSessionId)
            assertEquals(ReserveWatchCue.Duplicate, acknowledged.reserve(event))
            val success = event.copy(kind = WatchCueKind.WORKOUT_SUCCESS)
            assertEquals(ReserveWatchCue.Reserved(acknowledged.state().preferences), acknowledged.reserve(success))
            assertEquals(ReserveWatchCue.Duplicate, acknowledged.reserve(success))
            assertEquals(emptyList<String>(), acknowledged.state().ledger.deliveredKeys)
        } finally {
            thirdJob.cancelAndJoin()
            file.delete()
        }
    }
}
