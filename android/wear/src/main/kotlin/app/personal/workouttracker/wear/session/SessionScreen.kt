package app.personal.workouttracker.wear.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.exerciseDisplayName
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.wear.ui.WatchAction
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage

private enum class SessionConfirmation { RESTART, END }

/**
 * Focused active-exercise screen for a downloaded workout. One exercise is
 * shown at a time with set progress and direct actions.
 */
@Composable
fun SessionScreen(viewModel: SessionViewModel, onCancel: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val cueAction = rememberCueAction()

    // Exiting without an explicit unfinished state behaves like Pause, so
    // progress is never lost by accident — covers both the system back
    // gesture and the app being backgrounded/closed outright.
    BackHandler(enabled = true) {
        viewModel.onCancel()
        onCancel()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenVisibilityChanged(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenVisibilityChanged(false)
                Lifecycle.Event.ON_STOP -> viewModel.saveOnExitIfActive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onScreenVisibilityChanged(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            viewModel.onScreenVisibilityChanged(false)
            viewModel.saveOnExitIfActive()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (state.loading) return // brief DataStore read; nothing meaningful to render yet

    val exercise = state.currentExercise
    val session = state.session

    if (session == null) {
        CompletedView("Workout unavailable", onCancel)
        return
    }

    if (session.status == SessionStatus.COMPLETED) {
        CompletedView("Workout complete", onCancel)
        return
    }

    if (session.status == SessionStatus.ENDED) {
        CompletedView("Workout ended", onCancel)
        return
    }

    if (exercise == null) {
        CompletedView("Workout unavailable", onCancel)
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

    WatchPage {
        item {
            WatchHeading(
                eyebrow = "SET ${session.currentSet} / ${exercise.sets}",
                title = exerciseDisplayName(exercise.exercise),
                detail = "Exercise ${session.exerciseIndex + 1} of ${state.totalExercises}",
            )
        }
        item {
            Text(
                text = formatSetTarget(exercise.reps),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            WatchAction("Complete set", { cueAction(viewModel::onCompleteSet) }, primary = true)
        }
        item { WatchAction("Pause", { cueAction(viewModel::onPause) }) }
        item { WatchNote(formatExercisePrescription(exercise)) }
        item { WatchNote("Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}") }
        state.entry?.exercises?.firstNotNullOfOrNull { it.questDayLabel }?.let { label ->
            item { WatchNote(label) }
        }
        item { WatchAction("Skip exercise", { cueAction(viewModel::onSkip) }) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactChip(
                    onClick = { cueAction(viewModel::onDowngrade) },
                    label = { Text("− Set") },
                    modifier = Modifier.weight(1f),
                )
                CompactChip(
                    onClick = { cueAction(viewModel::onUpgrade) },
                    label = { Text("+ Set") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { WatchAction("Save & close", { cueAction { cancelSession(viewModel, onCancel) } }) }
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
    val plannedRestSeconds = exercise.rest.coerceAtLeast(1)
    val progress = (state.restRemainingSeconds.toFloat() / plannedRestSeconds).coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            strokeWidth = 3.dp,
        )
        WatchPage {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WatchNote("REST · SET ${session.currentSet} / ${exercise.sets}")
                    Text(
                        text = formatRestSeconds(state.restRemainingSeconds),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    )
                    Text(
                        text = exerciseDisplayName(exercise.exercise),
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { WatchAction("Start now", onStartNow, primary = true) }
            item { WatchAction("Pause", onPause) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RestExtensionChip(5, onAddRestSeconds, Modifier.weight(1f))
                    RestExtensionChip(10, onAddRestSeconds, Modifier.weight(1f))
                    RestExtensionChip(30, onAddRestSeconds, Modifier.weight(1f))
                }
            }
            item { WatchNote("Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}") }
            item { WatchAction("Save & close", onCancel) }
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
    var confirmation by remember { mutableStateOf<SessionConfirmation?>(null) }

    WatchPage {
        item {
            WatchHeading(
                eyebrow = if (confirmation != null) "CONFIRM" else "PAUSED",
                title = when (confirmation) {
                    SessionConfirmation.END -> "End workout?"
                    SessionConfirmation.RESTART -> "Start over?"
                    null -> exerciseDisplayName(exercise.exercise)
                },
                detail = "Set ${session.currentSet} / ${exercise.sets} · ${formatElapsedSeconds(state.elapsedSeconds)} elapsed",
            )
        }
        if (confirmation != null) {
            val restarting = confirmation == SessionConfirmation.RESTART
            item {
                WatchNote(if (restarting) "Return to the first set." else "Finished exercises stay logged.")
            }
            item {
                WatchAction(
                    label = if (restarting) "Restart workout" else "End workout",
                    onClick = if (restarting) onRestartWorkout else onEndWorkout,
                    primary = true,
                )
            }
            item { WatchAction("Keep paused", { confirmation = null }) }
        } else {
            item { WatchAction("Resume", onResume, primary = true) }
            session.pausedRestRemainingSeconds?.let { seconds ->
                item { WatchNote("${formatRestSeconds(seconds)} rest remaining") }
            }
            item { WatchAction("Save & close", onCancel) }
            item { WatchAction("Restart", { confirmation = SessionConfirmation.RESTART }) }
            item { WatchAction("End workout", { confirmation = SessionConfirmation.END }) }
            session.lastStopReason?.let { reason ->
                item { WatchNote(formatStopReason(reason)) }
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
private fun CompletedView(message: String, onClose: () -> Unit) {
    WatchPage {
        item { WatchHeading("PASINGOT", message) }
        item {
            WatchNote(
                if (message == "Workout complete") "All sets finished. Nice work." else "Return to your saved workouts."
            )
        }
        item { WatchAction("Back to workouts", onClose, primary = true) }
    }
}

private fun formatSetTarget(reps: String): String =
    if (reps.all { it.isDigit() || it in " -–" }) "$reps reps" else reps

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
    return "${exercise.sets} sets$load · ${exercise.rest}s rest"
}

private fun formatLoadWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toInt().toString() else "%.1f".format(weight)

private fun formatStopReason(reason: String): String = when (reason) {
    "paused_by_user" -> "Paused by user"
    "app_closed" -> "Paused after close"
    "unexpected_interruption" -> "Paused after interruption"
    else -> "Paused"
}
