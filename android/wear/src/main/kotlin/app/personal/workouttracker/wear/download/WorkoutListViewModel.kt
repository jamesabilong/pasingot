package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.wear.data.WearSyncClient
import app.personal.workouttracker.wear.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Manage Downloads list screen (Prompt 5). */
class WorkoutListViewModel(
    private val appContext: Context,
    private val repository: WorkoutRepository,
) : ViewModel() {

    val entries: StateFlow<List<DownloadedWorkoutEntry>> = repository.entries.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /** Requests today's workout from the phone. Adding it to storage happens
     *  asynchronously when WorkoutSetListenerService receives the reply —
     *  this call only reports whether the *request* went out. */
    fun downloadNow() {
        viewModelScope.launch {
            WearSyncClient.requestWorkout(appContext)
                .onSuccess { _downloadError.value = null }
                .onFailure { _downloadError.value = it.message ?: "Download failed" }
        }
    }

    fun dismissError() {
        _downloadError.value = null
    }

    /** Prompt 5 secondary action: clears progress only, keeps cached data. */
    fun reset(entryId: String) {
        viewModelScope.launch { repository.resetEntry(entryId) }
    }

    /** Prompt 5 secondary action: removes the entry entirely. */
    fun delete(entryId: String) {
        viewModelScope.launch { repository.deleteEntry(entryId) }
    }

    class Factory(
        private val appContext: Context,
        private val repository: WorkoutRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkoutListViewModel(appContext, repository) as T
    }
}
