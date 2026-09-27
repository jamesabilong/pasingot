package app.personal.workouttracker.wear.quickstart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object QuickStartOfferEvents {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flow = changes.asSharedFlow()
    fun notifyChanged() { changes.tryEmit(Unit) }
}

data class QuickStartOfferUiState(
    val sessionPackage: WatchSessionPackage? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Bridges the durable READY package to explicit watch Start/Dismiss actions. */
class QuickStartOfferViewModel(
    private val packageStore: QuickStartPackageStore,
    private val runtimeStore: QuickStartRuntimeStore,
    private val startGate: GlobalSessionStartGate,
    private val receiptClient: QuickStartReceiptClient,
    private val resultClient: QuickStartResultClient? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuickStartOfferUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { QuickStartOfferEvents.flow.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val sessionPackage = packageStore.current(nowEpochMillis())
                _uiState.value = _uiState.value.copy(
                    sessionPackage = sessionPackage,
                    loading = false,
                    error = null,
                )
                if (sessionPackage?.state == QuickStartPackageState.STARTING) {
                    runtimeStore.current()?.takeIf {
                        it.sessionPackage.request.requestId == sessionPackage.request.requestId
                    }?.let { runtime ->
                        runtime.finalResult?.let { retryFinalResult(it) }
                            ?: retryStartedReceipt(runtime)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(loading = false,
                    error = "Could not read Quick Start. Reopen the app to retry.")
            }
        }
    }

    fun start(onStarted: (String) -> Unit) {
        val offered = _uiState.value.sessionPackage ?: return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val gated = startGate.startQuickStart(
                    offered.request.requestId,
                    offered.request.revision,
                    nowEpochMillis(),
                )
                val starting = when (gated) {
                    is QuickStartGateResult.Started -> gated.sessionPackage
                    is QuickStartGateResult.AlreadyStarting -> gated.sessionPackage
                    is QuickStartGateResult.BlockedByLegacy -> error("Finish your current workout first")
                    QuickStartGateResult.Expired -> error("Quick Start expired. Send it again from your phone.")
                    QuickStartGateResult.Missing -> error("Quick Start is no longer available")
                }
                val startedAt = nowEpochMillis()
                val initialized = runtimeStore.initialize(
                    starting,
                    SessionState(
                        workoutEntryId = starting.request.requestId,
                        exerciseIndex = 0,
                        currentSet = 1,
                        status = SessionStatus.ACTIVE,
                        elapsedStartedAtEpochMillis = startedAt,
                    ),
                    startedAt,
                )
                val runtime = when (initialized) {
                    is InitializeQuickStartRuntimeResult.Initialized -> initialized.state
                    is InitializeQuickStartRuntimeResult.Existing -> initialized.state
                    is InitializeQuickStartRuntimeResult.Conflict -> error("Another Quick Start is already active")
                    is InitializeQuickStartRuntimeResult.AlreadyAcknowledged -> error("Quick Start already finished")
                    is InitializeQuickStartRuntimeResult.Invalid -> error(initialized.reason)
                }
                _uiState.value = _uiState.value.copy(sessionPackage = starting, busy = false)
                // Runtime persistence is the commit point; a transport failure is retryable.
                try { receiptClient.send(runtime.startedAcknowledgement) } catch (_: Exception) { Unit }
                onStarted(starting.request.requestId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(busy = false,
                    error = error.message ?: "Could not start workout. Try again.")
            }
        }
    }

    fun dismiss() {
        val offered = _uiState.value.sessionPackage ?: return
        if (_uiState.value.busy || offered.state != QuickStartPackageState.READY) return
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = packageStore.dismiss(
                    offered.request.requestId,
                    offered.request.revision + 1,
                    nowEpochMillis(),
                )
                val terminal = when (result) {
                    is TerminateQuickStartResult.Terminated -> result.terminal
                    is TerminateQuickStartResult.AlreadyTerminal -> result.terminal
                    is TerminateQuickStartResult.RefusedStarting -> error("Quick Start already started")
                    is TerminateQuickStartResult.RevisionMismatch -> error("Quick Start changed. Reopen the app.")
                    TerminateQuickStartResult.Missing -> error("Quick Start is no longer available")
                }
                val acknowledgement = QuickStartAcknowledgement(
                    requestId = terminal.requestId,
                    revision = terminal.revision,
                    targetNodeId = terminal.targetNodeId,
                    status = QuickStartStatus.DISMISSED,
                    watchUpdatedAtMillis = terminal.recordedAtMillis,
                )
                // The terminal decision is durable before it becomes visible to the phone.
                receiptClient.send(acknowledgement)
                _uiState.value = QuickStartOfferUiState(loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(busy = false,
                    error = error.message ?: "Could not dismiss Quick Start. Try again.")
            }
        }
    }

    private suspend fun retryStartedReceipt(runtime: QuickStartRuntimeState) {
        try { receiptClient.send(runtime.startedAcknowledgement) } catch (_: Exception) { Unit }
    }

    private suspend fun retryFinalResult(result: FinalQuickStartResult) {
        try { resultClient?.send(result) } catch (_: Exception) { Unit }
    }

    class Factory(
        private val context: Context,
        private val legacySessions: LegacySessionSnapshotSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val packageStore = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
            return QuickStartOfferViewModel(
                packageStore = packageStore,
                runtimeStore = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context)),
                startGate = GlobalSessionStartGate(legacySessions, packageStore),
                receiptClient = DataLayerQuickStartReceiptClient(context.applicationContext),
                resultClient = DataLayerQuickStartResultClient(context.applicationContext),
            ) as T
        }
    }
}
