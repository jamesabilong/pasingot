package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.wear.cues.AndroidVoiceCueAvailabilityProbe
import app.personal.workouttracker.wear.cues.DataStoreWatchCuePersistence
import app.personal.workouttracker.wear.cues.VoiceCueAvailability
import app.personal.workouttracker.wear.cues.VoiceCueAvailabilityProbe
import app.personal.workouttracker.wear.cues.VoiceCueAvailabilityRegistry
import app.personal.workouttracker.wear.cues.WatchCuePreferences
import app.personal.workouttracker.wear.cues.WatchCueStore
import app.personal.workouttracker.wear.data.ScheduledDownloadSettings
import app.personal.workouttracker.wear.data.ScheduledDownloadTime
import app.personal.workouttracker.wear.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VoiceCueSettingsUiState(
    val preferences: WatchCuePreferences = WatchCuePreferences(),
    val availability: VoiceCueAvailability = VoiceCueAvailability.CHECKING,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Persists the daily download time and reschedules its background worker. */
class SettingsViewModel(
    private val settingsRepository: ScheduledDownloadSettings,
    private val cueStore: WatchCueStore,
    availabilityProbe: VoiceCueAvailabilityProbe,
    availabilityUpdates: StateFlow<VoiceCueAvailability>? = null,
    private val rescheduleDownload: suspend (ScheduledDownloadTime) -> Unit,
) : ViewModel() {

    private val _time = MutableStateFlow(ScheduledDownloadTime.DEFAULT)
    val time: StateFlow<ScheduledDownloadTime> = _time.asStateFlow()
    private val _voice = MutableStateFlow(VoiceCueSettingsUiState(
        availability = runCatching { availabilityProbe.check() }
            .getOrDefault(VoiceCueAvailability.SERVICE_UNAVAILABLE),
    ))
    val voice: StateFlow<VoiceCueSettingsUiState> = _voice.asStateFlow()
    private val voiceMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepository.scheduledTime.collect { _time.value = it }
        }
        viewModelScope.launch {
            try {
                _voice.value = _voice.value.copy(
                    preferences = cueStore.state().preferences,
                    loading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _voice.value = _voice.value.copy(
                    loading = false,
                    error = "Could not load voice settings",
                )
            }
        }
        if (availabilityUpdates != null) {
            viewModelScope.launch {
                availabilityUpdates.collect { availability ->
                    _voice.value = _voice.value.copy(availability = availability)
                }
            }
        }
    }

    fun setHour(hour: Int) = update(_time.value.copy(hour = hour.mod(24)))
    fun setMinute(minute: Int) = update(_time.value.copy(minute = minute.mod(60)))

    fun enableVoiceFromPrompt() = updateVoice {
        WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true)
    }

    fun dismissVoicePrompt() = updateVoice {
        it.copy(voiceEnabled = false, voicePromptResolved = true)
    }

    fun setVoiceEnabled(enabled: Boolean) = updateVoice {
        it.copy(voiceEnabled = enabled, voicePromptResolved = true)
    }

    fun setStartBriefing(enabled: Boolean) = updateVoice { it.copy(startBriefing = enabled) }
    fun setRestAnnouncements(enabled: Boolean) = updateVoice { it.copy(restAnnouncements = enabled) }
    fun setCountdown(enabled: Boolean) = updateVoice { it.copy(countdown = enabled) }
    fun setCompletion(enabled: Boolean) = updateVoice { it.copy(completion = enabled) }

    private fun update(newTime: ScheduledDownloadTime) {
        _time.value = newTime
        viewModelScope.launch {
            settingsRepository.setScheduledTime(newTime)
            rescheduleDownload(newTime)
        }
    }

    private fun updateVoice(transform: (WatchCuePreferences) -> WatchCuePreferences) {
        viewModelScope.launch {
            voiceMutex.withLock {
                val previous = _voice.value.preferences
                val updated = transform(previous)
                _voice.value = _voice.value.copy(preferences = updated, error = null)
                try {
                    cueStore.setPreferences(updated)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    _voice.value = _voice.value.copy(
                        preferences = previous,
                        error = "Could not save voice settings",
                    )
                }
            }
        }
    }

    class Factory(
        private val appContext: Context,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AndroidVoiceCueAvailabilityProbe(appContext).let { availabilityProbe ->
                VoiceCueAvailabilityRegistry.reportDiscovery(availabilityProbe.check())
                SettingsViewModel(
                    settingsRepository,
                    WatchCueStore(DataStoreWatchCuePersistence(appContext)),
                    availabilityProbe,
                    VoiceCueAvailabilityRegistry.availability,
                    rescheduleDownload = { ScheduleDownloadWorker.enqueueNext(appContext, it) },
                ) as T
            }
    }
}
