package app.personal.workouttracker.quickstart

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.quickstart.*
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicitly paired emulator acceptance; never part of an unattended device run. */
@RunWith(AndroidJUnit4::class)
class PairedQuickStartTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val json = Json { ignoreUnknownKeys = true }
    private var completedFixtureId: String? = null

    @Test
    fun serveProductionTransportProbe() = runBlocking {
        assumeTrue(args.getString("quickStartPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messages = Wearable.getMessageClient(context)
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        // Prefer an explicit seed; otherwise use the newest completion bound to this peer.
        completedFixtureId = args.getString("completedRequestId") ?: store.recordsWithResultReceipts()
            .lastOrNull { it.request.targetNodeId == peer }?.request?.requestId
        requireNotNull(completedFixtureId) { "Complete a Quick Start on this paired watch before running the matrix" }
        var cancelledFixtureId: String? = null
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId != peer || event.path != PROBE_PATH) return@OnMessageReceivedListener
            scope.launch {
                val command = event.data.toString(Charsets.UTF_8)
                val reply = try {
                    when (command) {
                        "availability" -> when (val value = WatchQuickStartClient(context).availability()) {
                            is WatchQuickStartAvailability.Available -> "available:${value.watchNodeId}"
                            is WatchQuickStartAvailability.Unavailable -> "unavailable:${value.reason}"
                        }
                        "record" -> json.encodeToString(requireNotNull(store.current(requiredRequestId())))
                        "fresh_offer" -> {
                            val source = requireNotNull(store.current(requiredRequestId())).request
                            val now = System.currentTimeMillis()
                            val request = source.copy(requestId = UUID.randomUUID().toString(),
                                title = "Transport validation", source = QuickStartSource.SINGLE,
                                createdAtMillis = now, expiresAtMillis = now + QUICK_START_TTL_MILLIS,
                                exercises = listOf(source.exercises.first().copy(sets = 1)))
                            store.saveRequest(request, now)
                            WatchQuickStartClient(context).send(request)
                            withTimeout(15_000) {
                                while (store.current(request.requestId)?.acknowledgement?.status != QuickStartStatus.READY) delay(200)
                            }
                            completedFixtureId = request.requestId
                            "offered:${request.requestId}"
                        }
                        "await_completed" -> {
                            withTimeout(15_000) {
                                while (store.current(requiredRequestId())?.resultReceipt == null) delay(200)
                            }
                            "completed:${requiredRequestId()}"
                        }
                        "replay_completed" -> {
                            verifyReplayAndCleanup(peer, store, requiredRequestId(), completed = true)
                            "replay:passed"
                        }
                        "fresh_cancel" -> {
                            cancelledFixtureId = createCancelledFixture(peer, store)
                            "cancelled:$cancelledFixtureId"
                        }
                        "replay_cancelled" -> {
                            verifyReplayAndCleanup(peer, store, requireNotNull(cancelledFixtureId), completed = false)
                            "replay:passed"
                        }
                        "finish" -> "finished"
                        else -> error("Unknown probe command")
                    }
                } catch (failure: Throwable) {
                    val remaining = items().map { it.first.toString() }
                    "error:$command:${failure.javaClass.simpleName}:${failure.message}:items=$remaining"
                }
                messages.sendMessage(peer, REPLY_PATH, reply.toByteArray()).await()
                if (command == "finish") done.countDown()
            }
        }
        messages.addListener(listener).await()
        try {
            assertTrue("Wear test did not finish the paired probe", done.await(180, TimeUnit.SECONDS))
        } finally {
            messages.removeListener(listener).await()
            scope.cancel()
        }
    }

    private fun requiredRequestId() = completedFixtureId ?: requireNotNull(args.getString("completedRequestId"))

    private suspend fun createCancelledFixture(peer: String, store: QuickStartPhoneStore): String {
        val source = requireNotNull(store.current(requiredRequestId())).request
        val now = System.currentTimeMillis()
        val request = source.copy(requestId = UUID.randomUUID().toString(), targetNodeId = peer,
            createdAtMillis = now, expiresAtMillis = now + QUICK_START_TTL_MILLIS)
        val client = WatchQuickStartClient(context)
        val local = Wearable.getNodeClient(context).localNode.await().id
        store.saveRequest(request, now)
        client.send(request)
        withTimeout(15_000) {
            while (store.current(request.requestId)?.acknowledgement?.status != QuickStartStatus.READY) delay(200)
        }
        client.sendCancellation(store.prepareCancellation(request.requestId, local, System.currentTimeMillis()))
        withTimeout(15_000) {
            while (store.current(request.requestId)?.acknowledgement?.status != QuickStartStatus.CANCELLED) delay(200)
        }
        return request.requestId
    }

    private suspend fun verifyReplayAndCleanup(peer: String, store: QuickStartPhoneStore, requestId: String, completed: Boolean) {
        val record = requireNotNull(store.current(requestId))
        require(record.request.targetNodeId == peer)
        if (completed) require(record.resultReceipt != null)
        else require(record.acknowledgement?.status == QuickStartStatus.CANCELLED)
        val local = Wearable.getNodeClient(context).localNode.await().id
        val client = Wearable.getDataClient(context)
        val ownedPaths = mutableListOf<String>()
        suspend fun put(request: QuickStartRequest) {
            val path = QuickStartDataLayerPaths.REQUEST_PREFIX + request.requestId
            ownedPaths += path
            client.putDataItem(PutDataMapRequest.create(path).apply {
                dataMap.putString("payload", json.encodeToString(request))
                dataMap.putString("validationDeliveryId", UUID.randomUUID().toString())
            }.asPutDataRequest().setUrgent()).await()
        }
        try {
            withTimeout(15_000) {
                while (items().any { it.first.path?.split('/')?.contains(requestId) == true }) delay(200)
            }
            put(record.request)
            if (completed) {
                delay(2_000)
                assertTrue(items().none { it.first.path == QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + requestId })
            } else {
                withTimeout(15_000) {
                    while (items().any { it.first.path?.split('/')?.contains(requestId) == true }) delay(200)
                }
            }
            assertEquals(record, store.current(requestId))
            val wrongTarget = record.request.copy(requestId = UUID.randomUUID().toString(), targetNodeId = "other-watch")
            put(wrongTarget)
            delay(2_000)
            assertTrue(items().none { it.first.path == QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + wrongTarget.requestId })
            assertEquals(record, store.current(requestId))
        } finally {
            for (path in ownedPaths) client.deleteDataItems(Uri.parse("wear://$local$path")).await()
        }
    }

    private suspend fun items(): List<Pair<Uri, String?>> {
        val buffer = Wearable.getDataClient(context).dataItems.await()
        return try { buffer.map { it.uri to DataMapItem.fromDataItem(it).dataMap.getString("payload") } }
        finally { buffer.release() }
    }

    companion object {
        const val PROBE_PATH = "/validation/quick-start/probe"
        const val REPLY_PATH = "/validation/quick-start/reply"
    }
}
