package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.wear.data.ScheduledDownloadTime
import app.personal.workouttracker.wear.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Persists the daily download time and reschedules its background worker. */
class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _time = MutableStateFlow(ScheduledDownloadTime.DEFAULT)
    val time: StateFlow<ScheduledDownloadTime> = _time.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.scheduledTime.collect { _time.value = it }
        }
    }

    fun setHour(hour: Int) = update(_time.value.copy(hour = hour.mod(24)))
    fun setMinute(minute: Int) = update(_time.value.copy(minute = minute.mod(60)))

    private fun update(newTime: ScheduledDownloadTime) {
        _time.value = newTime
        viewModelScope.launch {
            settingsRepository.setScheduledTime(newTime)
            ScheduleDownloadWorker.enqueueNext(appContext, newTime)
        }
    }

    class Factory(
        private val appContext: Context,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(appContext, settingsRepository) as T
    }
}

