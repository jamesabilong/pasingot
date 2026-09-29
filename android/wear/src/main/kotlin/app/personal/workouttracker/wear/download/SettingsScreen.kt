package app.personal.workouttracker.wear.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import app.personal.workouttracker.wear.cues.VoiceCueAvailability
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage
import app.personal.workouttracker.wear.ui.LocalWatchPresentationPolicy
import app.personal.workouttracker.wear.ui.WatchAmbientPlaceholder

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val time by viewModel.time.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()

    if (LocalWatchPresentationPolicy.current.ambient) {
        WatchAmbientPlaceholder("Settings", "Wake to continue")
        return
    }

    WatchPage {
        item {
            WatchHeading(
                eyebrow = "VOICE CUES",
                title = if (voice.preferences.voiceEnabled) "Enabled" else "Off",
                detail = voice.availability.detail,
            )
        }
        voice.availability.note?.let { note ->
            item { WatchNote("$note · Visual cues and haptics stay active") }
        }
        voice.error?.let { error -> item { WatchNote(error) } }
        item {
            VoiceSettingToggle(
                label = "Voice cues",
                checked = voice.preferences.voiceEnabled,
                enabled = !voice.loading,
                onCheckedChange = viewModel::setVoiceEnabled,
            )
        }
        item {
            VoiceSettingToggle(
                label = "Start briefing",
                checked = voice.preferences.startBriefing,
                enabled = !voice.loading && voice.preferences.voiceEnabled,
                onCheckedChange = viewModel::setStartBriefing,
            )
        }
        item {
            VoiceSettingToggle(
                label = "Rest announcements",
                checked = voice.preferences.restAnnouncements,
                enabled = !voice.loading && voice.preferences.voiceEnabled,
                onCheckedChange = viewModel::setRestAnnouncements,
            )
        }
        item {
            VoiceSettingToggle(
                label = "Countdown cue",
                checked = voice.preferences.countdown,
                enabled = !voice.loading && voice.preferences.voiceEnabled,
                onCheckedChange = viewModel::setCountdown,
            )
        }
        item {
            VoiceSettingToggle(
                label = "Completion cue",
                checked = voice.preferences.completion,
                enabled = !voice.loading && voice.preferences.voiceEnabled,
                onCheckedChange = viewModel::setCompletion,
            )
        }
        item {
            WatchHeading(
                eyebrow = "DAILY DOWNLOAD",
                title = "%02d:%02d".format(time.hour, time.minute),
                detail = "24-hour time",
            )
        }
        item { WatchNote("Hour") }
        item {
            TimeAdjustmentRow(
                unit = "hour",
                onDecrease = { viewModel.setHour(time.hour - 1) },
                onIncrease = { viewModel.setHour(time.hour + 1) },
            )
        }
        item { WatchNote("Minute · 5 min steps") }
        item {
            TimeAdjustmentRow(
                unit = "minute",
                onDecrease = { viewModel.setMinute(time.minute - 5) },
                onIncrease = { viewModel.setMinute(time.minute + 5) },
            )
        }
        item { WatchNote("Saved automatically") }
    }
}

private val VoiceCueAvailability.detail: String
    get() = when (this) {
        VoiceCueAvailability.CHECKING -> "Checking system voice"
        VoiceCueAvailability.ENGINE_AVAILABLE -> "Checked when a workout starts"
        VoiceCueAvailability.AVAILABLE -> "System voice ready"
        VoiceCueAvailability.SERVICE_UNAVAILABLE -> "No system voice service"
        VoiceCueAvailability.INITIALIZATION_FAILED -> "System voice did not start"
        VoiceCueAvailability.LANGUAGE_UNAVAILABLE -> "Current language unavailable"
        VoiceCueAvailability.AUDIO_OUTPUT_UNAVAILABLE -> "No audio output"
        VoiceCueAvailability.AUDIO_FOCUS_UNAVAILABLE -> "Audio is busy"
    }

private val VoiceCueAvailability.note: String?
    get() = when (this) {
        VoiceCueAvailability.SERVICE_UNAVAILABLE -> "Voice service unavailable"
        VoiceCueAvailability.INITIALIZATION_FAILED -> "Voice initialization failed"
        VoiceCueAvailability.LANGUAGE_UNAVAILABLE -> "Voice language unavailable"
        VoiceCueAvailability.AUDIO_OUTPUT_UNAVAILABLE -> "Voice audio unavailable"
        VoiceCueAvailability.AUDIO_FOCUS_UNAVAILABLE -> "Voice temporarily unavailable"
        else -> null
    }

@Composable
private fun VoiceSettingToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label) },
        toggleControl = { Switch(checked = checked) },
        enabled = enabled,
        colors = ToggleChipDefaults.toggleChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeAdjustmentRow(
    unit: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Button(
            onClick = onDecrease,
            modifier = Modifier.semantics { contentDescription = "Decrease $unit" },
            colors = ButtonDefaults.secondaryButtonColors(),
        ) {
            Text("−")
        }
        Button(
            onClick = onIncrease,
            modifier = Modifier.semantics { contentDescription = "Increase $unit" },
            colors = ButtonDefaults.secondaryButtonColors(),
        ) {
            Text("+")
        }
    }
}
