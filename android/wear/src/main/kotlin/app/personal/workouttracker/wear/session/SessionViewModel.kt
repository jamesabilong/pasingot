package app.personal.workouttracker.wear.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionEventType
import app.personal.workouttracker.shared.SessionStopReason
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutSessionEvent
import app.personal.workouttracker.shared.WatchSessionSnapshot
import app.personal.workouttracker.shared.estimatedDurationSeconds
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.data.WorkoutSessionStore
import app.personal.workouttracker.wear.data.WorkoutSessionEffects
import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.wear.data.SessionOutcomeActionType
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartPackagePersistence
import app.personal.workouttracker.wear.quickstart.GlobalSessionStartGate
import app.personal.workouttracker.wear.quickstart.LegacySessionGateResult
import app.personal.workouttracker.wear.quickstart.NoOpQuickStartLogSender
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartRuntimePersistence
import app.personal.workouttracker.wear.quickstart.QuickStartRuntimeStore
import app.personal.workouttracker.wear.quickstart.QuickStartSessionStore
import app.personal.workouttracker.wear.quickstart.DataLayerQuickStartResultClient
import app.personal.workouttracker.wear.quickstart.WatchSessionPackageStore
import app.personal.workouttracker.wear.quickstart.WorkoutRepositorySessionSnapshotSource
import app.personal.workouttracker.wear.cues.RestCountdownChange
import app.personal.workouttracker.wear.cues.RestCountdownLock
import app.personal.workouttracker.wear.cues.AndroidTtsCueOutput
import app.personal.workouttracker.wear.cues.DataStoreWatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCueController
import app.personal.workouttracker.wear.cues.WatchCueStore
import app.personal.workouttracker.wear.cues.WatchCueCancellation
import app.personal.workouttracker.wear.cues.extend
import app.personal.workouttracker.wear.cues.tick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/** Everything [SessionScreen] needs to render one frame. */
data class SessionUiState(
    val entry: DownloadedWorkoutEntry? = null,
    val session: SessionState? = null,
    val restRemainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val loading: Boolean = true,
    val blockedReason: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val canAdjustSets: Boolean = true,
    val canRestart: Boolean = true,
    val canExtendRest: Boolean = false,
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
    private val repository: WorkoutSessionStore,
    private val logSender: LogSender,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val legacyStartGate: GlobalSessionStartGate?,
    private val canAdjustSets: Boolean = true,
    private val canRestart: Boolean = true,
    private val cueEmitter: SessionCueEmitter = NoOpSessionCueEmitter,
    private val newRestIntervalId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private var restTimerJob: Job? = null
    private var screenVisible = false
    private var exitPending = false
    private val transitionMutex = Mutex()

    init {
        viewModelScope.launch {
            try {
                val entry = repository.getEntry(entryId)
                // Resume in place if a SessionState already exists, else start
                // fresh at exerciseIndex = 0 (Prompt 4 req 3).
                val storedSession = entry?.sessionState
                val session = entry?.let { ensureRestLock(storedSession ?: newSession()) }
                if (legacyStartGate != null && entry != null && session != null && session.status != SessionStatus.COMPLETED &&
                    session.status != SessionStatus.ENDED) {
                    val result = legacyStartGate.startLegacy(entryId, nowEpochMillis()) {
                        check(repository.commitSession(entry, entry.copy(sessionState = session))) {
                            "Workout changed while opening"
                        }
                    }
                    val blocked = when (result) {
                        is LegacySessionGateResult.BlockedByQuickStart ->
                            if (result.sessionPackage.state == QuickStartPackageState.READY)
                                "Quick Start is ready on watch" else "Quick Start is in progress"
                        is LegacySessionGateResult.BlockedByLegacy -> "Finish your current workout first"
                        else -> null
                    }
                    if (blocked != null) {
                        _uiState.value = SessionUiState(loading = false, blockedReason = blocked)
                        return@launch
                    }
                } else if (entry != null && storedSession != null && session != storedSession) {
                    check(repository.commitSession(entry, entry.copy(sessionState = session))) {
                        "Workout changed while recovering rest"
                    }
                }
                _uiState.value = SessionUiState(
                    entry = entry?.copy(sessionState = session),
                    session = session,
                    elapsedSeconds = session?.let(::elapsedSeconds) ?: 0,
                    loading = false,
                    canAdjustSets = canAdjustSets,
                    canRestart = canRestart,
                    canExtendRest = session?.let(::canExtendRest) ?: false,
                )
                if (entry != null && session != null) sendSessionSnapshot(entry, session)
                flushPendingHistory()
                synchronizeRestTimer()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = SessionUiState(loading = false,
                    blockedReason = "Could not open workout")
            }
        }
    }

    /** Completes the current set. The row is logged only after its final set. */
    fun onCompleteSet() = mutate(expected = _uiState.value.session) { entry, session ->
        if (session.status != SessionStatus.ACTIVE) return@mutate null
        val exercise = entry.exercises.getOrNull(session.exerciseIndex) ?: return@mutate null
        val finalSet = session.currentSet >= exercise.sets
        val next = if (!finalSet) {
            startRestOrAdvance(
                session = session.copy(currentSet = session.currentSet + 1),
                restSeconds = exercise.rest,
            )
        } else {
            advanceExercise(entry, session)
        }
        val restSeconds = exercise.rest
        SessionChange(next, effects = if (finalSet) WorkoutSessionEffects(
            log = LogEntry(exercise = exercise.exercise, status = LogStatus.DONE,
                timestamp = Instant.ofEpochMilli(nowEpochMillis()).toString(), workoutRowId = exercise.workoutRowId),
            event = if (next.status == SessionStatus.COMPLETED)
                sessionEvent(entry, next, SessionEventType.COMPLETED, SessionStopReason.COMPLETED) else null,
        ) else null, action = SessionOutcomeAction(SessionOutcomeActionType.SET_COMPLETED, session.exerciseIndex),
            afterCommit = if (next.status == SessionStatus.RESTING) { committedEntry, committedSession ->
                cueEmitter.restStarted(
                    committedEntry, committedSession, restSeconds, session.exerciseIndex,
                )
            } else null)
    }

    /** Skips the active exercise immediately and advances to the next row. */
    fun onSkip() = mutate(expected = _uiState.value.session) { entry, session ->
        if (session.status != SessionStatus.ACTIVE) return@mutate null
        val exercise = entry.exercises.getOrNull(session.exerciseIndex) ?: return@mutate null
        val nextSession = advanceExercise(entry, session, restAfterCurrent = false)
        SessionChange(nextSession, effects = WorkoutSessionEffects(
            log = LogEntry(exercise = exercise.exercise, status = LogStatus.SKIPPED,
                timestamp = Instant.ofEpochMilli(nowEpochMillis()).toString(), workoutRowId = exercise.workoutRowId),
            event = if (nextSession.status == SessionStatus.COMPLETED)
                sessionEvent(entry, nextSession, SessionEventType.COMPLETED, SessionStopReason.COMPLETED) else null,
        ), action = SessionOutcomeAction(SessionOutcomeActionType.EXERCISE_SKIPPED, session.exerciseIndex),
            afterCommit = { _, _ -> cueEmitter.cancel(WatchCueCancellation.SKIP) })
    }

    /** Ends the current rest early and starts the next planned set/exercise. */
    fun onStartNow() = mutate { _, session ->
        val deadline = session.restUntilEpochMillis
        if (session.status == SessionStatus.RESTING && deadline != null) {
            SessionChange(activeAfterRest(session), afterCommit = { entry, active ->
                cueEmitter.go(entry, active, deadline)
            })
        } else null
    }

    /** Pauses the active set or freezes the current rest countdown. */
    fun onPause() = mutate { _, session ->
        if (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.RESTING) {
            SessionChange(pauseSession(session, SessionStopReason.PAUSED_BY_USER),
                afterCommit = { _, _ -> cueEmitter.cancel(WatchCueCancellation.PAUSE) })
        } else null
    }

    /** Resumes an explicitly paused set or rest countdown. */
    fun onResume() = mutate { _, session ->
        if (session.status != SessionStatus.PAUSED) return@mutate null

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
        SessionChange(resumed, afterCommit = if (resumed.status == SessionStatus.RESTING) {
            { entry, committed ->
                cueEmitter.restStarted(entry, committed, pausedRestSeconds ?: 0,
                    committed.exerciseIndex)
            }
        } else null)
    }

    /** Restarts the workout from the first exercise without emitting logs. */
    fun onRestartWorkout() = mutate(restarting = true) { _, session ->
        if (session.status == SessionStatus.PAUSED) SessionChange(newSession(),
            afterCommit = { _, _ -> cueEmitter.cancel(WatchCueCancellation.RESTART) }) else null
    }

    /** Ends the workout without sending completion logs for unfinished rows. */
    fun onEndWorkout() = mutate { entry, session ->
        if (session.status == SessionStatus.COMPLETED || session.status == SessionStatus.ENDED) return@mutate null
        val endedSession = stopElapsedSegment(session, SessionStopReason.ENDED_BY_USER).copy(
            status = SessionStatus.ENDED, restUntilEpochMillis = null,
            pausedRestRemainingSeconds = null, restIntervalId = null,
            restFinalCountdownStarted = false)
        SessionChange(endedSession, effects = WorkoutSessionEffects(
            event = sessionEvent(entry, endedSession, SessionEventType.ENDED, SessionStopReason.ENDED_BY_USER)),
            afterCommit = { _, _ -> cueEmitter.cancel(WatchCueCancellation.END) })
    }

    /** Extends the active rest countdown so users can recover before continuing. */
    fun onAddRestSeconds(seconds: Int) {
        if (seconds !in setOf(5, 10, 30)) return
        mutate { _, session ->
            if (session.status != SessionStatus.RESTING) return@mutate null
            val lock = session.restCountdownLock() ?: return@mutate null
            when (val result = lock.extend(nowEpochMillis(), seconds)) {
                is RestCountdownChange.Changed -> SessionChange(session.copy(
                    restUntilEpochMillis = result.state.deadlineEpochMillis,
                    restFinalCountdownStarted = result.state.finalCountdownStarted,
                ))
                is RestCountdownChange.Refused -> null
            }
        }
    }

    /** Cancels the active view without completing or skipping the exercise. */
    fun onCancel() = persistPaused()

    /** Navigate only after the pause is durably committed. */
    fun onCancel(afterSaved: () -> Unit) {
        if (exitPending) return
        exitPending = true
        persistPaused { committed ->
            exitPending = false
            if (committed) afterSaved()
        }
    }

    fun onUpgrade() = adjustSets(1)

    fun onDowngrade() = adjustSets(-1)

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /** Called from the screen's exit hooks (back press / lifecycle ON_STOP).
     *  A no-op if the session isn't currently "active" (e.g. already paused
     *  or completed) so it can't clobber a completed session on exit. */
    fun saveOnExitIfActive() {
        mutate { _, session ->
            if (session.status == SessionStatus.ACTIVE)
                SessionChange(pauseSession(session, SessionStopReason.APP_CLOSED)) else null
        }
    }

    /** Only redraw a visible session. Deadlines preserve rest/elapsed time while hidden. */
    fun onScreenVisibilityChanged(visible: Boolean) {
        if (screenVisible == visible) return
        screenVisible = visible
        if (!visible) dispatchCue { cueEmitter.cancel(WatchCueCancellation.NAVIGATION) }
        synchronizeRestTimer()
    }

    override fun onCleared() {
        restTimerJob?.cancel()
        cueEmitter.close()
        super.onCleared()
    }

    private fun persistPaused(onCommitted: ((Boolean) -> Unit)? = null) = mutate(onCommitted = onCommitted) { _, session ->
        when (session.status) {
            SessionStatus.ACTIVE -> SessionChange(pauseSession(session, SessionStopReason.APP_CLOSED))
            SessionStatus.PAUSED,
            SessionStatus.RESTING,
            SessionStatus.COMPLETED,
            SessionStatus.ENDED -> null
            else -> SessionChange(pauseSession(session, SessionStopReason.UNEXPECTED_INTERRUPTION))
        }
    }

    private fun advanceExercise(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        restAfterCurrent: Boolean = true,
    ): SessionState = advanceExerciseIndex(entry, session, restAfterCurrent)

    private fun adjustSets(delta: Int) = mutate { entry, session ->
        if (!canAdjustSets) return@mutate null
        if (session.status != SessionStatus.ACTIVE) return@mutate null
        val index = session.exerciseIndex
        val exercise = entry.exercises.getOrNull(index) ?: return@mutate null
        val newSets = (exercise.sets + delta).coerceIn(1, 99)
        if (newSets == exercise.sets) return@mutate null

        val updatedExercise = exercise.copy(sets = newSets)
        val updatedExercises = entry.exercises.toMutableList().apply { this[index] = updatedExercise }
        val updatedSession = session.copy(currentSet = session.currentSet.coerceAtMost(newSets))
        SessionChange(updatedSession, exercises = updatedExercises)
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
                restIntervalId = null,
                restFinalCountdownStarted = false,
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
                restIntervalId = newRestIntervalId(),
                restFinalCountdownStarted = false,
            )
        }

    private fun activeAfterRest(session: SessionState): SessionState =
        session.copy(status = SessionStatus.ACTIVE, restUntilEpochMillis = null,
            pausedRestRemainingSeconds = null, restIntervalId = null,
            restFinalCountdownStarted = false)

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

    private fun ensureRestLock(session: SessionState): SessionState {
        if (session.restIntervalId != null) return session
        val restDeadline = session.restUntilEpochMillis
        val pausedRestSeconds = session.pausedRestRemainingSeconds
        return when {
            session.status == SessionStatus.RESTING && restDeadline != null -> session.copy(
                restIntervalId = newRestIntervalId(),
                restFinalCountdownStarted = session.restFinalCountdownStarted ||
                    restDeadline - nowEpochMillis() <= 5_000L,
            )
            session.status == SessionStatus.PAUSED && pausedRestSeconds != null -> session.copy(
                restIntervalId = newRestIntervalId(),
                restFinalCountdownStarted = session.restFinalCountdownStarted ||
                    pausedRestSeconds <= 5,
            )
            else -> session
        }
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
            restFinalCountdownStarted = session.restFinalCountdownStarted ||
                ((session.restUntilEpochMillis ?: Long.MAX_VALUE) - nowEpochMillis() <= 5_000L),
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

    private fun sessionEvent(
        entry: DownloadedWorkoutEntry,
        session: SessionState,
        eventType: String,
        stopReason: String,
    ): WorkoutSessionEvent {
        val currentExercise = entry.exercises.getOrNull(session.exerciseIndex)
        return WorkoutSessionEvent(
            workoutEntryId = entry.id,
            workoutDate = entry.date,
            eventType = eventType,
            stopReason = stopReason,
            timestamp = Instant.ofEpochMilli(nowEpochMillis()).toString(),
            elapsedSeconds = elapsedSeconds(session),
            estimatedDurationSeconds = entry.estimatedDurationSeconds(),
            exerciseIndex = session.exerciseIndex,
            currentSet = session.currentSet,
            totalExercises = entry.exercises.size,
            currentExercise = currentExercise?.exercise,
        )
    }

    private fun remainingRestSeconds(session: SessionState): Int {
        val until = session.restUntilEpochMillis ?: return 0
        return ((until - nowEpochMillis() + 999L) / 1_000L).toInt().coerceAtLeast(0)
    }

    private fun canExtendRest(session: SessionState): Boolean {
        val deadline = session.restUntilEpochMillis ?: return false
        return session.status == SessionStatus.RESTING &&
            !session.restFinalCountdownStarted && deadline - nowEpochMillis() > 5_000L
    }

    private fun SessionState.restCountdownLock(): RestCountdownLock? {
        val intervalId = restIntervalId?.takeIf(String::isNotBlank) ?: return null
        val deadline = restUntilEpochMillis ?: return null
        return RestCountdownLock(
            intervalId = intervalId,
            deadlineEpochMillis = deadline,
            finalCountdownStarted = restFinalCountdownStarted,
        )
    }

    private fun sendSessionSnapshot(entry: DownloadedWorkoutEntry, session: SessionState) {
        val snapshot = WatchSessionSnapshot(
            workoutEntryId = entry.id,
            workoutDate = entry.date,
            status = session.status,
            timestamp = Instant.ofEpochMilli(nowEpochMillis()).toString(),
            exerciseIndex = session.exerciseIndex,
            currentSet = session.currentSet,
            totalExercises = entry.exercises.size,
            currentExercise = entry.exercises.getOrNull(session.exerciseIndex)?.exercise,
            elapsedSeconds = elapsedSeconds(session),
            restUntilEpochMillis = session.restUntilEpochMillis,
        )
        viewModelScope.launch { logSender.sendSessionSnapshot(snapshot) }
    }

    private fun synchronizeRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
        if (!screenVisible) return
        val session = _uiState.value.session ?: return
        val pausedRestRemainingSeconds = session.pausedRestRemainingSeconds
        if (session.status == SessionStatus.PAUSED && pausedRestRemainingSeconds != null) {
            _uiState.value = _uiState.value.copy(
                restRemainingSeconds = pausedRestRemainingSeconds,
                elapsedSeconds = elapsedSeconds(session),
                canExtendRest = false,
            )
            return
        }
        if (session.status != SessionStatus.RESTING) {
            _uiState.value = _uiState.value.copy(
                restRemainingSeconds = 0,
                elapsedSeconds = elapsedSeconds(session),
                canExtendRest = false,
            )
            if (session.status == SessionStatus.ACTIVE) startElapsedTicker()
            return
        }

        val remaining = remainingRestSeconds(session)
        val lockChange = session.restCountdownLock()?.tick(nowEpochMillis())
        if (lockChange is RestCountdownChange.Changed) {
            if (lockChange.state.finished) finishRest(session)
            else latchFinalCountdown(session, lockChange.state)
            return
        }

        _uiState.value = _uiState.value.copy(restRemainingSeconds = remaining,
            elapsedSeconds = elapsedSeconds(session), canExtendRest = canExtendRest(session))
        restTimerJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value.session ?: return@launch
                if (current.status != SessionStatus.RESTING) return@launch

                val change = current.restCountdownLock()?.tick(nowEpochMillis())
                if (change is RestCountdownChange.Changed) {
                    if (change.state.finished) finishRest(current)
                    else latchFinalCountdown(current, change.state)
                    return@launch
                }

                val seconds = remainingRestSeconds(current)
                _uiState.value = _uiState.value.copy(
                    restRemainingSeconds = seconds,
                    elapsedSeconds = elapsedSeconds(current),
                    canExtendRest = canExtendRest(current),
                )
                delay(1_000L)
            }
        }
    }

    private fun latchFinalCountdown(expected: SessionState, lock: RestCountdownLock) {
        mutate(expected = expected) { _, current ->
            SessionChange(current.copy(restFinalCountdownStarted = lock.finalCountdownStarted),
                afterCommit = if (lock.finalCountdownStarted) { entry, committed ->
                    cueEmitter.fiveSeconds(entry, committed)
                } else null)
        }
    }

    private fun finishRest(expected: SessionState) {
        val deadline = expected.restUntilEpochMillis ?: nowEpochMillis()
        mutate(expected = expected) { _, current ->
            SessionChange(activeAfterRest(current), afterCommit = { entry, active ->
                cueEmitter.go(entry, active, deadline)
            })
        }
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

    private data class SessionChange(
        val session: SessionState,
        val exercises: List<WorkoutExercise>? = null,
        val effects: WorkoutSessionEffects? = null,
        val action: SessionOutcomeAction? = null,
        val afterCommit: (suspend (DownloadedWorkoutEntry, SessionState) -> Unit)? = null,
    )

    /** Serialize commands, persist progress/history atomically, then expose the result. */
    private fun mutate(
        expected: SessionState? = null,
        restarting: Boolean = false,
        onCommitted: ((Boolean) -> Unit)? = null,
        transform: (DownloadedWorkoutEntry, SessionState) -> SessionChange?,
    ) {
        if (_uiState.value.session == null) {
            onCommitted?.invoke(true)
            return
        }
        viewModelScope.launch {
            transitionMutex.withLock {
                val state = _uiState.value
                val entry = state.entry ?: run { onCommitted?.invoke(true); return@withLock }
                val session = state.session ?: run { onCommitted?.invoke(true); return@withLock }
                if (expected != null && expected != session) {
                    onCommitted?.invoke(false)
                    return@withLock
                }
                val change = transform(entry, session) ?: run {
                    onCommitted?.invoke(true)
                    return@withLock
                }
                val updated = entry.copy(sessionState = change.session,
                    exercises = change.exercises ?: entry.exercises)
                if (updated == entry && change.effects == null && change.action == null) {
                    onCommitted?.invoke(true)
                    return@withLock
                }
                _uiState.value = state.copy(saving = true, error = null)
                try {
                    suspend fun commit() {
                        check(repository.commitSession(entry, updated, change.effects, change.action)) {
                            "Workout changed. Close and reopen it."
                        }
                    }
                    if (restarting && legacyStartGate != null) {
                        check(legacyStartGate.startLegacy(entryId, nowEpochMillis(), ::commit) ==
                            LegacySessionGateResult.Started) { "Another workout owns the session" }
                    } else commit()
                    _uiState.value = _uiState.value.copy(entry = updated, session = change.session,
                        elapsedSeconds = elapsedSeconds(change.session), saving = false, error = null,
                        canExtendRest = canExtendRest(change.session))
                    sendSessionSnapshot(updated, change.session)
                    flushPendingHistory()
                    synchronizeRestTimer()
                    change.afterCommit?.let { effect ->
                        dispatchCue { effect(updated, change.session) }
                    }
                    onCommitted?.invoke(true)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _uiState.value = _uiState.value.copy(saving = false,
                        error = error.message ?: "Could not save progress. Try again.")
                    onCommitted?.invoke(false)
                }
            }
        }
    }

    private fun flushPendingHistory() {
        viewModelScope.launch {
            try {
                repository.flushPendingEffects(logSender)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The outbox is still durable; app start and LogFlushWorker retry it.
            }
        }
    }

    private fun dispatchCue(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { Unit }
        }
    }

    class Factory(
        private val entryId: String,
        private val repository: WorkoutRepository,
        private val logSender: LogSender,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionViewModel(entryId, repository, logSender,
                legacyStartGate = GlobalSessionStartGate(
                    WorkoutRepositorySessionSnapshotSource(repository),
                    WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(appContext)),
                ), cueEmitter = createProductionSessionCueEmitter(appContext)) as T
    }

    class QuickStartFactory(
        private val requestId: String,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(appContext))
            return SessionViewModel(
                entryId = requestId,
                repository = QuickStartSessionStore(
                    requestId = requestId,
                    runtimeStore = runtime,
                    resultClient = DataLayerQuickStartResultClient(appContext.applicationContext),
                ),
                logSender = NoOpQuickStartLogSender,
                legacyStartGate = null,
                canAdjustSets = false,
                canRestart = false,
                cueEmitter = createProductionSessionCueEmitter(appContext),
            ) as T
        }
    }
}

private fun createProductionSessionCueEmitter(context: Context): SessionCueEmitter =
    ControllerSessionCueEmitter(WatchCueController(
        WatchCueStore(DataStoreWatchCuePersistence(context)),
        AndroidTtsCueOutput(context.applicationContext),
    ))
