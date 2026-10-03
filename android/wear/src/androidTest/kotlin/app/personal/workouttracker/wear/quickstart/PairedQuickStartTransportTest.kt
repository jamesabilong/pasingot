package app.personal.workouttracker.wear.quickstart

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Starts only with explicit opt-in and exact peer identity, on an emulator. */
@RunWith(AndroidJUnit4::class)
class PairedQuickStartTransportTest {
    @Test
    fun capabilityBindingAndTerminalReplay() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        val local = Wearable.getNodeClient(context).localNode.await().id
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val json = Json { ignoreUnknownKeys = true }
        val data = Wearable.getDataClient(context)
        val messages = Wearable.getMessageClient(context)
        val replies = Channel<String>(Channel.UNLIMITED)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId == peer && event.path == REPLY_PATH) replies.trySend(event.data.toString(Charsets.UTF_8))
        }
        messages.addListener(listener).await()
        suspend fun probe(command: String): String {
            messages.sendMessage(peer, PROBE_PATH, command.toByteArray()).await()
            return withTimeout(30_000) { replies.receive() }.also { assertFalse(it, it.startsWith("error:")) }
        }
        suspend fun publish(payload: String?) {
            if (payload == null) data.deleteDataItems(Uri.parse("wear://$local${QuickStartDataLayerPaths.CAPABILITY}")).await()
            else data.putDataItem(PutDataMapRequest.create(QuickStartDataLayerPaths.CAPABILITY).apply {
                dataMap.putString("payload", payload)
            }.asPutDataRequest().setUrgent()).await()
        }
        val buffer = data.dataItems.await()
        val original = try { buffer.firstOrNull { it.uri.host == local && it.uri.path == QuickStartDataLayerPaths.CAPABILITY }
            ?.let { DataMapItem.fromDataItem(it).dataMap.getString("payload") } } finally { buffer.release() }
        val packages = DataStoreQuickStartPackagePersistence(context)
        val runtime = DataStoreQuickStartRuntimePersistence(context)
        var invalidAckPath: String? = null
        var injectedResultPath: String? = null
        suspend fun hasItem(path: String): Boolean {
            val items = data.dataItems.await()
            return try { items.any { it.uri.path == path } } finally { items.release() }
        }
        suspend fun putFixture(path: String, payload: String) {
            assertFalse("Preserve existing Data Item at $path", hasItem(path))
            data.putDataItem(PutDataMapRequest.create(path).apply {
                dataMap.putString("payload", payload)
                dataMap.putString("validationDeliveryId", UUID.randomUUID().toString())
            }.asPutDataRequest().setUrgent()).await()
        }
        try {
            if (args.getString("quickStartFreshCompletedFixture") == "true") {
                assertTrue("Finish the active legacy emulator workout before creating a completion fixture",
                    WorkoutRepositorySessionSnapshotSource(WorkoutRepository(context)).entries().blockingSessions().isEmpty())
                // Native transport fixture only: no countdown/audio/UI acceptance.
                val id = probe("fresh_offer").removePrefix("offered:")
                val packageStore = WatchSessionPackageStore(packages)
                withTimeout(15_000) {
                    while (packageStore.current(System.currentTimeMillis())?.request?.requestId != id) delay(200)
                }
                val runtimeStore = QuickStartRuntimeStore(runtime)
                val started = QuickStartStartCoordinator(
                    GlobalSessionStartGate(WorkoutRepositorySessionSnapshotSource(WorkoutRepository(context)), packageStore),
                    runtimeStore, DataLayerQuickStartReceiptClient(context),
                ).start(id, requireNotNull(packageStore.current(System.currentTimeMillis())).request.revision)
                val now = System.currentTimeMillis()
                val completed = runtimeStore.transition(id, started.runtimeRevision, started.session,
                    started.session.copy(status = SessionStatus.COMPLETED, elapsedStartedAtEpochMillis = null,
                        accumulatedElapsedMillis = now - requireNotNull(started.session.elapsedStartedAtEpochMillis)),
                    QuickStartRuntimeAction(WorkoutOutcomeTransitionType.SET_COMPLETED, 0), now)
                    as ApplyQuickStartRuntimeResult.Applied
                DataLayerQuickStartResultClient(context).send(requireNotNull(completed.state.finalResult))
                assertEquals("completed:$id", probe("await_completed"))
                withTimeout(15_000) {
                    while (runtimeStore.current() != null || packageStore.current(System.currentTimeMillis()) != null) delay(200)
                }
            }
            val beforePackages = packages.read()
            val beforeRuntime = runtime.read()
            val capability = localQuickStartCapability(local, QuickStartNodeRole.WATCH)
            val variants = listOf(
                null to "unavailable:missing_capability",
                "{" to "unavailable:malformed_capability",
                json.encodeToString(capability.copy(capabilitySchemaVersion = 2)) to "unavailable:unsupported_capability_schema",
                encodeQuickStartCapability(capability.copy(supportedRequestSchemaVersions = listOf(2))) to "unavailable:no_common_request_schema",
                encodeQuickStartCapability(capability.copy(nodeId = "other-watch")) to "unavailable:node_mismatch",
                encodeQuickStartCapability(capability.copy(role = QuickStartNodeRole.PHONE)) to "unavailable:role_mismatch",
                encodeQuickStartCapability(capability) to "available:$local",
            )
            for ((payload, expected) in variants) {
                publish(payload)
                withTimeout(15_000) {
                    while (probe("availability") != expected) delay(200)
                }
            }
            val record = probe("record")
            val acknowledgement = json.decodeFromString<QuickStartAcknowledgement>(
                json.parseToJsonElement(record).jsonObject.getValue("acknowledgement").toString())
            // URI sender is genuinely this watch, but the payload claims another node.
            invalidAckPath = QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + acknowledgement.requestId
            data.putDataItem(PutDataMapRequest.create(invalidAckPath).apply {
                dataMap.putString("payload", json.encodeToString(acknowledgement.copy(targetNodeId = "other-watch")))
                dataMap.putString("validationDeliveryId", UUID.randomUUID().toString())
            }.asPutDataRequest().setUrgent()).await()
            delay(2_000)
            assertEquals(record, probe("record"))
            data.deleteDataItems(Uri.parse("wear://$local$invalidAckPath")).await()
            invalidAckPath = null
            // A valid payload under another request's URI must also be ignored.
            invalidAckPath = QuickStartDataLayerPaths.ACKNOWLEDGEMENT_PREFIX + UUID.randomUUID()
            putFixture(invalidAckPath, json.encodeToString(acknowledgement))
            delay(2_000)
            assertEquals(record, probe("record"))
            data.deleteDataItems(Uri.parse("wear://$local$invalidAckPath")).await()
            invalidAckPath = null

            val finalResult = json.decodeFromString<FinalQuickStartResult>(
                json.parseToJsonElement(record).jsonObject.getValue("finalResult").toString())
            val resultEnvelope = QuickStartResultEnvelope(QUICK_START_RESULT_SCHEMA_VERSION, local, finalResult)
            val resultPath = quickStartResultPath(finalResult.requestId, finalResult.resultId)
            val invalidResults = listOf(
                resultPath to json.encodeToString(resultEnvelope.copy(watchNodeId = "other-watch")),
                resultPath to json.encodeToString(resultEnvelope.copy(result = finalResult.copy(phoneNodeId = "other-phone"))),
                resultPath to json.encodeToString(resultEnvelope.copy(schemaVersion = 2)),
                quickStartResultPath(UUID.randomUUID().toString(), finalResult.resultId) to encodeQuickStartResultEnvelope(resultEnvelope),
            )
            for ((path, payload) in invalidResults) {
                injectedResultPath = path
                putFixture(path, payload)
                delay(2_000)
                assertEquals(record, probe("record"))
                assertEquals(beforeRuntime, runtime.read())
                assertTrue("Invalid result must not be consumed", hasItem(path))
                assertFalse("Invalid result must not receive a persisted receipt",
                    hasItem(quickStartResultReceiptPath(finalResult.requestId, finalResult.resultId)))
                data.deleteDataItems(Uri.parse("wear://$local$path")).await()
                injectedResultPath = null
            }
            assertEquals("replay:passed", probe("replay_completed"))
            delay(500)
            if (args.getString("quickStartTerminalReplayOnly") == "true") {
                // Expired replay protection may add this request's refusal again.
                // Every unrelated package/history field must remain exact.
                fun withoutFixtureRefusal(raw: String?): JsonObject? = raw?.let {
                    val state = json.parseToJsonElement(it).jsonObject
                    JsonObject(state + ("terminalHistory" to JsonArray(
                        state["terminalHistory"]?.jsonArray.orEmpty().filterNot { entry ->
                            entry.jsonObject["requestId"]?.jsonPrimitive?.content == acknowledgement.requestId
                        })))
                }
                assertEquals(withoutFixtureRefusal(beforePackages), withoutFixtureRefusal(packages.read()))
            } else assertEquals(beforePackages, packages.read())
            assertEquals(beforeRuntime, runtime.read())
            if (args.getString("quickStartTerminalReplayOnly") != "true") {
                val cancelledId = probe("fresh_cancel").removePrefix("cancelled:")
                UUID.fromString(cancelledId)
                val cancelledPackages = packages.read()
                val terminal = json.parseToJsonElement(requireNotNull(cancelledPackages)).jsonObject.getValue("terminal").jsonObject
                assertEquals("\"$cancelledId\"", terminal.getValue("requestId").toString())
                assertEquals("\"cancelled\"", terminal.getValue("status").toString())
                assertEquals("replay:passed", probe("replay_cancelled"))
                assertEquals(cancelledPackages, packages.read())
                assertEquals(beforeRuntime, runtime.read())
            }
        } finally {
            invalidAckPath?.let { data.deleteDataItems(Uri.parse("wear://$local$it")).await() }
            injectedResultPath?.let { data.deleteDataItems(Uri.parse("wear://$local$it")).await() }
            publish(original)
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }

    companion object {
        const val PROBE_PATH = "/validation/quick-start/probe"
        const val REPLY_PATH = "/validation/quick-start/reply"
    }
}
