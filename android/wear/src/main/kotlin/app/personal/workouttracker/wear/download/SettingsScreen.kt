package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.wear.data.ScheduledDownloadTime
import app.personal.workouttracker.wear.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Prompt 5 req 2: pick the daily auto-download time. */
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

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val time by viewModel.time.collectAsState()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
        item {
            Text(
                text = "Auto-download at",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "%02d:%02d".format(time.hour, time.minute),
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { viewModel.setHour(time.hour - 1) }, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("H-")
                }
                Button(onClick = { viewModel.setHour(time.hour + 1) }, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("H+")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { viewModel.setMinute(time.minute - 5) }, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("M-")
                }
                Button(onClick = { viewModel.setMinute(time.minute + 5) }, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("M+")
                }
            }
        }
        item {
            Chip(
                onClick = {},
                label = { Text("Saved automatically") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
