package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import kotlinx.coroutines.CancellationException

/** Commits READY -> STARTING, runtime creation, then best-effort Started receipt. */
class QuickStartStartCoordinator(
    private val startGate: GlobalSessionStartGate,
    private val runtimeStore: QuickStartRuntimeStore,
    private val receiptClient: QuickStartReceiptClient,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val offerNotifier: QuickStartOfferNotifier = NoOpQuickStartOfferNotifier,
) {
    suspend fun start(requestId: String, revision: Long): QuickStartRuntimeState {
        val gated = startGate.startQuickStart(requestId, revision, nowEpochMillis())
        val starting = when (gated) {
            is QuickStartGateResult.Started -> gated.sessionPackage
            is QuickStartGateResult.AlreadyStarting -> gated.sessionPackage
            is QuickStartGateResult.BlockedByLegacy -> error("Finish your current workout first")
            QuickStartGateResult.Expired -> error("Quick Start expired. Send it again from your phone.")
            QuickStartGateResult.Missing -> error("Quick Start is no longer available")
        }
        runCatching { offerNotifier.cancel() }
        val startedAt = nowEpochMillis()
        val initialized = runtimeStore.initialize(
            starting,
            SessionState(
                workoutEntryId = requestId,
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
        try { receiptClient.send(runtime.startedAcknowledgement) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { Unit }
        return runtime
    }
}
