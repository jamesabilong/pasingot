package app.personal.workouttracker.wear.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.SessionStatus

/**
 * Focused active-exercise screen for a downloaded workout. One exercise is
 * shown at a time with its prescription and four direct actions.
 */
@Composable
fun SessionScreen(viewModel: SessionViewModel, onCancel: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

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

    if (exercise == null || session == null || session.status == SessionStatus.COMPLETED) {
        CompletedView()
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
                text = exercise.exercise,
                style = MaterialTheme.typography.title3,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            Text(
                text = "${exercise.sets} sets · ${exercise.reps} reps · ${exercise.rest}s rest",
                style = MaterialTheme.typography.body1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            Chip(
                onClick = viewModel::onDone,
                label = { Text("Done") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item {
            CompactChip(
                onClick = viewModel::onUpgrade,
                label = { Text("Upgrade (+1 set)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = viewModel::onDowngrade,
                label = { Text("Downgrade (-1 set)") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CompactChip(
                onClick = { viewModel.onCancel(); onCancel() },
                label = { Text("Cancel") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompletedView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Workout complete", style = MaterialTheme.typography.title3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
