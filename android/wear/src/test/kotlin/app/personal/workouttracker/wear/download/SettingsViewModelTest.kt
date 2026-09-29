package app.personal.workouttracker.wear.download

import androidx.lifecycle.ViewModelStore
import app.personal.workouttracker.wear.cues.VoiceCueAvailability
import app.personal.workouttracker.wear.cues.VoiceCueAvailabilityProbe
import app.personal.workouttracker.wear.cues.WatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCueStore
import app.personal.workouttracker.wear.data.ScheduledDownloadSettings
import app.personal.workouttracker.wear.data.ScheduledDownloadTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test fun `voice prompt is opt in and enables every category`() = runTest(dispatcher) {
        val persistence = MemoryCueSettingsPersistence()
        val viewModel = viewModel(persistence).also { viewModels.put("enable", it) }
        runCurrent()

        assertFalse(viewModel.voice.value.preferences.voiceEnabled)
        assertFalse(viewModel.voice.value.preferences.voicePromptResolved)
        viewModel.enableVoiceFromPrompt()
        runCurrent()

        val preferences = viewModel.voice.value.preferences
        assertTrue(preferences.voiceEnabled)
        assertTrue(preferences.voicePromptResolved)
        assertTrue(preferences.startBriefing)
        assertTrue(preferences.restAnnouncements)
        assertTrue(preferences.countdown)
        assertTrue(preferences.completion)
        assertEquals(preferences, WatchCueStore(persistence).state().preferences)
    }

    @Test fun `not now and category choices survive settings recreation`() = runTest(dispatcher) {
        val persistence = MemoryCueSettingsPersistence()
        val first = viewModel(persistence).also { viewModels.put("first", it) }
        runCurrent()
        first.dismissVoicePrompt()
        first.setVoiceEnabled(true)
        first.setRestAnnouncements(false)
        runCurrent()

        val restored = viewModel(persistence).also { viewModels.put("restored", it) }
        runCurrent()

        assertTrue(restored.voice.value.preferences.voicePromptResolved)
        assertTrue(restored.voice.value.preferences.voiceEnabled)
        assertFalse(restored.voice.value.preferences.restAnnouncements)
        assertEquals(VoiceCueAvailability.UNAVAILABLE, restored.voice.value.availability)
    }

    @Test fun `failed voice write rolls back visible selection and reports error`() = runTest(dispatcher) {
        val persistence = MemoryCueSettingsPersistence(failWrites = true)
        val viewModel = viewModel(persistence).also { viewModels.put("failure", it) }
        runCurrent()

        viewModel.enableVoiceFromPrompt()
        runCurrent()

        assertFalse(viewModel.voice.value.preferences.voiceEnabled)
        assertFalse(viewModel.voice.value.preferences.voicePromptResolved)
        assertEquals("Could not save voice settings", viewModel.voice.value.error)
    }

    private fun viewModel(persistence: MemoryCueSettingsPersistence) = SettingsViewModel(
        settingsRepository = FakeScheduledDownloadSettings(),
        cueStore = WatchCueStore(persistence),
        availabilityProbe = VoiceCueAvailabilityProbe { VoiceCueAvailability.UNAVAILABLE },
        rescheduleDownload = {},
    )

    private class FakeScheduledDownloadSettings : ScheduledDownloadSettings {
        override val scheduledTime = MutableStateFlow(ScheduledDownloadTime.DEFAULT)
        override suspend fun setScheduledTime(time: ScheduledDownloadTime) {
            scheduledTime.value = time
        }
    }

    private class MemoryCueSettingsPersistence(
        private val failWrites: Boolean = false,
    ) : WatchCuePersistence {
        private var raw: String? = null
        override suspend fun read(): String? = raw
        override suspend fun write(value: String) {
            if (failWrites) error("disk unavailable")
            raw = value
        }
    }
}
