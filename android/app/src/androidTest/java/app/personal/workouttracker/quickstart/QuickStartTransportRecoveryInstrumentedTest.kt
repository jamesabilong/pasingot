package app.personal.workouttracker.quickstart

import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.MainActivity
import app.personal.workouttracker.shared.quickstart.*
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in emulator fixture: existing consumed history, actual Data Items and plugin lifecycle. */
@RunWith(AndroidJUnit4::class)
class QuickStartTransportRecoveryInstrumentedTest {
    @Test fun terminalCleanupRetriesOnLaunchAndResumeWithoutRecreatingHistory() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartRecoveryValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val client = WatchQuickStartClient(context)
        val local = client.localNodeId()
        val persistence = DataStoreQuickStartPhonePersistence(context)
        val store = QuickStartPhoneStore(persistence)
        val retainedPaths = client.dataItems().map { it.path }.toSet()
        val records = store.recordsForTransportRecovery().filter { record ->
            record.request.targetNodeId == peer && record.resultReceipt?.phoneNodeId == local &&
                quickStartResultPath(record.request.requestId, requireNotNull(record.finalResult).resultId) !in retainedPaths
        }.takeLast(2)
        assertEquals("Complete two emulator fixtures before running recovery acceptance", 2, records.size)
        val before = persistence.read()
        val ownedPaths = mutableSetOf<String>()
        val data = Wearable.getDataClient(context)
        suspend fun put(path: String, payload: String) {
            ownedPaths += path
            data.putDataItem(PutDataMapRequest.create(path).apply {
                dataMap.putString("payload", payload)
                dataMap.putString("validationDeliveryId", UUID.randomUUID().toString())
            }.asPutDataRequest().setUrgent()).await()
        }
        suspend fun addOffers() {
            for (record in records) {
                // Invalid payloads leave watch state untouched; only durable phone
                // terminal decisions may authorize cleanup of these exact paths.
                put(QuickStartDataLayerPaths.REQUEST_PREFIX + record.request.requestId, "{")
                put(quickStartCancellationPath(record.request.requestId), "{")
            }
        }
        suspend fun awaitRemoved(paths: Set<String>) = withTimeout(15_000) {
            while (client.dataItems().any { it.nodeId == local && it.path in paths }) delay(200)
        }
        val receipt = requireNotNull(records.first().resultReceipt)
        val receiptPath = quickStartResultReceiptPath(receipt.requestId, receipt.resultId)
        fun receiptPayload(value: QuickStartResultReceipt) = encodeQuickStartResultReceiptEnvelope(
            QuickStartResultReceiptEnvelope(QUICK_START_RESULT_SCHEMA_VERSION, peer,
                QuickStartResultReceiptStatus.PERSISTED, value))
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            addOffers()
            val blockedPath = quickStartCancellationPath(records.first().request.requestId)
            val failures = mutableListOf<String>()
            val failOnce = object : QuickStartPhoneTransport by client {
                override suspend fun cleanupTerminalOffer(requestId: String, watchNodeId: String) {
                    client.deleteItem(local, QuickStartDataLayerPaths.REQUEST_PREFIX + requestId)
                    if (quickStartCancellationPath(requestId) == blockedPath) throw IOException("Injected partial deletion")
                    client.deleteItem(local, quickStartCancellationPath(requestId))
                    client.deleteItem(watchNodeId, QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + requestId)
                }
            }
            QuickStartPhoneTransportRecovery(store, failOnce, onFailure = { id, _ -> failures += id }).reconcile()
            assertEquals(listOf(records.first().request.requestId), failures)
            assertTrue(client.dataItems().any { it.nodeId == local && it.path == blockedPath })
            put(receiptPath, receiptPayload(receipt))
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
            awaitRemoved(ownedPaths.toSet())
            assertEquals(before, persistence.read())

            scenario.moveToState(Lifecycle.State.CREATED)
            addOffers()
            // A timestamp conflict must survive; it cannot prove watch cleanup.
            put(receiptPath, receiptPayload(receipt.copy(receivedAtMillis = receipt.receivedAtMillis + 1)))
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitRemoved(ownedPaths - receiptPath)
            delay(1_000)
            assertTrue(client.dataItems().any { it.nodeId == local && it.path == receiptPath })
            client.deleteItem(local, receiptPath)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            delay(1_000)
            assertTrue(client.dataItems().none { it.nodeId == local && it.path in ownedPaths })
            assertEquals(before, persistence.read())
        } finally {
            scenario?.close()
            for (path in ownedPaths) client.deleteItem(local, path)
        }
    }
}
