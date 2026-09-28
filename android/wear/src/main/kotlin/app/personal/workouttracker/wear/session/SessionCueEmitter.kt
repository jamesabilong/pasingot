package app.personal.workouttracker.wear.session

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.exerciseDisplayName
import app.personal.workouttracker.shared.session.ExerciseOutcomeStatus
import app.personal.workouttracker.wear.cues.WatchCueCancellation
import app.personal.workouttracker.wear.cues.WatchCueController
import app.personal.workouttracker.wear.cues.WatchCueEvent
import app.personal.workouttracker.wear.cues.WatchCueKind
import app.personal.workouttracker.wear.cues.WatchCueScripts

interface SessionCueEmitter {
    suspend fun restStarted(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        prescribedRestSeconds: Int,
        previousExerciseIndex: Int,
    )
    suspend fun fiveSeconds(entry: DownloadedWorkoutEntry, session: SessionState)
    suspend fun go(entry: DownloadedWorkoutEntry, session: SessionState, restDeadlineMillis: Long)
    suspend fun exerciseSuccess(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        prescribedRestSeconds: Int,
        previousExerciseIndex: Int,
    )
    suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState)
    suspend fun cancel(reason: WatchCueCancellation)
    fun close()
}

object NoOpSessionCueEmitter : SessionCueEmitter {
    override suspend fun restStarted(entry: DownloadedWorkoutEntry, session: SessionState,
        prescribedRestSeconds: Int, previousExerciseIndex: Int) = Unit
    override suspend fun fiveSeconds(entry: DownloadedWorkoutEntry, session: SessionState) = Unit
    override suspend fun go(entry: DownloadedWorkoutEntry, session: SessionState,
        restDeadlineMillis: Long) = Unit
    override suspend fun exerciseSuccess(entry: DownloadedWorkoutEntry, session: SessionState,
        prescribedRestSeconds: Int, previousExerciseIndex: Int) = Unit
    override suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState) = Unit
    override suspend fun cancel(reason: WatchCueCancellation) = Unit
    override fun close() = Unit
}

class ControllerSessionCueEmitter(
    private val controller: WatchCueController,
) : SessionCueEmitter {
    override suspend fun restStarted(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        prescribedRestSeconds: Int,
        previousExerciseIndex: Int,
    ) {
        val deadline = session.restUntilEpochMillis ?: return
        val next = entry.exercises.getOrNull(session.exerciseIndex)
        val changedExercise = session.exerciseIndex != previousExerciseIndex
        val script = WatchCueScripts.rest(
            prescribedRestSeconds,
            next?.takeIf { changedExercise }?.let { exerciseDisplayName(it.exercise) },
            next?.takeIf { changedExercise }?.reps,
        ) ?: return
        controller.emit(
            event(entry, session, WatchCueKind.REST, deadline),
            script,
        )
    }

    override suspend fun fiveSeconds(entry: DownloadedWorkoutEntry, session: SessionState) {
        val deadline = session.restUntilEpochMillis ?: return
        controller.emit(
            event(entry, session, WatchCueKind.FIVE_SECONDS, deadline),
            WatchCueScripts.FIVE_SECONDS,
        )
    }

    override suspend fun go(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        restDeadlineMillis: Long,
    ) {
        controller.cancel(WatchCueCancellation.START_NOW)
        val exercise = entry.exercises.getOrNull(session.exerciseIndex)
        val changedExercise = session.exerciseIndex > 0 && session.currentSet == 1
        controller.emit(
            event(entry, session, WatchCueKind.GO, restDeadlineMillis),
            WatchCueScripts.go(
                exercise?.takeIf { changedExercise }?.let { exerciseDisplayName(it.exercise) },
                session.currentSet.takeUnless { changedExercise },
            ),
        )
    }

    override suspend fun exerciseSuccess(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        prescribedRestSeconds: Int,
        previousExerciseIndex: Int,
    ) {
        val deadline = session.restUntilEpochMillis
        val restScript = if (deadline != null) {
            val next = entry.exercises.getOrNull(session.exerciseIndex)
            WatchCueScripts.rest(
                prescribedRestSeconds,
                next?.let { exerciseDisplayName(it.exercise) },
                next?.reps,
            )
        } else null
        controller.emit(
            successEvent(entry, session, WatchCueKind.EXERCISE_SUCCESS, previousExerciseIndex),
            listOfNotNull(WatchCueScripts.EXERCISE_SUCCESS, restScript).joinToString(" "),
        )
    }

    override suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState) {
        controller.emit(
            successEvent(entry, session, WatchCueKind.WORKOUT_SUCCESS, session.exerciseIndex),
            WatchCueScripts.WORKOUT_SUCCESS,
        )
    }

    override suspend fun cancel(reason: WatchCueCancellation) = controller.cancel(reason)

    override fun close() = controller.dispose()

    private fun event(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        kind: WatchCueKind,
        deadline: Long,
    ) = WatchCueEvent(
        sessionId = entry.id,
        revision = deadline.coerceAtLeast(0),
        exerciseIndex = session.exerciseIndex,
        setIndex = session.currentSet,
        kind = kind,
        thresholdMillis = deadline,
    )

    private fun successEvent(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        kind: WatchCueKind,
        exerciseIndex: Int,
    ): WatchCueEvent {
        val progress = session.progress
        val revision = if (progress == null) 0L else
            progress.completedSets.sumOf(Int::toLong) +
                progress.exerciseStatuses.count { it != ExerciseOutcomeStatus.PENDING }.toLong()
        return WatchCueEvent(
            sessionId = entry.id,
            revision = revision,
            exerciseIndex = exerciseIndex,
            setIndex = session.currentSet,
            kind = kind,
        )
    }
}
