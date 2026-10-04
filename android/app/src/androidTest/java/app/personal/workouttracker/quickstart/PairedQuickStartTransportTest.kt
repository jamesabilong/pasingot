package app.personal.workouttracker.quickstart

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.MainActivity
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File
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
        if (args.getString("quickStartRecoveryUiPairedValidation") == "true" ||
            args.getString("quickStartLifecycleUiPairedValidation") == "true") {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val avdName = ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("getprop ro.boot.qemu.avd_name")
            ).bufferedReader().use { it.readText().trim() }
            assertEquals("Pasingot_Matrix_Phone", avdName)
        }
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messages = Wearable.getMessageClient(context)
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        val beforeReceipts = store.recordsWithResultReceipts()
        val beforeRecords = store.recordsForTransportRecovery()
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
                        "ui_offer", "ui_recovery_offer", "ui_lifecycle_offer" -> {
                            val recovery = command == "ui_recovery_offer"
                            val lifecycle = command == "ui_lifecycle_offer"
                            check(args.getString(when {
                                lifecycle -> "quickStartLifecycleUiPairedValidation"
                                recovery -> "quickStartRecoveryUiPairedValidation"
                                else -> "quickStartUiPairedValidation"
                            }) == "true")
                            val source = requireNotNull(store.current(requiredRequestId())).request
                            val exercise = source.exercises.first()
                            val now = System.currentTimeMillis()
                            val request = source.copy(requestId = UUID.randomUUID().toString(),
                                title = when { lifecycle -> "Emulator lifecycle acceptance"
                                    recovery -> "Emulator cue recovery" else -> "Emulator UI acceptance" }, source = QuickStartSource.LIBRARY_SELECTION,
                                createdAtMillis = now, expiresAtMillis = now + QUICK_START_TTL_MILLIS,
                                exercises = if (lifecycle) listOf("Lifecycle A", "Lifecycle B").mapIndexed { index, name ->
                                    exercise.copy(itemId = "lifecycle-$index", exerciseName = name, sets = 1,
                                        prescription = "8", restSeconds = 0, loadWeight = null, loadUnit = null)
                                } else if (recovery) listOf(0, 3, 5, 6, 8, 10, 12, 20).mapIndexed { index, rest ->
                                    exercise.copy(itemId = "recovery-$index", exerciseName = "Rest $rest", sets = 2,
                                        prescription = "8", restSeconds = rest, loadWeight = null, loadUnit = null)
                                } else listOf(
                                    exercise.copy(itemId = "ui-a", exerciseName = "Emulator UI A", sets = 2,
                                        prescription = "8-10", restSeconds = 12, loadWeight = 2.5, loadUnit = "kg"),
                                    exercise.copy(itemId = "ui-b", exerciseName = "Emulator UI B", sets = 1,
                                        prescription = "20 sec", restSeconds = 0, loadWeight = null, loadUnit = null),
                                ))
                            store.saveRequest(request, now)
                            WatchQuickStartClient(context).send(request)
                            withTimeout(15_000) {
                                while (store.current(request.requestId)?.acknowledgement?.status != QuickStartStatus.READY) delay(200)
                            }
                            completedFixtureId = request.requestId
                            if (lifecycle) {
                                val folder = File(context.getExternalFilesDir(null), "lifecycle-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            json.encodeToString(request)
                        }
                        "ui_cancel_pending" -> {
                            check(args.getString("quickStartUiPairedValidation") == "true")
                            val pending = store.recordsForTransportRecovery().lastOrNull {
                                it.request.title == "Emulator UI acceptance" && it.request.targetNodeId == peer &&
                                    it.acknowledgement?.status == QuickStartStatus.READY
                            }
                            if (pending != null) {
                                val local = Wearable.getNodeClient(context).localNode.await().id
                                WatchQuickStartClient(context).sendCancellation(
                                    store.prepareCancellation(pending.request.requestId, local, System.currentTimeMillis()))
                                withTimeout(15_000) {
                                    while (store.current(pending.request.requestId)?.acknowledgement?.status != QuickStartStatus.CANCELLED) delay(100)
                                }
                            }
                            "pending:cleared"
                        }
                        "ui_started" -> {
                            check(args.getString("quickStartUiPairedValidation") == "true" ||
                                args.getString("quickStartRecoveryUiPairedValidation") == "true" ||
                                args.getString("quickStartLifecycleUiPairedValidation") == "true")
                            withTimeout(15_000) {
                                while (store.current(requiredRequestId())?.acknowledgement?.status != QuickStartStatus.STARTED) delay(200)
                            }
                            "started:${requiredRequestId()}"
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
                        "finish" -> {
                            if (args.getString("quickStartRecoveryUiPairedValidation") == "true" ||
                                args.getString("quickStartLifecycleUiPairedValidation") == "true") {
                                for (record in beforeRecords) assertEquals(record, store.current(record.request.requestId))
                                assertEquals(beforeRecords.size + 1, store.recordsForTransportRecovery().size)
                                store.current(requiredRequestId())?.resultReceipt?.let {
                                    assertEquals(beforeReceipts.size + 1, store.recordsWithResultReceipts().size)
                                }
                            }
                            "finished"
                        }
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
            val timeout = if (args.getString("quickStartRecoveryUiPairedValidation") == "true") 360L
                else if (args.getString("quickStartUiPairedValidation") == "true") 240L else 180L
            assertTrue("Wear test did not finish the paired probe", done.await(timeout, TimeUnit.SECONDS))
        } finally {
            messages.removeListener(listener).await()
            scope.cancel()
        }
    }

    @Test fun verifyLifecycleReceiptAfterReconnect() = runBlocking {
        assumeTrue(args.getString("quickStartLifecycleReceiptValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val name = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("getprop ro.boot.qemu.avd_name"))
            .bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Phone", name)
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val requestId = UUID.fromString(requireNotNull(args.getString("lifecycleRequestId"))).toString()
        val folder = File(context.getExternalFilesDir(null), "lifecycle-acceptance/$requestId")
        val before = json.decodeFromString<List<PhoneQuickStartRecord>>(File(folder, "before-records.json").readText())
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        withTimeout(45_000) { while (store.current(requestId)?.resultReceipt == null) delay(100) }
        val record = requireNotNull(store.current(requestId))
        assertEquals("Emulator lifecycle acceptance", record.request.title)
        assertEquals(requireNotNull(args.getString("lifecycleResultId")), record.finalResult?.resultId)
        assertEquals(record.finalResult?.resultId, record.resultReceipt?.resultId)
        assertEquals(2, record.finalResult?.snapshot?.exercises?.sumOf { it.completedSets })
        assertNotNull(record.finalResult?.summary)
        assertNull(record.finalResult?.endedSummary)
        for (prior in before) assertEquals(prior, store.current(prior.request.requestId))
        assertEquals(before.size + 1, store.recordsForTransportRecovery().size)
        assertEquals(before.count { it.resultReceipt != null } + 1, store.recordsWithResultReceipts().size)
        File(folder, "after-record.json").writeText(json.encodeToString(record))
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
            // Retained orphan receipts are retried by real phone launch/resume recovery.
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                withTimeout(15_000) {
                    while (items().any { it.first.path?.split('/')?.contains(requestId) == true }) delay(200)
                }
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
