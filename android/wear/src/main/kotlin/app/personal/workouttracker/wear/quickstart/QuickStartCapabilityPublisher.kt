package app.personal.workouttracker.wear.quickstart

import android.content.Context
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartNodeRole
import app.personal.workouttracker.shared.quickstart.encodeQuickStartCapability
import app.personal.workouttracker.shared.quickstart.localQuickStartCapability
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object QuickStartCapabilityPublisher {
    suspend fun publish(context: Context) {
        val localNodeId = Wearable.getNodeClient(context).localNode.await().id
        val payload = encodeQuickStartCapability(
            localQuickStartCapability(localNodeId, QuickStartNodeRole.WATCH),
        )
        val item = PutDataMapRequest.create(QuickStartDataLayerPaths.CAPABILITY).apply {
            dataMap.putString("payload", payload)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }
}
