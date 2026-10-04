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
            args.getString("quickStartLifecycleUiPairedValidation") == "true" ||
            args.getString("reducedMotionValidation") == "true" ||
            args.getString("quickStartSpeechUiPairedValidation") == "true" ||
            args.getString("quickStartVoiceMatrixUiValidation") == "true" ||
            args.getString("quickStartVoiceLifecycleUiValidation") == "true" ||
            args.getString("quickStartVoiceProcessUiValidation") == "true" ||
            args.getString("quickStartVoiceRestProcessUiValidation") == "true" ||
            args.getString("quickStartVoicePendingRestProcessUiValidation") == "true" ||
            args.getString("quickStartVoicePausedRestProcessUiValidation") == "true" ||
            args.getString("quickStartVoiceLockedRestProcessUiValidation") == "true") {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val avdName = ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("getprop ro.boot.qemu.avd_name")
            ).bufferedReader().use { it.readText().trim() }
            assertEquals("Pasingot_Matrix_Phone", avdName)
        }
        val peer = requireNotNull(args.getString("peerNodeId"))
        if (args.getString("quickStartVoicePausedRestProcessUiValidation") == "true" ||
            args.getString("quickStartVoiceLockedRestProcessUiValidation") == "true") withTimeout(30_000) {
            while (Wearable.getNodeClient(context).connectedNodes.await().map { it.id } != listOf(peer)) delay(200)
        }
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messages = Wearable.getMessageClient(context)
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        var beforeReceipts = store.recordsWithResultReceipts()
        var beforeRecords = store.recordsForTransportRecovery()
        // Prefer an explicit seed; otherwise use the newest completion bound to this peer.
        completedFixtureId = args.getString("completedRequestId") ?: store.recordsWithResultReceipts()
            .lastOrNull { it.request.targetNodeId == peer }?.request?.requestId
        requireNotNull(completedFixtureId) { "Complete a Quick Start on this paired watch before running the matrix" }
        var cancelledFixtureId: String? = null
        var cancelledPriorUiRecord: PhoneQuickStartRecord? = null
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId != peer || event.path != PROBE_PATH) return@OnMessageReceivedListener
            scope.launch {
                val command = event.data.toString(Charsets.UTF_8)
                val reply = try {
                    when (command) {
                        "ui_cleanup_complete", "ui_speech_cleanup_complete", "ui_voice_matrix_cleanup_complete", "ui_voice_lifecycle_cleanup_complete" -> {
                            val speech = command == "ui_speech_cleanup_complete"
                            val matrix = command == "ui_voice_matrix_cleanup_complete"
                            val voiceLifecycle = command == "ui_voice_lifecycle_cleanup_complete"
                            check(args.getString(when { voiceLifecycle -> "quickStartVoiceLifecycleUiValidation"; matrix -> "quickStartVoiceMatrixUiValidation"
                                speech -> "quickStartSpeechUiPairedValidation" else -> "reducedMotionValidation" }) == "true")
                            val ownedTitle = when { voiceLifecycle -> "Emulator voice lifecycle"; matrix -> "Emulator voice matrix"
                                speech -> "Emulator UI speech" else -> "Emulator UI acceptance" }
                            for (record in beforeRecords) {
                                if (record.request.title == ownedTitle &&
                                    record.request.targetNodeId == peer &&
                                    record.acknowledgement?.status == QuickStartStatus.STARTED && record.finalResult == null) {
                                    withTimeout(15_000) {
                                        while (store.current(record.request.requestId)?.resultReceipt == null) delay(100)
                                    }
                                    val ended = requireNotNull(store.current(record.request.requestId))
                                    assertEquals(record.request, ended.request)
                                    assertNotNull(ended.finalResult?.endedSummary)
                                } else {
                                    val expected = cancelledPriorUiRecord?.takeIf { it.request.requestId == record.request.requestId } ?: record
                                    assertEquals(expected, store.current(record.request.requestId))
                                }
                            }
                            beforeRecords = store.recordsForTransportRecovery()
                            beforeReceipts = store.recordsWithResultReceipts()
                            cancelledPriorUiRecord = null
                            "cleanup:preserved"
                        }
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
                        "ui_offer", "ui_recovery_offer", "ui_lifecycle_offer", "ui_speech_offer", "ui_voice_matrix_offer", "ui_voice_lifecycle_offer", "ui_voice_process_offer", "ui_voice_rest_process_offer", "ui_voice_pending_rest_process_offer", "ui_voice_paused_rest_process_offer", "ui_voice_locked_rest_process_offer" -> {
                            val recovery = command == "ui_recovery_offer"
                            val lifecycle = command == "ui_lifecycle_offer"
                            val speech = command == "ui_speech_offer"
                            val matrix = command == "ui_voice_matrix_offer"
                            val voiceLifecycle = command == "ui_voice_lifecycle_offer"
                            val voiceProcess = command == "ui_voice_process_offer"
                            val voiceRestProcess = command == "ui_voice_rest_process_offer"
                            val voicePendingRestProcess = command == "ui_voice_pending_rest_process_offer"
                            val voicePausedRestProcess = command == "ui_voice_paused_rest_process_offer"
                            val voiceLockedRestProcess = command == "ui_voice_locked_rest_process_offer"
                            check(args.getString(when {
                                voiceLockedRestProcess -> "quickStartVoiceLockedRestProcessUiValidation"
                                voicePausedRestProcess -> "quickStartVoicePausedRestProcessUiValidation"
                                voicePendingRestProcess -> "quickStartVoicePendingRestProcessUiValidation"
                                voiceRestProcess -> "quickStartVoiceRestProcessUiValidation"
                                voiceProcess -> "quickStartVoiceProcessUiValidation"
                                voiceLifecycle -> "quickStartVoiceLifecycleUiValidation"
                                matrix -> "quickStartVoiceMatrixUiValidation"
                                speech -> "quickStartSpeechUiPairedValidation"
                                lifecycle -> "quickStartLifecycleUiPairedValidation"
                                recovery -> "quickStartRecoveryUiPairedValidation"
                                else -> "quickStartUiPairedValidation"
                            }) == "true")
                            val source = requireNotNull(store.current(requiredRequestId())).request
                            val exercise = source.exercises.first()
                            val now = System.currentTimeMillis()
                            val request = source.copy(requestId = UUID.randomUUID().toString(),
                                title = when { voiceLockedRestProcess -> "Emulator voice locked rest process"; voicePausedRestProcess -> "Emulator voice paused rest process"; voicePendingRestProcess -> "Emulator voice pending rest process"; voiceRestProcess -> "Emulator voice rest process"; voiceProcess -> "Emulator voice process"; voiceLifecycle -> "Emulator voice lifecycle"; matrix -> "Emulator voice matrix"; speech -> "Emulator UI speech"; lifecycle -> "Emulator lifecycle acceptance"
                                    recovery -> "Emulator cue recovery" else -> "Emulator UI acceptance" }, source = QuickStartSource.LIBRARY_SELECTION,
                                createdAtMillis = now, expiresAtMillis = now + QUICK_START_TTL_MILLIS,
                                exercises = if (voiceRestProcess || voicePendingRestProcess || voicePausedRestProcess || voiceLockedRestProcess) listOf(
                                    exercise.copy(itemId = when { voiceLockedRestProcess -> "voice-locked-rest-process"; voicePausedRestProcess -> "voice-paused-rest-process"; voicePendingRestProcess -> "voice-pending-rest-process"; else -> "voice-rest-process" }, exerciseName = "Rest recovery", sets = 2,
                                        prescription = "8", restSeconds = when { voicePausedRestProcess -> 20; voicePendingRestProcess -> 120; else -> 12 }, loadWeight = null, loadUnit = null)
                                ) else if (voiceProcess) listOf("Process A", "Process B").mapIndexed { index, name ->
                                    exercise.copy(itemId = "voice-process-$index", exerciseName = name, sets = 1,
                                        prescription = "8", restSeconds = 0, loadWeight = null, loadUnit = null)
                                } else if (voiceLifecycle) listOf(
                                    exercise.copy(itemId = "voice-lifecycle-a", exerciseName = "Lifecycle sets", sets = 4,
                                        prescription = "8", restSeconds = 12, loadWeight = null, loadUnit = null),
                                    exercise.copy(itemId = "voice-lifecycle-b", exerciseName = "Final exercise", sets = 1,
                                        prescription = "8", restSeconds = 0, loadWeight = null, loadUnit = null),
                                ) else if (matrix) listOf(0, 3, 5, 6, 8, 10, 12, 20).mapIndexed { index, rest ->
                                    exercise.copy(itemId = "voice-matrix-$index", exerciseName = "Rest $rest", sets = 2,
                                        prescription = "8", restSeconds = rest, loadWeight = null, loadUnit = null)
                                } else if (speech) listOf(
                                    exercise.copy(itemId = "speech-a", exerciseName = "Controlled repetitions", sets = 5,
                                        prescription = "8", restSeconds = 60, loadWeight = null, loadUnit = null),
                                    exercise.copy(itemId = "speech-b", exerciseName = "Next exercise", sets = 1,
                                        prescription = "8", restSeconds = 0, loadWeight = null, loadUnit = null),
                                ) else if (lifecycle) listOf("Lifecycle A", "Lifecycle B").mapIndexed { index, name ->
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
                            if (args.getString("reducedMotionValidation") == "true") {
                                val folder = File(context.getExternalFilesDir(null), "presentation-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (speech) {
                                val folder = File(context.getExternalFilesDir(null), "speech-ui-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (matrix) {
                                val folder = File(context.getExternalFilesDir(null), "voice-matrix-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (voiceLifecycle) {
                                val folder = File(context.getExternalFilesDir(null), "voice-lifecycle-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (lifecycle) {
                                val folder = File(context.getExternalFilesDir(null), "lifecycle-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (voiceProcess) {
                                val folder = File(context.getExternalFilesDir(null), "voice-process-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (voiceRestProcess) {
                                val folder = File(context.getExternalFilesDir(null), "voice-rest-process-acceptance/${request.requestId}").apply { mkdirs() }
                                File(folder, "before-records.json").writeText(json.encodeToString(beforeRecords))
                            }
                            if (voicePendingRestProcess || voicePausedRestProcess || voiceLockedRestProcess) {
                                val folder = File(context.getExternalFilesDir(null), "${when { voiceLockedRestProcess -> "voice-locked-rest-process"; voicePausedRestProcess -> "voice-paused-rest-process"; else -> "voice-pending-rest-process" }}-acceptance/${request.requestId}").apply { mkdirs() }
                                // This witness must survive stopping the copied phone AVD.
                                File(folder, "before-records.json").outputStream().use { stream ->
                                    stream.write(json.encodeToString(beforeRecords).toByteArray(Charsets.UTF_8))
                                    stream.fd.sync()
                                }
                            }
                            json.encodeToString(request)
                        }
                        "ui_cancel_pending" -> {
                            val speech = args.getString("quickStartSpeechUiPairedValidation") == "true"
                            val matrix = args.getString("quickStartVoiceMatrixUiValidation") == "true"
                            val voiceLifecycle = args.getString("quickStartVoiceLifecycleUiValidation") == "true"
                            check(voiceLifecycle || matrix || speech || args.getString("quickStartUiPairedValidation") == "true")
                            val pending = store.recordsForTransportRecovery().lastOrNull {
                                it.request.title == (when { voiceLifecycle -> "Emulator voice lifecycle"; matrix -> "Emulator voice matrix"
                                    speech -> "Emulator UI speech" else -> "Emulator UI acceptance" }) && it.request.targetNodeId == peer &&
                                    it.acknowledgement?.status == QuickStartStatus.READY
                            }
                            if (pending != null) {
                                val local = Wearable.getNodeClient(context).localNode.await().id
                                WatchQuickStartClient(context).sendCancellation(
                                    store.prepareCancellation(pending.request.requestId, local, System.currentTimeMillis()))
                                withTimeout(15_000) {
                                    while (store.current(pending.request.requestId)?.acknowledgement?.status != QuickStartStatus.CANCELLED) delay(100)
                                }
                                cancelledPriorUiRecord = store.current(pending.request.requestId)
                            }
                            "pending:cleared"
                        }
                        "ui_started" -> {
                            check(args.getString("quickStartUiPairedValidation") == "true" ||
                                args.getString("quickStartVoiceRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoicePendingRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoicePausedRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceLockedRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceLifecycleUiValidation") == "true" ||
                                args.getString("quickStartVoiceMatrixUiValidation") == "true" ||
                                args.getString("quickStartSpeechUiPairedValidation") == "true" ||
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
                                args.getString("quickStartVoiceLockedRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoicePausedRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoicePendingRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceRestProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceProcessUiValidation") == "true" ||
                                args.getString("quickStartVoiceLifecycleUiValidation") == "true" ||
                                args.getString("quickStartVoiceMatrixUiValidation") == "true" ||
                                args.getString("quickStartLifecycleUiPairedValidation") == "true" ||
                                args.getString("quickStartSpeechUiPairedValidation") == "true" ||
                                args.getString("reducedMotionValidation") == "true") {
                                for (record in beforeRecords) {
                                    val expected = cancelledPriorUiRecord?.takeIf { it.request.requestId == record.request.requestId } ?: record
                                    assertEquals(expected, store.current(record.request.requestId))
                                }
                                assertEquals(beforeRecords.size + 1, store.recordsForTransportRecovery().size)
                                store.current(requiredRequestId())?.resultReceipt?.let {
                                    assertEquals(beforeReceipts.size + 1, store.recordsWithResultReceipts().size)
                                }
                                if (args.getString("reducedMotionValidation") == "true") {
                                    val folder = File(context.getExternalFilesDir(null), "presentation-acceptance/${requiredRequestId()}")
                                    File(folder, "after-records.json").writeText(json.encodeToString(store.recordsForTransportRecovery()))
                                }
                                if (args.getString("quickStartSpeechUiPairedValidation") == "true") {
                                    val folder = File(context.getExternalFilesDir(null), "speech-ui-acceptance/${requiredRequestId()}")
                                    File(folder, "after-records.json").writeText(json.encodeToString(store.recordsForTransportRecovery()))
                                }
                                if (args.getString("quickStartVoiceMatrixUiValidation") == "true") {
                                    val folder = File(context.getExternalFilesDir(null), "voice-matrix-acceptance/${requiredRequestId()}")
                                    File(folder, "after-records.json").writeText(json.encodeToString(store.recordsForTransportRecovery()))
                                }
                                if (args.getString("quickStartVoiceLifecycleUiValidation") == "true") {
                                    val folder = File(context.getExternalFilesDir(null), "voice-lifecycle-acceptance/${requiredRequestId()}")
                                    File(folder, "after-records.json").writeText(json.encodeToString(store.recordsForTransportRecovery()))
                                }
                            }
                            "finished"
                        }
                        else -> error("Unknown probe command")
                    }
                } catch (failure: Throwable) {
                    android.util.Log.e("QuickStartProbe", "Probe failed: $command", failure)
                    val remaining = items().map { it.first.toString() }
                    "error:$command:${failure.javaClass.simpleName}:${failure.message}:items=$remaining"
                }
                messages.sendMessage(peer, REPLY_PATH, reply.toByteArray()).await()
                if (command == "finish") done.countDown()
            }
        }
        messages.addListener(listener).await()
        try {
            val timeout = if (args.getString("quickStartRecoveryUiPairedValidation") == "true" ||
                args.getString("quickStartVoiceLifecycleUiValidation") == "true" ||
                args.getString("quickStartVoiceMatrixUiValidation") == "true" ||
                args.getString("quickStartSpeechUiPairedValidation") == "true") 360L
                else if (args.getString("quickStartUiPairedValidation") == "true") 240L else 180L
            assertTrue("Wear test did not finish the paired probe", done.await(timeout, TimeUnit.SECONDS))
        } finally {
            messages.removeListener(listener).await()
            scope.cancel()
        }
    }

    @Test fun verifyLifecycleReceiptAfterReconnect() = runBlocking {
        val voiceProcess = args.getString("quickStartVoiceProcessUiValidation") == "true"
        val voiceRestProcess = args.getString("quickStartVoiceRestProcessUiValidation") == "true"
        val voicePendingRestProcess = args.getString("quickStartVoicePendingRestProcessUiValidation") == "true"
        val voicePausedRestProcess = args.getString("quickStartVoicePausedRestProcessUiValidation") == "true"
        val voiceLockedRestProcess = args.getString("quickStartVoiceLockedRestProcessUiValidation") == "true"
        assumeTrue(args.getString("quickStartLifecycleReceiptValidation") == "true" || voiceProcess || voiceRestProcess || voicePendingRestProcess || voicePausedRestProcess || voiceLockedRestProcess)
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val name = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("getprop ro.boot.qemu.avd_name"))
            .bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Phone", name)
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val requestId = UUID.fromString(requireNotNull(args.getString("lifecycleRequestId"))).toString()
        val folder = File(context.getExternalFilesDir(null), "${when { voiceLockedRestProcess -> "voice-locked-rest-process"; voicePausedRestProcess -> "voice-paused-rest-process"; voicePendingRestProcess -> "voice-pending-rest-process"; voiceRestProcess -> "voice-rest-process"; voiceProcess -> "voice-process"; else -> "lifecycle" }}-acceptance/$requestId")
        val before = json.decodeFromString<List<PhoneQuickStartRecord>>(File(folder, "before-records.json").readText())
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        withTimeout(45_000) { while (store.current(requestId)?.resultReceipt == null) delay(100) }
        val record = requireNotNull(store.current(requestId))
        assertEquals(when { voiceLockedRestProcess -> "Emulator voice locked rest process"; voicePausedRestProcess -> "Emulator voice paused rest process"; voicePendingRestProcess -> "Emulator voice pending rest process"; voiceRestProcess -> "Emulator voice rest process"; voiceProcess -> "Emulator voice process"; else -> "Emulator lifecycle acceptance" }, record.request.title)
        assertEquals(requireNotNull(args.getString("lifecycleResultId")), record.finalResult?.resultId)
        assertEquals(record.finalResult?.resultId, record.resultReceipt?.resultId)
        assertEquals(2, record.finalResult?.snapshot?.exercises?.sumOf { it.completedSets })
        assertNotNull(record.finalResult?.summary)
        assertNull(record.finalResult?.endedSummary)
        for (prior in before) assertEquals(prior, store.current(prior.request.requestId))
        assertEquals(before.size + 1, store.recordsForTransportRecovery().size)
        assertEquals(before.count { it.resultReceipt != null } + 1, store.recordsWithResultReceipts().size)
        File(folder, "after-record.json").writeText(json.encodeToString(record))
        if (voiceProcess || voiceRestProcess || voicePendingRestProcess || voicePausedRestProcess || voiceLockedRestProcess) File(folder, "after-records.json").writeText(json.encodeToString(store.recordsForTransportRecovery()))
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
