package app.personal.workouttracker.quickstart

import android.content.Context
import app.personal.workouttracker.shared.quickstart.QuickStartCapabilityDecision
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartNodeRole
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.localQuickStartCapability
import app.personal.workouttracker.shared.quickstart.negotiateQuickStartCapability
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
}
