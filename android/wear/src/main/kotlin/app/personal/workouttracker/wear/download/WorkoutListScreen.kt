package app.personal.workouttracker.wear.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.EntryDisplayStatus
import app.personal.workouttracker.shared.displayStatus
import app.personal.workouttracker.shared.estimatedDurationSeconds
import app.personal.workouttracker.shared.formatEstimatedDuration
import app.personal.workouttracker.wear.ui.WatchAction
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage
import java.text.SimpleDateFormat
import java.util.Locale

private enum class WorkoutConfirmation { RESET, DELETE }

/** Saved workouts with download controls and explicit reset/delete confirmation. */
@Composable
fun WorkoutListScreen(
    viewModel: WorkoutListViewModel,
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val syncing by viewModel.syncing.collectAsState()

    WatchPage {
        item {
            WatchHeading(
                eyebrow = "PASINGOT",
                title = "Workouts",
                detail = if (entries.isEmpty()) "Your next session starts here" else "${entries.size} saved on watch",
            )
        }
        item {
            WatchAction(
                label = if (syncing) "Syncing…" else "Sync from phone",
                onClick = viewModel::downloadNow,
                primary = true,
                enabled = !syncing,
            )
        }
        feedback?.let { result ->
            item {
                Text(
                    text = result.message,
                    color = if (result.error) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            WorkoutRow(
                entry = entry,
                onStartOrResume = { onOpenEntry(entry.id) },
                onReset = { viewModel.reset(entry.id) },
                onDelete = { viewModel.delete(entry.id) },
            )
        }
        if (entries.isEmpty()) {
            item { WatchNote("Connect your phone to download a workout.") }
        }
        item { WatchAction("Schedule", onOpenSettings) }
    }
}

@Composable
private fun WorkoutRow(
    entry: DownloadedWorkoutEntry,
    onStartOrResume: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by remember(entry.id) { mutableStateOf(false) }
    var confirmation by remember(entry.id) { mutableStateOf<WorkoutConfirmation?>(null) }
    val status = entry.displayStatus()
    val canResume = status == EntryDisplayStatus.IN_PROGRESS ||
        status == EntryDisplayStatus.RESTING || status == EntryDisplayStatus.PAUSED
    val canReset = canResume || status == EntryDisplayStatus.COMPLETED || status == EntryDisplayStatus.ENDED
    val title = entry.exercises.firstNotNullOfOrNull { it.questDayLabel } ?: formatDateLabel(entry.date)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (confirmation != null) {
            WatchHeading(
                eyebrow = "CONFIRM",
                title = if (confirmation == WorkoutConfirmation.DELETE) "Delete workout?" else "Reset progress?",
            )
            WatchNote(
                if (confirmation == WorkoutConfirmation.DELETE) {
                    "Remove this download from your watch."
                } else {
                    "Start this download from set one."
                }
            )
            WatchAction(
                label = "Confirm",
                onClick = {
                    if (confirmation == WorkoutConfirmation.DELETE) onDelete() else onReset()
                    confirmation = null
                    showActions = false
                },
            )
            WatchAction("Keep workout", { confirmation = null })
        } else {
            Chip(
                onClick = onStartOrResume,
                enabled = status != EntryDisplayStatus.STALE,
                label = { Text(title) },
                secondaryLabel = {
                    Text("${if (canResume) "Resume" else status.label} · ${entry.exercises.size} exercises")
                },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            WatchNote(formatEstimatedDuration(entry.estimatedDurationSeconds()))
            CompactChip(
                onClick = { showActions = !showActions },
                label = { Text(if (showActions) "Hide options" else "Options") },
            )
            if (showActions) {
                if (canReset) {
                    WatchAction("Reset progress", { confirmation = WorkoutConfirmation.RESET })
                }
                WatchAction("Delete download", { confirmation = WorkoutConfirmation.DELETE })
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
