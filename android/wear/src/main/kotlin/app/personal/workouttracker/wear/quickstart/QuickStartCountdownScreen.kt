package app.personal.workouttracker.wear.quickstart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.wear.ui.WatchAction
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage
import app.personal.workouttracker.wear.ui.LocalWatchPresentationPolicy
import app.personal.workouttracker.wear.ui.ambientBurnInOffset

@Composable
fun QuickStartCountdownScreen(
    viewModel: QuickStartCountdownViewModel,
    onStarted: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val presentation = LocalWatchPresentationPolicy.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) { viewModel.begin(onStarted) }
    LaunchedEffect(presentation.ambient) {
        if (presentation.ambient) viewModel.onLeavingForeground(onCancel)
    }
    BackHandler(enabled = !state.starting) { viewModel.leave(onCancel) }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onLeavingForeground(onCancel)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (presentation.ambient) {
        val offset = if (presentation.burnInProtectionRequired) {
            ambientBurnInOffset(presentation.ambientUpdate)
        } else {
            0 to 0
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = offset.first.dp, y = offset.second.dp)
                .padding(34.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Quick Start paused. Return to begin the countdown again."
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("QUICK START", style = MaterialTheme.typography.caption1)
            Text("Paused", style = MaterialTheme.typography.title3, fontWeight = FontWeight.Bold)
            Text("Return to begin again", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        }
        return
    }

    if (state.loading) return

    state.error?.let { error ->
        WatchPage {
            item { WatchHeading("QUICK START", "Could not start") }
            item { WatchNote(error) }
            item { WatchAction("Back", { viewModel.leave(onCancel) }, primary = true) }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (presentation.showDecorativeProgress) {
            CircularProgressIndicator(
                progress = state.progress,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                strokeWidth = 4.dp,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 34.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (state.starting) {
                        "Go. ${state.exerciseName}. ${state.target}."
                    } else {
                        "Starting in ${state.remainingSeconds}. ${state.exerciseName}. ${state.target}."
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.starting) "GO" else state.remainingSeconds.toString(),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
            )
            Text(
                text = state.exerciseName,
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.target,
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
