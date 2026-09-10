package app.personal.workouttracker.wear.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val time by viewModel.time.collectAsState()

    WatchPage {
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
