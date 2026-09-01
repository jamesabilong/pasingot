package app.personal.workouttracker.wear.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.wear.data.EntryDisplayStatus
import app.personal.workouttracker.wear.data.displayStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Prompt 5: list of downloaded workout sets. Empty state, per-entry
 * Start/Resume, and a "⋮"-style secondary action revealing Reset/Delete.
 */
@Composable
fun WorkoutListScreen(
    viewModel: WorkoutListViewModel,
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val listState = rememberScalingLazyListState()

    if (entries.isEmpty() && downloadError == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Workouts",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CompactChip(
                onClick = viewModel::downloadNow,
                label = { Text("Download Now") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            CompactChip(
                onClick = onOpenSettings,
                label = { Text("Auto-download settings") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No workouts downloaded — tap to download",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "Workouts",
                style = MaterialTheme.typography.title3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        if (downloadError != null) {
            item {
                Text(
                    text = downloadError ?: "",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            CompactChip(
                onClick = viewModel::downloadNow,
                label = { Text("Download Now") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = onOpenSettings,
                label = { Text("Auto-download settings") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = "No workouts downloaded — tap to download",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.82f).padding(top = 4.dp),
                )
            }
        } else {
            items(entries) { entry ->
                WorkoutRow(
                    entry = entry,
                    onStartOrResume = { onOpenEntry(entry.id) },
                    onReset = { viewModel.reset(entry.id) },
                    onDelete = { viewModel.delete(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun WorkoutRow(
    entry: DownloadedWorkoutEntry,
    onStartOrResume: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val status = entry.displayStatus()
    val hasProgress = status == EntryDisplayStatus.IN_PROGRESS ||
        status == EntryDisplayStatus.PAUSED ||
        status == EntryDisplayStatus.COMPLETED

    Column(modifier = Modifier.fillMaxWidth()) {
        Chip(
            onClick = {
                // Stale entries (schema mismatch) can't be started/resumed —
                // only Delete (via the ⋮ menu) is offered (Prompt 5 req 5).
                if (status != EntryDisplayStatus.STALE) onStartOrResume()
            },
            label = { Text(formatDateLabel(entry.date)) },
            secondaryLabel = { Text(status.label) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        CompactChip(
            onClick = { showActions = !showActions },
            label = { Text("⋮") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showActions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasProgress) {
                    CompactChip(
                        onClick = { onReset(); showActions = false },
                        label = { Text("Reset") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CompactChip(
                    onClick = { onDelete(); showActions = false },
                    label = { Text("Delete") },
                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatDateLabel(dateKey: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
    SimpleDateFormat("EEE, MMM d", Locale.US).format(parsed!!)
} catch (e: Exception) {
    dateKey
}
