package app.personal.workouttracker.wear.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise

/**
 * Focused active-exercise screen for a downloaded workout. One exercise is
 * shown at a time with set progress and direct actions.
 */
@Composable
fun SessionScreen(viewModel: SessionViewModel, onCancel: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val cueAction = rememberCueAction()

    // Exiting without an explicit unfinished state behaves like Pause, so
    // progress is never lost by accident — covers both the system back
    // gesture and the app being backgrounded/closed outright.
    BackHandler(enabled = true) {
        viewModel.onCancel()
        onCancel()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.saveOnExitIfActive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.loading) return // brief DataStore read; nothing meaningful to render yet

    val exercise = state.currentExercise
    val session = state.session

    if (session == null) {
        CompletedView("Workout unavailable")
        return
    }

    if (session.status == SessionStatus.COMPLETED) {
        CompletedView("Workout complete")
        return
    }

    if (session.status == SessionStatus.ENDED) {
        CompletedView("Workout ended")
        return
    }

    if (exercise == null) {
        CompletedView("Workout unavailable")
        return
    }

    if (state.isPaused) {
        PausedView(
            state = state,
            onResume = { cueAction(viewModel::onResume) },
            onRestartWorkout = { cueAction(viewModel::onRestartWorkout) },
            onEndWorkout = { cueAction(viewModel::onEndWorkout) },
            onCancel = { cueAction(onCancel) },
        )
        return
    }

    if (state.isResting) {
        RestingView(
            state = state,
            onStartNow = { cueAction(viewModel::onStartNow) },
            onPause = { cueAction(viewModel::onPause) },
            onAddRestSeconds = { seconds -> cueAction { viewModel.onAddRestSeconds(seconds) } },
            onCancel = { cueAction { cancelSession(viewModel, onCancel) } },
        )
        return
    }

    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            Text(
                text = "ONGOING",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.primary,
            )
        }
        state.entry?.exercises?.firstNotNullOfOrNull { it.questDayLabel }?.let { questDayLabel ->
            item {
                Text(
                    text = questDayLabel,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary,
                )
            }
        }
        item {
            Text(
                text = "Exercise ${session.exerciseIndex + 1} of ${state.totalExercises}",
                style = MaterialTheme.typography.caption1,
            )
        }
        item {
            Text(
                text = "Set ${session.currentSet.coerceAtMost(exercise.sets)} of ${exercise.sets}",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            Text(
                text = exercise.exercise,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = formatExercisePrescription(exercise),
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Chip(
                onClick = { cueAction(viewModel::onCompleteSet) },
                label = { Text("Complete Set") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item {
            CompactChip(
                onClick = { cueAction(viewModel::onPause) },
                label = { Text("Pause") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = { cueAction(viewModel::onSkip) },
                label = { Text("Skip") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = { cueAction(viewModel::onUpgrade) },
                label = { Text("Upgrade (+1 set)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = { cueAction(viewModel::onDowngrade) },
                label = { Text("Downgrade (-1 set)") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = { cueAction { cancelSession(viewModel, onCancel) } },
                label = { Text("Cancel") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun rememberCueAction(): (() -> Unit) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { action ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
}

private fun cancelSession(viewModel: SessionViewModel, onCancel: () -> Unit) {
    viewModel.onCancel()
    onCancel()
}

@Composable
private fun RestingView(
    state: SessionUiState,
    onStartNow: () -> Unit,
    onPause: () -> Unit,
    onAddRestSeconds: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val exercise = state.currentExercise ?: return
    val session = state.session ?: return
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
    ) {
        item {
            Text(
                text = "REST",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.primary,
            )
        }
        state.entry?.exercises?.firstNotNullOfOrNull { it.questDayLabel }?.let { questDayLabel ->
            item {
                Text(
                    text = questDayLabel,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary,
                )
            }
        }
        item {
            Text(
                text = formatRestSeconds(state.restRemainingSeconds),
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Next: exercise ${session.exerciseIndex + 1} of ${state.totalExercises}",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Set ${session.currentSet.coerceAtMost(exercise.sets)} of ${exercise.sets}",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = exercise.exercise,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RestExtensionChip(seconds = 5, onAddRestSeconds = onAddRestSeconds, modifier = Modifier.weight(1f))
                RestExtensionChip(seconds = 10, onAddRestSeconds = onAddRestSeconds, modifier = Modifier.weight(1f))
                RestExtensionChip(seconds = 30, onAddRestSeconds = onAddRestSeconds, modifier = Modifier.weight(1f))
            }
        }
        item {
            Chip(
                onClick = onStartNow,
                label = { Text("Start Now") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item {
            CompactChip(
                onClick = onPause,
                label = { Text("Pause") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = onCancel,
                label = { Text("Cancel") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PausedView(
    state: SessionUiState,
    onResume: () -> Unit,
    onRestartWorkout: () -> Unit,
    onEndWorkout: () -> Unit,
    onCancel: () -> Unit,
) {
    val exercise = state.currentExercise ?: return
    val session = state.session ?: return
    var confirmingEnd by remember { mutableStateOf(false) }
    var confirmingRestart by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
    ) {
        item {
            Text(
                text = when {
                    confirmingEnd -> "END WORKOUT?"
                    confirmingRestart -> "RESTART?"
                    else -> "PAUSED"
                },
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Exercise ${session.exerciseIndex + 1} of ${state.totalExercises}",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Set ${session.currentSet.coerceAtMost(exercise.sets)} of ${exercise.sets}",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = exercise.exercise,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
        session.lastStopReason?.let { reason ->
            item {
                Text(
                    text = formatStopReason(reason),
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        session.pausedRestRemainingSeconds?.let { restSeconds ->
            item {
                Text(
                    text = "Rest paused at ${formatRestSeconds(restSeconds)}",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (confirmingRestart) {
            item {
                Chip(
                    onClick = onRestartWorkout,
                    label = { Text("Restart") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                CompactChip(
                    onClick = { confirmingRestart = false },
                    label = { Text("Keep Paused") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (confirmingEnd) {
            item {
                Chip(
                    onClick = {
                        onEndWorkout()
                        onCancel()
                    },
                    label = { Text("End Workout") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                CompactChip(
                    onClick = { confirmingEnd = false },
                    label = { Text("Keep Paused") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                Chip(
                    onClick = onResume,
                    label = { Text("Resume") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                CompactChip(
                    onClick = { confirmingRestart = true },
                    label = { Text("Restart") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    onClick = { confirmingEnd = true },
                    label = { Text("End Workout") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    onClick = onCancel,
                    label = { Text("Close") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RestExtensionChip(
    seconds: Int,
    onAddRestSeconds: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactChip(
        onClick = { onAddRestSeconds(seconds) },
        label = { Text("+${seconds}s") },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = modifier,
    )
}

@Composable
private fun CompletedView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
    }
}

private fun formatRestSeconds(seconds: Int): String {
    val boundedSeconds = seconds.coerceAtLeast(0)
    val minutes = boundedSeconds / 60
    val remainder = boundedSeconds % 60
    return "%d:%02d".format(minutes, remainder)
}

private fun formatElapsedSeconds(seconds: Int): String {
    val boundedSeconds = seconds.coerceAtLeast(0)
    val hours = boundedSeconds / 3_600
    val minutes = (boundedSeconds % 3_600) / 60
    val remainder = boundedSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

private fun formatExercisePrescription(exercise: WorkoutExercise): String {
    val loadWeight = exercise.loadWeight
    val loadUnit = exercise.loadUnit
    val load = if (loadWeight != null && !loadUnit.isNullOrBlank()) {
        " · ${formatLoadWeight(loadWeight)} $loadUnit"
    } else {
        ""
    }
    return "${exercise.sets} sets · ${exercise.reps} reps$load · ${exercise.rest}s rest"
}

private fun formatLoadWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toInt().toString() else "%.1f".format(weight)

private fun formatStopReason(reason: String): String = when (reason) {
    "paused_by_user" -> "Paused by user"
    "app_closed" -> "Paused after close"
    "unexpected_interruption" -> "Paused after interruption"
    else -> "Paused"
}
