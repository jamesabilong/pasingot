package app.personal.workouttracker.wear.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.exerciseDisplayName
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.estimatedDurationSeconds
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.wear.ui.WatchAction
import app.personal.workouttracker.wear.ui.WatchAmbientPlaceholder
import app.personal.workouttracker.wear.ui.WatchHeading
import app.personal.workouttracker.wear.ui.WatchNote
import app.personal.workouttracker.wear.ui.WatchPage
import app.personal.workouttracker.wear.ui.LocalWatchPresentationPolicy
import app.personal.workouttracker.wear.ui.ambientBurnInOffset

private enum class SessionConfirmation { RESTART, END }

/**
 * Focused active-exercise screen for a downloaded workout. One exercise is
 * shown at a time with set progress and direct actions.
 */
@Composable
fun SessionScreen(viewModel: SessionViewModel, onCancel: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val presentation = LocalWatchPresentationPolicy.current
    val cueAction = rememberCueAction()

    // Exiting without an explicit unfinished state behaves like Pause, so
    // progress is never lost by accident — covers both the system back
    // gesture and the app being backgrounded/closed outright.
    BackHandler(enabled = !state.saving) {
        viewModel.onCancel(onCancel)
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

    if (state.loading) {
        if (presentation.ambient) WatchAmbientPlaceholder("Workout", "Loading")
        return
    }

    if (presentation.ambient) {
        if (state.session == null) {
            WatchAmbientPlaceholder("Workout", "Wake to continue")
        } else {
            AmbientSessionView(state)
        }
        return
    }

    state.error?.let { error ->
        WatchPage {
            item { WatchHeading("PROGRESS", "Could not save") }
            item { WatchNote(error) }
            item { WatchAction("Back to workout", viewModel::clearError, primary = true) }
            item { WatchAction("Close workout", onCancel) }
        }
        return
    }

    val exercise = state.currentExercise
    val session = state.session

    if (session == null) {
        CompletedView(state.blockedReason ?: "Workout unavailable", onCancel)
        return
    }

    if (session.status == SessionStatus.COMPLETED) {
        WorkoutSuccessView(state, onCancel)
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
        state.progress?.successExerciseIndex?.let { completedIndex ->
            val completedName = state.entry?.exercises?.getOrNull(completedIndex)?.exercise
            item { ExerciseSuccessHeader(state, completedName?.let(::exerciseDisplayName)) }
        }
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
        if (state.canAdjustSets) {
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

private fun cancelSession(viewModel: SessionViewModel, onCancel: () -> Unit) =
    viewModel.onCancel(onCancel)

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
        if (LocalWatchPresentationPolicy.current.showDecorativeProgress) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                strokeWidth = 3.dp,
            )
        }
        WatchPage {
            state.progress?.successExerciseIndex?.let { completedIndex ->
                val completedName = state.entry?.exercises?.getOrNull(completedIndex)?.exercise
                item { ExerciseSuccessHeader(state, completedName?.let(::exerciseDisplayName)) }
            }
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
                    RestExtensionChip(5, onAddRestSeconds, state.canExtendRest, Modifier.weight(1f))
                    RestExtensionChip(10, onAddRestSeconds, state.canExtendRest, Modifier.weight(1f))
                    RestExtensionChip(30, onAddRestSeconds, state.canExtendRest, Modifier.weight(1f))
                }
            }
            if (!state.canExtendRest) {
                item { WatchNote("Rest extension unavailable during final countdown") }
            }
            item { WatchNote("Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}") }
            item { WatchAction("Save & close", onCancel) }
        }
    }
}

@Composable
private fun ExerciseSuccessHeader(state: SessionUiState, exerciseName: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = progressAccessibilityLabel(state) },
    ) {
        Text("✓", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
        WatchHeading(
            eyebrow = "EXERCISE COMPLETE",
            title = exerciseName ?: "Exercise complete",
            detail = progressLabel(state),
        )
        ProgressCounts(state)
    }
}

