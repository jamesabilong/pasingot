package app.personal.workouttracker.wear.quickstart

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.wear.cues.PhoneFirstWatchCueOutput
import app.personal.workouttracker.wear.cues.DataStoreWatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCueCancellation
import app.personal.workouttracker.wear.cues.WatchCueController
import app.personal.workouttracker.wear.cues.WatchCueEvent
import app.personal.workouttracker.wear.cues.WatchCueKind
import app.personal.workouttracker.wear.cues.WatchCueScripts
import app.personal.workouttracker.wear.cues.WatchCueStore
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private const val QUICK_START_COUNTDOWN_MILLIS = 5_000L

data class QuickStartCountdownUiState(
    val loading: Boolean = true,
    val title: String = "Quick workout",
    val exerciseName: String = "",
    val target: String = "",
    val remainingSeconds: Int = 5,
    val progress: Float = 1f,
    val starting: Boolean = false,
    val error: String? = null,
)

fun interface QuickStartCountdownDelay {
    suspend fun waitForNextFrame()
}

interface QuickStartCountdownCues {
    suspend fun begin(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long)
    suspend fun go(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long)
    suspend fun cancel(reason: WatchCueCancellation)
    suspend fun close()
}

class ControllerQuickStartCountdownCues(
    private val controller: WatchCueController,
) : QuickStartCountdownCues {
    override suspend fun begin(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long) {
        val request = sessionPackage.request
        val exercise = request.exercises.first()
        val load = exercise.loadWeight?.let { weight ->
            val shown = if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
            "$shown ${exercise.loadUnit.orEmpty()}".trim()
        }
        controller.emit(
            WatchCueEvent(request.requestId, request.revision, 0, 1,
                WatchCueKind.BRIEFING, attemptDeadlineMillis),
            WatchCueScripts.briefing(exercise.exerciseName, exercise.sets,
                exercise.prescription, load),
        )
        controller.emit(
            WatchCueEvent(request.requestId, request.revision, 0, 1,
                WatchCueKind.FIVE_SECONDS, attemptDeadlineMillis),
            WatchCueScripts.FIVE_SECONDS,
        )
    }

    override suspend fun go(sessionPackage: WatchSessionPackage, attemptDeadlineMillis: Long) {
        val request = sessionPackage.request
        controller.emit(
            WatchCueEvent(request.requestId, request.revision + 1, 0, 1,
                WatchCueKind.GO, attemptDeadlineMillis),
            WatchCueScripts.go(null, null),
        )
    }

    override suspend fun cancel(reason: WatchCueCancellation) = controller.cancel(reason)
    override suspend fun close() = controller.close()
}

/** Foreground-only countdown. READY is untouched until the zero boundary. */
class QuickStartCountdownViewModel(
    private val requestId: String,
    private val packageStore: QuickStartPackageStore,
    private val startCoordinator: QuickStartStartCoordinator,
    private val cues: QuickStartCountdownCues,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val countdownDelay: QuickStartCountdownDelay = QuickStartCountdownDelay { delay(100) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuickStartCountdownUiState())
    val uiState = _uiState.asStateFlow()
    private var countdownJob: Job? = null
    private var cueJob: Job? = null
    private var leaving = false

    fun begin(onStarted: (String) -> Unit) {
        if (countdownJob != null || leaving) return
        countdownJob = viewModelScope.launch {
            try {
                val countdownStartedAt = nowEpochMillis()
                val offered = packageStore.current(countdownStartedAt)?.takeIf {
                    it.request.requestId == requestId && it.state == QuickStartPackageState.READY
                } ?: error("Quick Start is no longer ready")
                val exercise = offered.request.exercises.first()
                val deadline = elapsedRealtimeMillis() + QUICK_START_COUNTDOWN_MILLIS
                // Cue identity uses an epoch deadline so a retry after process
                // death/reboot does not collide with a reset monotonic clock.
                val cueAttemptDeadline = countdownStartedAt + QUICK_START_COUNTDOWN_MILLIS
                _uiState.value = QuickStartCountdownUiState(
                    loading = false,
                    title = offered.request.title ?: "Quick workout",
                    exerciseName = exercise.exerciseName,
                    target = exercise.prescription,
                )
                cueJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    cues.begin(offered, cueAttemptDeadline)
                }
                while (true) {
                    val remainingMillis = deadline - elapsedRealtimeMillis()
                    if (remainingMillis <= 0) break
                    _uiState.value = _uiState.value.copy(
                        remainingSeconds = ceil(remainingMillis / 1_000.0).toInt().coerceIn(1, 5),
                        progress = (remainingMillis.toFloat() / QUICK_START_COUNTDOWN_MILLIS)
                            .coerceIn(0f, 1f),
                    )
                    countdownDelay.waitForNextFrame()
                }
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = 0,
                    progress = 0f,
                    starting = true,
                )
                cueJob?.cancelAndJoin()
                cues.cancel(WatchCueCancellation.START_NOW)
                val runtime = startCoordinator.start(
                    offered.request.requestId, offered.request.revision,
                )
                // Runtime is already durable and elapsed time has started. Keep
                // the visual GO state while bounded optional speech completes.
                cues.go(runtime.sessionPackage, cueAttemptDeadline)
                leaving = true
                onStarted(requestId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    starting = false,
                    error = error.message ?: "Could not start workout. Try again.",
                )
            }
        }
    }

    fun leave(onCancelled: () -> Unit) {
        if (leaving || _uiState.value.starting) return
        leaving = true
        val runningCountdown = countdownJob
        val runningCue = cueJob
        viewModelScope.launch {
            runningCountdown?.cancelAndJoin()
            runningCue?.cancelAndJoin()
            cues.cancel(WatchCueCancellation.NAVIGATION)
            cues.close()
            // ON_PAUSE may call leave while NavController is iterating its back stack.
            // Cancel immediately, then navigate after that lifecycle dispatch completes.
            yield()
            onCancelled()
        }
    }

    fun onLeavingForeground(onCancelled: () -> Unit) {
        if (_uiState.value.starting) {
            // Zero already committed STARTING; stop foreground audio without
            // trying to roll the session back to READY.
            viewModelScope.launch {
                cues.cancel(WatchCueCancellation.NAVIGATION)
                cues.close()
            }
        } else {
            leave(onCancelled)
        }
    }

    class Factory(
        private val requestId: String,
        private val context: Context,
        private val legacySessions: LegacySessionSnapshotSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val packageStore = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
            val runtimeStore = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
            val output = PhoneFirstWatchCueOutput(context.applicationContext, requestId)
            return QuickStartCountdownViewModel(
                requestId = requestId,
                packageStore = packageStore,
                startCoordinator = QuickStartStartCoordinator(
                    GlobalSessionStartGate(legacySessions, packageStore),
                    runtimeStore,
                    DataLayerQuickStartReceiptClient(context.applicationContext),
                    offerNotifier = AndroidQuickStartOfferNotifier(context),
                ),
                cues = ControllerQuickStartCountdownCues(
                    WatchCueController(
                        WatchCueStore(DataStoreWatchCuePersistence(context)),
                        output,
                    ),
                ),
            ) as T
        }
    }
}
