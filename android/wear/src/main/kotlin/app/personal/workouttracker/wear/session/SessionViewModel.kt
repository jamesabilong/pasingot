package app.personal.workouttracker.wear.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionEventType
import app.personal.workouttracker.shared.SessionStopReason
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.shared.estimatedDurationSeconds
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** Everything [SessionScreen] needs to render one frame. */
data class SessionUiState(
    val entry: DownloadedWorkoutEntry? = null,
    val session: SessionState? = null,
    val restRemainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val loading: Boolean = true,
) {
    val currentExercise get() = entry?.exercises?.getOrNull(session?.exerciseIndex ?: 0)
    val totalExercises get() = entry?.exercises?.size ?: 0
    val isResting get() = session?.status == SessionStatus.RESTING
    val isPaused get() = session?.status == SessionStatus.PAUSED
}

/**
 * Backs the active session screen (Prompt 4). Reads/writes [SessionState]
 * scoped to one [DownloadedWorkoutEntry] — never a single global session,
 * since several workouts can be cached at once (Prompt 5).
 */
class SessionViewModel(
    private val entryId: String,
    private val repository: WorkoutRepository,
    private val logSender: LogSender,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private var restTimerJob: Job? = null

    init {
        viewModelScope.launch {
            val entry = repository.getEntry(entryId)
            // Resume in place if a SessionState already exists, else start
            // fresh at exerciseIndex = 0 (Prompt 4 req 3).
            val storedSession = entry?.sessionState
            val session = storedSession ?: newSession()
            _uiState.value = SessionUiState(
                entry = entry,
                session = session,
                elapsedSeconds = elapsedSeconds(session),
                loading = false,
            )
            synchronizeRestTimer()
        }
    }

    /** Completes the current set. The row is logged only after its final set. */
    fun onCompleteSet() = mutate { entry, session ->
        if (session.status != SessionStatus.ACTIVE) return@mutate session
        val exercise = entry.exercises.getOrNull(session.exerciseIndex) ?: return@mutate session
        if (session.currentSet < exercise.sets) {
            startRestOrAdvance(
                session = session.copy(currentSet = session.currentSet + 1),
                restSeconds = exercise.rest,
            )
        } else {
            viewModelScope.launch { logSender.send(exercise, LogStatus.DONE, exercise.workoutRowId) }
            val nextSession = advanceExercise(entry, session)
            if (nextSession.status == SessionStatus.COMPLETED) {
                sendSessionEvent(entry, nextSession, SessionEventType.COMPLETED, SessionStopReason.COMPLETED)
            }
            nextSession
        }
    }

    /** Skips the active exercise immediately and advances to the next row. */
    fun onSkip() = mutate { entry, session ->
        if (session.status != SessionStatus.ACTIVE) return@mutate session
        val exercise = entry.exercises.getOrNull(session.exerciseIndex) ?: return@mutate session
        viewModelScope.launch { logSender.send(exercise, LogStatus.SKIPPED, exercise.workoutRowId) }
        advanceExercise(entry, session, restAfterCurrent = false)
    }

    /** Ends the current rest early and starts the next planned set/exercise. */
    fun onStartNow() {
        val session = _uiState.value.session ?: return
        if (session.status != SessionStatus.RESTING) return
        setSession(activeAfterRest(session))
    }

    /** Pauses the active set or freezes the current rest countdown. */
    fun onPause() {
        val session = _uiState.value.session ?: return
        if (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.RESTING) {
            setSession(pauseSession(session, SessionStopReason.PAUSED_BY_USER))
        }
    }

    /** Resumes an explicitly paused set or rest countdown. */
    fun onResume() {
        val session = _uiState.value.session ?: return
        if (session.status != SessionStatus.PAUSED) return

        val pausedRestSeconds = session.pausedRestRemainingSeconds
        val resumed = if (pausedRestSeconds != null && pausedRestSeconds > 0) {
            session.copy(
                status = SessionStatus.RESTING,
                restUntilEpochMillis = nowEpochMillis() + pausedRestSeconds * 1_000L,
                pausedRestRemainingSeconds = null,
                elapsedStartedAtEpochMillis = nowEpochMillis(),
                lastStopReason = null,
            )
        } else {
            activeAfterRest(session).startElapsedSegment()
        }
        setSession(resumed)
    }

    /** Restarts the workout from the first exercise without emitting logs. */
    fun onRestartWorkout() = setSession(newSession())

    /** Ends the workout without sending completion logs for unfinished rows. */
    fun onEndWorkout() {
        val state = _uiState.value
        val entry = state.entry ?: return
        val session = state.session ?: return
        if (session.status == SessionStatus.COMPLETED || session.status == SessionStatus.ENDED) return
        val endedSession = stopElapsedSegment(session, SessionStopReason.ENDED_BY_USER).copy(status = SessionStatus.ENDED)
        setSession(endedSession)
        sendSessionEvent(entry, endedSession, SessionEventType.ENDED, SessionStopReason.ENDED_BY_USER)
    }

    /** Extends the active rest countdown so users can recover before continuing. */
    fun onAddRestSeconds(seconds: Int) {
        if (seconds <= 0) return
        val session = _uiState.value.session ?: return
        if (session.status != SessionStatus.RESTING) return

        val now = nowEpochMillis()
        val currentUntil = session.restUntilEpochMillis ?: now
        setSession(session.copy(restUntilEpochMillis = maxOf(currentUntil, now) + seconds * 1_000L))
    }

    /** Cancels the active view without completing or skipping the exercise. */
    fun onCancel() = persistPaused()

    fun onUpgrade() = adjustSets(1)

    fun onDowngrade() = adjustSets(-1)

    /** Called from the screen's exit hooks (back press / lifecycle ON_STOP).
     *  A no-op if the session isn't currently "active" (e.g. already paused
     *  or completed) so it can't clobber a completed session on exit. */
    fun saveOnExitIfActive() {
        val session = _uiState.value.session ?: return
        if (session.status == SessionStatus.ACTIVE) setSession(pauseSession(session, SessionStopReason.APP_CLOSED))
    }

    override fun onCleared() {
        restTimerJob?.cancel()
        super.onCleared()
    }

    private fun persistPaused() = mutate { _, session ->
        when (session.status) {
            SessionStatus.ACTIVE -> pauseSession(session, SessionStopReason.APP_CLOSED)
            SessionStatus.PAUSED,
            SessionStatus.RESTING,
            SessionStatus.COMPLETED,
            SessionStatus.ENDED -> session
            else -> pauseSession(session, SessionStopReason.UNEXPECTED_INTERRUPTION)
        }
    }

    private fun advanceExercise(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        restAfterCurrent: Boolean = true,
    ): SessionState = advanceExerciseIndex(entry, session, restAfterCurrent)

    private fun adjustSets(delta: Int) {
        val state = _uiState.value
        val entry = state.entry ?: return
        val session = state.session ?: return
        val index = session.exerciseIndex
        val exercise = entry.exercises.getOrNull(index) ?: return
        val newSets = (exercise.sets + delta).coerceIn(1, 99)
        if (newSets == exercise.sets) return

        val updatedExercise = exercise.copy(sets = newSets)
        val updatedExercises = entry.exercises.toMutableList().apply { this[index] = updatedExercise }
        val updatedSession = session.copy(currentSet = session.currentSet.coerceAtMost(newSets))
        _uiState.value = state.copy(entry = entry.copy(exercises = updatedExercises), session = updatedSession)
        viewModelScope.launch {
            repository.updateExercise(entryId, index, updatedExercise)
            repository.updateSessionState(entryId, updatedSession)
        }
        synchronizeRestTimer()
    }

    private fun advanceExerciseIndex(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        restAfterCurrent: Boolean,
    ): SessionState {
        val nextIndex = session.exerciseIndex + 1
        return if (nextIndex >= entry.exercises.size) {
            stopElapsedSegment(session, SessionStopReason.COMPLETED).copy(
                exerciseIndex = entry.exercises.lastIndex.coerceAtLeast(0),
                status = SessionStatus.COMPLETED,
                restUntilEpochMillis = null,
                pausedRestRemainingSeconds = null,
            )
        } else {
            val exercise = entry.exercises.getOrNull(session.exerciseIndex)
            startRestOrAdvance(
                session = session.copy(exerciseIndex = nextIndex, currentSet = 1),
                restSeconds = if (restAfterCurrent) exercise?.rest ?: 0 else 0,
            )
        }
    }

    private fun startRestOrAdvance(session: SessionState, restSeconds: Int): SessionState =
        if (restSeconds <= 0) {
            activeAfterRest(session)
        } else {
            session.copy(
                status = SessionStatus.RESTING,
                restUntilEpochMillis = nowEpochMillis() + restSeconds * 1_000L,
                pausedRestRemainingSeconds = null,
            )
        }

    private fun activeAfterRest(session: SessionState): SessionState =
        session.copy(status = SessionStatus.ACTIVE, restUntilEpochMillis = null, pausedRestRemainingSeconds = null)

    private fun newSession(): SessionState = SessionState(
        workoutEntryId = entryId,
        exerciseIndex = 0,
        currentSet = 1,
        status = SessionStatus.ACTIVE,
        elapsedStartedAtEpochMillis = nowEpochMillis(),
    )

    private fun SessionState.startElapsedSegment(): SessionState =
        if (elapsedStartedAtEpochMillis == null) {
            copy(elapsedStartedAtEpochMillis = nowEpochMillis(), lastStopReason = null)
        } else {
            copy(lastStopReason = null)
        }

    private fun pauseSession(session: SessionState, reason: String): SessionState {
        val pausedRestSeconds = if (session.status == SessionStatus.RESTING) {
            remainingRestSeconds(session).coerceAtLeast(1)
        } else {
            null
        }
        return stopElapsedSegment(session, reason).copy(
            status = SessionStatus.PAUSED,
            restUntilEpochMillis = null,
            pausedRestRemainingSeconds = pausedRestSeconds,
        )
    }

    private fun stopElapsedSegment(session: SessionState, reason: String): SessionState {
        val elapsedStartedAt = session.elapsedStartedAtEpochMillis
        val elapsedThisSegment = if (elapsedStartedAt != null) {
            (nowEpochMillis() - elapsedStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        return session.copy(
            accumulatedElapsedMillis = (session.accumulatedElapsedMillis + elapsedThisSegment).coerceAtLeast(0L),
            elapsedStartedAtEpochMillis = null,
            lastStopReason = reason,
        )
    }

    private fun elapsedSeconds(session: SessionState): Int {
        val elapsedStartedAt = session.elapsedStartedAtEpochMillis
        val runningMillis = if (
            elapsedStartedAt != null &&
            (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.RESTING)
        ) {
            (nowEpochMillis() - elapsedStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        return ((session.accumulatedElapsedMillis + runningMillis + 999L) / 1_000L).toInt().coerceAtLeast(0)
    }

    private fun sendSessionEvent(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        eventType: String,
        stopReason: String,
    ) {
        val currentExercise = entry.exercises.getOrNull(session.exerciseIndex)
        val event = WorkoutSessionEvent(
            workoutEntryId = entry.id,
            workoutDate = entry.date,
            eventType = eventType,
            stopReason = stopReason,
            timestamp = Instant.now().toString(),
            elapsedSeconds = elapsedSeconds(session),
            estimatedDurationSeconds = entry.estimatedDurationSeconds(),
            exerciseIndex = session.exerciseIndex,
            currentSet = session.currentSet,
            totalExercises = entry.exercises.size,
            currentExercise = currentExercise?.exercise,
        )
        viewModelScope.launch { logSender.sendSessionEvent(event) }
    }

    private fun remainingRestSeconds(session: SessionState): Int {
        val until = session.restUntilEpochMillis ?: return 0
        return ((until - nowEpochMillis() + 999L) / 1_000L).toInt().coerceAtLeast(0)
    }

    private fun synchronizeRestTimer() {
        restTimerJob?.cancel()
        val session = _uiState.value.session ?: return
        val pausedRestRemainingSeconds = session.pausedRestRemainingSeconds
        if (session.status == SessionStatus.PAUSED && pausedRestRemainingSeconds != null) {
            _uiState.value = _uiState.value.copy(
                restRemainingSeconds = pausedRestRemainingSeconds,
                elapsedSeconds = elapsedSeconds(session),
            )
            return
        }
        if (session.status != SessionStatus.RESTING) {
            _uiState.value = _uiState.value.copy(
                restRemainingSeconds = 0,
                elapsedSeconds = elapsedSeconds(session),
            )
            if (session.status == SessionStatus.ACTIVE) startElapsedTicker()
            return
        }

        val remaining = remainingRestSeconds(session)
        if (remaining <= 0) {
            setSession(activeAfterRest(session))
            return
        }

        _uiState.value = _uiState.value.copy(restRemainingSeconds = remaining, elapsedSeconds = elapsedSeconds(session))
        restTimerJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value.session ?: return@launch
                if (current.status != SessionStatus.RESTING) return@launch

                val seconds = remainingRestSeconds(current)
                if (seconds <= 0) {
                    setSession(activeAfterRest(current))
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    restRemainingSeconds = seconds,
                    elapsedSeconds = elapsedSeconds(current),
                )
                delay(1_000L)
            }
        }
    }

    private fun setSession(newSession: SessionState) {
        _uiState.value = _uiState.value.copy(session = newSession, elapsedSeconds = elapsedSeconds(newSession))
        viewModelScope.launch { repository.updateSessionState(entryId, newSession) }
        synchronizeRestTimer()
    }

    private fun startElapsedTicker() {
        restTimerJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value.session ?: return@launch
                if (current.status != SessionStatus.ACTIVE) return@launch
                _uiState.value = _uiState.value.copy(elapsedSeconds = elapsedSeconds(current))
                delay(1_000L)
            }
        }
    }

    /** Runs [transform] against the current (entry, session) pair, persists
     *  the result both to local state and to the repository. No-ops if
     *  either is missing (still loading). */
    private inline fun mutate(transform: (DownloadedWorkoutEntry, SessionState) -> SessionState) {
        val state = _uiState.value
        val entry = state.entry ?: return
        val session = state.session ?: return
        val newSession = transform(entry, session)
        _uiState.value = state.copy(session = newSession, elapsedSeconds = elapsedSeconds(newSession))
        viewModelScope.launch { repository.updateSessionState(entryId, newSession) }
        synchronizeRestTimer()
    }

    class Factory(
        private val entryId: String,
        private val repository: WorkoutRepository,
        private val logSender: LogSender,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionViewModel(entryId, repository, logSender) as T
    }
}
