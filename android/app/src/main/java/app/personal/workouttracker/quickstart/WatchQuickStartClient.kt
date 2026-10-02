package app.personal.workouttracker.quickstart

import android.content.Context
import android.net.Uri
import app.personal.workouttracker.shared.quickstart.QuickStartCapabilityDecision
import app.personal.workouttracker.shared.quickstart.QUICK_START_RESULT_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QuickStartCancellation
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartNodeRole
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceipt
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.QuickStartResultReceiptStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.encodeQuickStartCancellation
import app.personal.workouttracker.shared.quickstart.encodeQuickStartResultReceiptEnvelope
import app.personal.workouttracker.shared.quickstart.localQuickStartCapability
import app.personal.workouttracker.shared.quickstart.negotiateQuickStartCapability
import app.personal.workouttracker.shared.quickstart.encodeQuickStartCapability
import app.personal.workouttracker.shared.quickstart.quickStartCancellationPath
import app.personal.workouttracker.shared.quickstart.quickStartResultReceiptPath
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

sealed interface WatchQuickStartAvailability {
    data class Available(val watchNodeId: String) : WatchQuickStartAvailability
    data class Unavailable(val reason: String) : WatchQuickStartAvailability
}

/** The phone selects one connected watch and verifies its published codec. */
class WatchQuickStartClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun availability(): WatchQuickStartAvailability {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) return WatchQuickStartAvailability.Unavailable("disconnected")
        if (nodes.size != 1) return WatchQuickStartAvailability.Unavailable("multiple_watches")
        val selected = nodes.single()
        val local = Wearable.getNodeClient(context).localNode.await()
        val items = Wearable.getDataClient(context).dataItems.await()
        val capabilityPayload = try {
            var found: String? = null
            for (item in items) {
                if (item.uri.path == QuickStartDataLayerPaths.CAPABILITY && item.uri.host == selected.id) {
                    found = DataMapItem.fromDataItem(item).dataMap.getString("payload")
                    break
                }
            }
            found
        } finally {
            items.release()
        }
        return when (val decision = negotiateQuickStartCapability(
            localQuickStartCapability(local.id, QuickStartNodeRole.PHONE),
            capabilityPayload, selected.id, remoteReachable = true,
        )) {
            is QuickStartCapabilityDecision.Compatible -> WatchQuickStartAvailability.Available(selected.id)
            is QuickStartCapabilityDecision.Unavailable ->
                WatchQuickStartAvailability.Unavailable(decision.reason.name.lowercase())
        }
    }

    suspend fun publishCapability() {
        val local = Wearable.getNodeClient(context).localNode.await()
        val item = PutDataMapRequest.create(QuickStartDataLayerPaths.CAPABILITY).apply {
            dataMap.putString("payload", encodeQuickStartCapability(
                localQuickStartCapability(local.id, QuickStartNodeRole.PHONE),
            ))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }

    /** Returns only when DataClient accepts the item; Ready requires a later watch ack. */
    suspend fun send(request: QuickStartRequest) {
        val available = availability() as? WatchQuickStartAvailability.Available
            ?: throw IllegalStateException("Compatible watch not available")
        require(request.targetNodeId == available.watchNodeId) { "Watch selection changed" }
        require(validateQuickStartRequest(request, System.currentTimeMillis()) is QuickStartValidationResult.Valid)
        val path = QuickStartDataLayerPaths.REQUEST_PREFIX + request.requestId
        val item = PutDataMapRequest.create(path).apply {
            dataMap.putString("payload", json.encodeToString(request))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }

    /** Cancellation is durable/offline-capable and remains bound to the original nodes. */
    suspend fun sendCancellation(cancellation: QuickStartCancellation) {
        val local = Wearable.getNodeClient(context).localNode.await()
        require(cancellation.phoneNodeId == local.id) { "Phone identity changed" }
        val item = PutDataMapRequest.create(quickStartCancellationPath(cancellation.requestId)).apply {
            dataMap.putString("payload", encodeQuickStartCancellation(cancellation))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }

    suspend fun sendResultReceipt(receipt: QuickStartResultReceipt, watchNodeId: String) {
        val local = Wearable.getNodeClient(context).localNode.await()
        require(receipt.phoneNodeId == local.id) { "Phone identity changed" }
        val payload = encodeQuickStartResultReceiptEnvelope(QuickStartResultReceiptEnvelope(
            schemaVersion = QUICK_START_RESULT_SCHEMA_VERSION,
            watchNodeId = watchNodeId,
            status = QuickStartResultReceiptStatus.PERSISTED,
            receipt = receipt,
        ))
        val item = PutDataMapRequest.create(
            quickStartResultReceiptPath(receipt.requestId, receipt.resultId),
        ).apply {
            dataMap.putString("payload", payload)
            // Reopening the phone must re-deliver an exact receipt whose cleanup
            // failed on the watch, even when its immutable payload is unchanged.
            dataMap.putString("deliveryId", UUID.randomUUID().toString())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }

    /** Safe only after a matching terminal acknowledgement is durable on the phone. */
    suspend fun cleanupTerminalOffer(requestId: String, watchNodeId: String) {
        val localNodeId = Wearable.getNodeClient(context).localNode.await().id
        val client = Wearable.getDataClient(context)
        val paths = listOf(
            localNodeId to (QuickStartDataLayerPaths.REQUEST_PREFIX + requestId),
            localNodeId to quickStartCancellationPath(requestId),
            watchNodeId to (QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + requestId),
        )
        for ((nodeId, path) in paths) {
            client.deleteDataItems(Uri.parse("wear://$nodeId$path")).await()
        }
    }
}
