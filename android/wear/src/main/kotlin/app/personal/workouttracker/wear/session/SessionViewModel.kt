package app.personal.workouttracker.wear.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything [SessionScreen] needs to render one frame. */
data class SessionUiState(
    val entry: DownloadedWorkoutEntry? = null,
    val session: SessionState? = null,
    val loading: Boolean = true,
) {
    val currentExercise get() = entry?.exercises?.getOrNull(session?.exerciseIndex ?: 0)
    val totalExercises get() = entry?.exercises?.size ?: 0
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = repository.getEntry(entryId)
            // Resume in place if a SessionState already exists, else start
            // fresh at exerciseIndex = 0 (Prompt 4 req 3).
            val session = entry?.sessionState ?: SessionState(
                workoutEntryId = entryId,
                exerciseIndex = 0,
                currentSet = 1,
                status = SessionStatus.ACTIVE,
            )
            _uiState.value = SessionUiState(entry = entry, session = session, loading = false)
        }
    }

    /** Completes the whole active exercise and advances to the next one. */
    fun onDone() = mutate { entry, session ->
        val exercise = entry.exercises.getOrNull(session.exerciseIndex) ?: return@mutate session
        viewModelScope.launch { logSender.send(exercise, LogStatus.DONE, exercise.workoutRowId) }
        advanceExercise(entry, session)
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
        if (session.status == SessionStatus.ACTIVE) persistPaused()
    }

    private fun persistPaused() = mutate { _, session -> session.copy(status = SessionStatus.PAUSED) }

    private fun advanceExercise(entry: DownloadedWorkoutEntry, session: SessionState): SessionState =
        advanceExerciseIndex(entry, session)

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
        _uiState.value = state.copy(entry = entry.copy(exercises = updatedExercises))
        viewModelScope.launch { repository.updateExercise(entryId, index, updatedExercise) }
    }

    private fun advanceExerciseIndex(entry: DownloadedWorkoutEntry, session: SessionState): SessionState {
        val nextIndex = session.exerciseIndex + 1
        return if (nextIndex >= entry.exercises.size) {
            session.copy(exerciseIndex = entry.exercises.lastIndex.coerceAtLeast(0), status = SessionStatus.COMPLETED)
        } else {
            session.copy(exerciseIndex = nextIndex, currentSet = 1)
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
        _uiState.value = state.copy(session = newSession)
        viewModelScope.launch { repository.updateSessionState(entryId, newSession) }
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
