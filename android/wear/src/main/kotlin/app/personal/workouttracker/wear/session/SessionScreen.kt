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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.SessionStatus

/**
 * Prompt 4: the active-session screen for a workout already in progress.
 * Round-screen-optimized via ScalingLazyColumn; exactly four in-session
 * actions (Next Exercise, Complete Set, Skip, Pause) — no Reset here.
 */
@Composable
fun SessionScreen(viewModel: SessionViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Exiting without an explicit unfinished state behaves like Pause, so
    // progress is never lost by accident — covers both the system back
    // gesture and the app being backgrounded/closed outright.
    BackHandler(enabled = true) {
        viewModel.saveOnExitIfActive()
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
                text = "Set ${session.currentSet} of ${exercise.sets}",
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
            // reps kept as text — may be a range like "8-12", never parsed as Int.
            Text(text = "${exercise.reps} reps", style = MaterialTheme.typography.body1)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = viewModel::onCompleteSet, colors = ButtonDefaults.primaryButtonColors()) {
                    Text("✓", style = MaterialTheme.typography.button)
                }
                Button(onClick = viewModel::onSkip, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("Skip", style = MaterialTheme.typography.button)
                }
            }
        }
        item {
            CompactChip(
                onClick = viewModel::onNextExercise,
                label = { Text("Next Exercise") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            // Reachable without extra taps, but visually de-emphasized —
            // Complete Set/Skip are the primary actions per Prompt 4 req 4.
            CompactChip(
                onClick = viewModel::onPause,
                label = { Text("Pause") },
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
