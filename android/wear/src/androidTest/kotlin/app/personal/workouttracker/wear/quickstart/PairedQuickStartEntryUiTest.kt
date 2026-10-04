package app.personal.workouttracker.wear.quickstart

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.session.SessionViewModel
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Observes actual packages sent by phone WebView controls; never creates offers. */
@RunWith(AndroidJUnit4::class)
class PairedQuickStartEntryUiTest {
    @Test fun verifyIdleAfterTransportReconnect() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartDisconnectedUiValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val avd = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name"))
            .bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Wear", avd)
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        withTimeout(45_000) {
            while (Wearable.getNodeClient(context).connectedNodes.await().map { it.id } != listOf(peer)) delay(250)
        }
        assertNull(WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context)).current(System.currentTimeMillis()))
        assertNull(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context)).current())
    }

    @Test fun serveStartAndRequestStates() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartStateUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val automation = instrumentation.uiAutomation
        val avd = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("getprop ro.boot.qemu.avd_name"))
            .bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Wear", avd)
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val repository = WorkoutRepository(context)
        val before = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        assertNull(packages.current(System.currentTimeMillis()))
        assertNull(runtime.current())
        val output = File(context.getExternalFilesDir(null), "state-ui-acceptance/${System.currentTimeMillis()}").apply { mkdirs() }
        fun nodes(): List<AccessibilityNodeInfo> {
            automation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) { result += node; for (index in 0 until node.childCount) node.getChild(index)?.let(::visit) }
            automation.rootInActiveWindow?.let(::visit)
            return result
        }
        suspend fun tap(text: String) = withTimeout(15_000) {
            while (true) {
                nodes().firstOrNull { it.text?.toString() == text }?.let { leaf ->
                    var node = leaf
                    while (!node.isClickable) node = node.parent ?: error("No clickable parent")
                    assertTrue(node.isEnabled)
                    assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return@withTimeout
                }
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                delay(150)
            }
        }
        suspend fun capture(name: String) {
            delay(500)
            File(output, "$name.txt").writeText(nodes().joinToString("\n") { "${it.text ?: it.contentDescription ?: ""} enabled=${it.isEnabled}" })
            automation.takeScreenshot()?.let { bitmap ->
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val done = CountDownLatch(1)
        var scenario: ActivityScenario<WearMainActivity>? = null
        val owned = mutableSetOf<String>()
        var started = 0
        var completed = 0
        var expired = 0
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId != peer || event.path != STATE_PROBE_PATH) return@OnMessageReceivedListener
            scope.launch {
                val command = event.data.toString(Charsets.UTF_8)
                val id = command.substringAfter(':', "")
                val reply = try {
                    when {
                        command.startsWith("ready:") -> {
                            val offer = requireNotNull(packages.current(System.currentTimeMillis()))
                            assertEquals(id, offer.request.requestId)
                            assertEquals(QuickStartPackageState.READY, offer.state)
                            assertNull(runtime.current())
                            owned += id
                            Json.encodeToString(offer.request)
                        }
                        command.startsWith("start:") -> {
                            assertTrue(id in owned)
                            scenario?.close()
                            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
                            tap("Start")
                            withTimeout(15_000) { while (runtime.current()?.session?.status != SessionStatus.ACTIVE) delay(100) }
                            assertEquals(id, runtime.current()?.sessionPackage?.request?.requestId)
                            capture("${++started}-started")
                            "started:$id"
                        }
                        command == "snapshot" -> Json.encodeToString(requireNotNull(runtime.current()))
                        command.startsWith("complete:") -> {
                            assertEquals(id, runtime.current()?.sessionPackage?.request?.requestId)
                            tap("Complete set")
                            withTimeout(20_000) { while (runtime.current() != null) delay(100) }
                            assertNull(packages.current(System.currentTimeMillis()))
                            capture("${++completed}-completed")
                            scenario?.close(); scenario = null
                            "completed:$id"
                        }
                        command.startsWith("expire:") -> {
                            assertTrue(id in owned)
                            val offer = requireNotNull(packages.current(System.currentTimeMillis()))
                            assertEquals(id, offer.request.requestId)
                            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
                            val remaining = offer.request.expiresAtMillis + QUICK_START_CLOCK_SKEW_MILLIS + 1_000 - System.currentTimeMillis()
                            assertTrue("Use the actual request deadline", remaining > 0)
                            File(output, "expiry-deadline.txt").writeText("created=${offer.request.createdAtMillis} expires=${offer.request.expiresAtMillis} wait=$remaining")
                            delay(remaining)
                            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 224")).close()
                            // The still-rendered Start action must not create a runtime after expiry.
                            if (nodes().any { it.text?.toString() == "Start" }) tap("Start")
                            delay(6_000)
                            assertNull(runtime.current())
                            assertNull(packages.current(System.currentTimeMillis()))
                            capture("expired-start")
                            scenario?.close(); scenario = null
                            expired++
                            "expired:$id"
                        }
                        command == "idle" -> {
                            assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
                            "idle"
                        }
                        command == "finish" -> {
                            // Failure cleanup may end only a request observed by this run.
                            runtime.current()?.let { interrupted ->
                                assertTrue(interrupted.sessionPackage.request.requestId in owned)
                                val models = ViewModelStore()
                                val model = withContext(Dispatchers.Main) {
                                    SessionViewModel.QuickStartFactory(interrupted.sessionPackage.request.requestId, context)
                                        .create(SessionViewModel::class.java).also { models.put("state-ui-cleanup", it) }
                                }
                                try {
                                    withTimeout(15_000) { while (model.uiState.value.loading) delay(100) }
                                    withContext(Dispatchers.Main) { model.onEndWorkout() }
                                    withTimeout(20_000) { while (runtime.current() != null) delay(100) }
                                } finally { withContext(Dispatchers.Main) { models.clear() } }
                            }
                            assertEquals(2, started); assertEquals(2, completed); assertEquals(1, expired)
                            "finished"
                        }
                        else -> error("Unknown state command")
                    }.also { assertEquals(before, repository.entries.first()) }
                } catch (failure: Throwable) {
                    File(output, "failure-stack.txt").writeText(failure.stackTraceToString())
                    "error:${failure.javaClass.simpleName}:${failure.message}"
                }
                messages.sendMessage(peer, STATE_REPLY_PATH, reply.toByteArray()).await()
                if (command == "finish") done.countDown()
            }
        }
        messages.addListener(listener).await()
        try {
            assertTrue("Phone state test did not finish", done.await(900, TimeUnit.SECONDS))
            assertEquals(2, started); assertEquals(2, completed); assertEquals(1, expired)
            assertEquals(before, repository.entries.first())
            assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
        } finally { scenario?.close(); messages.removeListener(listener).await(); scope.cancel() }
    }

    @Test fun observePhoneEntryPackages() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartEntryUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val automation = instrumentation.uiAutomation
        val avd = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        ).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Wear", avd)
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val repository = WorkoutRepository(context)
        val before = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        assertNull(packages.current(System.currentTimeMillis()))
        assertNull(runtime.current())
        val output = File(context.getExternalFilesDir(null), "entry-ui-acceptance/${System.currentTimeMillis()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val done = CountDownLatch(1)
        var observed = 0
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId != peer || event.path != PROBE_PATH) return@OnMessageReceivedListener
            scope.launch {
                val command = event.data.toString(Charsets.UTF_8)
                val reply = try {
                    when {
                        command.startsWith("ready:") -> {
                            val id = command.removePrefix("ready:")
                            val offer = withTimeout(15_000) {
                                while (true) {
                                    packages.current(System.currentTimeMillis())?.takeIf { it.request.requestId == id }
                                        ?.let { return@withTimeout it }
                                    delay(100)
                                }
                                @Suppress("UNREACHABLE_CODE") error("No offer")
                            }
                            assertEquals(QuickStartPackageState.READY, offer.state)
                            assertNull(runtime.current())
                            assertEquals(before, repository.entries.first())
                            ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java)).use {
                                withTimeout(15_000) {
                                    fun hasStart(): Boolean {
                                        automation.clearCache()
                                        fun visit(node: android.view.accessibility.AccessibilityNodeInfo): Boolean =
                                            node.text?.toString() == "Start" || (0 until node.childCount).any { index ->
                                                node.getChild(index)?.let(::visit) == true
                                            }
                                        return automation.rootInActiveWindow?.let(::visit) == true
                                    }
                                    while (!hasStart()) delay(150)
                                }
                                delay(500)
                                automation.takeScreenshot()?.let { bitmap ->
                                    File(output, "${++observed}-ready.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                    bitmap.recycle()
                                }
                                File(output, "$observed-request.json").writeText(Json.encodeToString(offer.request))
                            }
                            Json.encodeToString(offer.request)
                        }
                        command.startsWith("cleared:") -> {
                            withTimeout(15_000) { while (packages.current(System.currentTimeMillis()) != null) delay(100) }
                            assertNull(runtime.current())
                            assertEquals(before, repository.entries.first())
                            "cleared"
                        }
                        command == "finish" -> {
                            assertEquals(4, observed)
                            "finished"
                        }
                        else -> error("Unknown entry command")
                    }
                } catch (failure: Throwable) { "error:${failure.javaClass.simpleName}:${failure.message}" }
                messages.sendMessage(peer, REPLY_PATH, reply.toByteArray()).await()
                if (command == "finish") done.countDown()
            }
        }
        messages.addListener(listener).await()
        try {
            assertTrue("Phone UI test did not finish", done.await(600, TimeUnit.SECONDS))
            assertEquals(4, observed)
            assertEquals(before, repository.entries.first())
            assertNull(runtime.current())
            assertNull(packages.current(System.currentTimeMillis()))
        } finally { messages.removeListener(listener).await(); scope.cancel() }
    }

    companion object {
        const val PROBE_PATH = "/validation/quick-start-entry/probe"
        const val REPLY_PATH = "/validation/quick-start-entry/reply"
        const val STATE_PROBE_PATH = "/validation/quick-start-state/probe"
        const val STATE_REPLY_PATH = "/validation/quick-start-state/reply"
    }
}