@Composable
private fun WorkoutSuccessView(state: SessionUiState, onClose: () -> Unit) {
    val progress = state.progress
    val completedSets = progress?.completedSets?.sum() ?: 0
    val plannedSets = state.entry?.exercises?.sumOf { it.sets } ?: 0
    WatchPage {
        item {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (LocalWatchPresentationPolicy.current.showDecorativeProgress) {
                    CircularProgressIndicator(progress = 1f, modifier = Modifier.padding(4.dp), strokeWidth = 4.dp)
                }
                Text("✓", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
            }
        }
        item { WatchHeading("PASINGOT", "Workout complete", progressLabel(state)) }
        item { ProgressCounts(state) }
        item { WatchNote("Sets $completedSets / $plannedSets") }
        item { WatchNote("Elapsed ${formatElapsedSeconds(state.elapsedSeconds)}") }
        state.entry?.estimatedDurationSeconds()?.takeIf { it > 0 }?.let { estimate ->
            item { WatchNote("Estimated ${formatElapsedSeconds(estimate)}") }
        }
        item {
            WatchNote(if (state.session?.resultSaved == true) "Saved on watch" else "Saving on watch…")
        }
        if (state.awaitingPhoneSync) item { WatchNote("Waiting to sync") }
        item { WatchAction("Back to workouts", onClose, primary = true) }
    }
}

@Composable
private fun AmbientSessionView(state: SessionUiState) {
    val presentation = LocalWatchPresentationPolicy.current
    val session = state.session ?: return
    val exercise = state.currentExercise
    val offset = if (presentation.burnInProtectionRequired) {
        ambientBurnInOffset(presentation.ambientUpdate)
    } else {
        0 to 0
    }
    val title = when (session.status) {
        SessionStatus.COMPLETED -> "Workout complete"
        SessionStatus.ENDED -> "Workout ended"
        SessionStatus.RESTING -> formatRestSeconds(state.restRemainingSeconds)
        SessionStatus.PAUSED -> "Paused"
        else -> exercise?.let { exerciseDisplayName(it.exercise) } ?: "Workout"
    }
    val detail = when (session.status) {
        SessionStatus.COMPLETED, SessionStatus.ENDED -> progressLabel(state)
        SessionStatus.RESTING -> exercise?.let { "Next · ${exerciseDisplayName(it.exercise)}" } ?: "Rest"
        else -> exercise?.let { "Set ${session.currentSet} / ${it.sets}" } ?: progressLabel(state)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = offset.first.dp, y = offset.second.dp)
            .padding(34.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail. ${progressAccessibilityLabel(state)}"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PASINGOT", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        Text(title, style = MaterialTheme.typography.title3, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProgressCounts(state: SessionUiState) {
    val statuses = state.progress?.exerciseStatuses.orEmpty()
    val completed = statuses.count { it == ExerciseOutcomeStatus.COMPLETED }
    val skipped = statuses.count { it == ExerciseOutcomeStatus.SKIPPED }
    val pending = statuses.count { it == ExerciseOutcomeStatus.PENDING }
    WatchNote("Completed $completed · Skipped $skipped · Pending $pending")
}

private fun progressLabel(state: SessionUiState): String {
    val statuses = state.progress?.exerciseStatuses.orEmpty()
    val completed = statuses.count { it == ExerciseOutcomeStatus.COMPLETED }
    return "$completed/${statuses.size.coerceAtLeast(state.totalExercises)} completed"
}

private fun progressAccessibilityLabel(state: SessionUiState): String {
    val statuses = state.progress?.exerciseStatuses.orEmpty()
    val completed = statuses.count { it == ExerciseOutcomeStatus.COMPLETED }
    val total = statuses.size.coerceAtLeast(state.totalExercises)
    return "$completed of $total exercises completed"
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
            if (state.canRestart) {
                item { WatchAction("Restart", { confirmation = SessionConfirmation.RESTART }) }
            }
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    CompactChip(
        onClick = { onAddRestSeconds(seconds) },
        enabled = enabled,
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
