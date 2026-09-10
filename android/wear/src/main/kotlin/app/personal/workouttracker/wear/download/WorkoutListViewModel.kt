package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.wear.data.WearSyncClient
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.data.DownloadFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
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

    private val _feedback = MutableStateFlow<DownloadFeedback?>(null)
    val feedback = _feedback.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.downloadFeedback.filterNotNull().collect { _feedback.value = it }
        }
    }

    /** Wait for the listener's storage outcome, not just a sent request. */
    fun downloadNow() {
        if (_syncing.value) return
        _syncing.value = true
        _feedback.value = DownloadFeedback("Connecting to phone…", error = false)
        val previous = repository.downloadFeedback.value
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    coroutineScope {
                        val reply = async(start = CoroutineStart.UNDISPATCHED) {
                            repository.downloadFeedback.first { it != null && it != previous }
                        }
                        WearSyncClient.requestWorkout(appContext).getOrThrow()
                        _feedback.value = DownloadFeedback("Request sent. Waiting for your phone…", error = false)
                        _feedback.value = reply.await()
                    }
                }
            } catch (error: TimeoutCancellationException) {
                _feedback.value = DownloadFeedback("No reply yet. Open Pasingot on your phone, check the connection, then retry.", error = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _feedback.value = DownloadFeedback(error.message ?: "Sync failed. Try again.", error = true)
            } finally {
                _syncing.value = false
            }
        }
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
