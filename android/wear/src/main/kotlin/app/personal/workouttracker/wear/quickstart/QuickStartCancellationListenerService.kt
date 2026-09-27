package app.personal.workouttracker.wear.quickstart

import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartCancellationDecodeResult
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.decodeQuickStartCancellationEnvelope
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class QuickStartCancellationCoordinator(
    private val gate: GlobalSessionStartGate,
    private val receiptClient: QuickStartReceiptClient,
) {
    suspend fun receive(
        payload: String,
        path: String,
        observedPhoneNodeId: String,
        localWatchNodeId: String,
        nowEpochMillis: Long,
    ): QuickStartAcknowledgement? {
        val cancellation = when (val decoded = decodeQuickStartCancellationEnvelope(
            payload, path, observedPhoneNodeId, localWatchNodeId,
        )) {
            is QuickStartCancellationDecodeResult.Accepted -> decoded.cancellation
            is QuickStartCancellationDecodeResult.Rejected -> return null
        }
        val terminal = when (val result = gate.cancelQuickStart(
            cancellation, observedPhoneNodeId, nowEpochMillis,
        )) {
            is TerminateQuickStartResult.Terminated -> result.terminal
            is TerminateQuickStartResult.AlreadyTerminal -> result.terminal
            is TerminateQuickStartResult.RefusedStarting,
            is TerminateQuickStartResult.RevisionMismatch,
            TerminateQuickStartResult.Missing -> return null
        }
        val acknowledgement = QuickStartAcknowledgement(
            requestId = terminal.requestId,
            revision = terminal.revision,
            targetNodeId = terminal.targetNodeId,
            status = terminal.status,
            reason = terminal.reason,
            watchUpdatedAtMillis = terminal.recordedAtMillis,
        )
        receiptClient.send(acknowledgement)
        return acknowledgement
    }
}

/** Phone cancellation can dismiss READY, but can never erase STARTING. */
class QuickStartCancellationListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val context = applicationContext
            val packageStore = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
            val coordinator = QuickStartCancellationCoordinator(
                GlobalSessionStartGate(
                    WorkoutRepositorySessionSnapshotSource(
                        app.personal.workouttracker.wear.data.WorkoutRepository(context),
                    ),
                    packageStore,
                ),
                DataLayerQuickStartReceiptClient(context),
            )
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                if (!path.startsWith(QuickStartDataLayerPaths.CANCELLATION_PREFIX)) continue
                val sender = item.uri.host ?: continue
                val payload = try { DataMapItem.fromDataItem(item).dataMap.getString("payload") }
                catch (error: Exception) {
                    Log.w(TAG, "Unreadable Quick Start cancellation", error)
                    null
                } ?: continue
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(10_000) {
                            val localNodeId = Wearable.getNodeClient(context).localNode.await().id
                            coordinator.receive(
                                payload, path, sender, localNodeId, System.currentTimeMillis(),
                            )?.let { QuickStartOfferEvents.notifyChanged() }
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Could not process Quick Start cancellation", error)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private companion object { const val TAG = "QuickStartCancel" }
}
