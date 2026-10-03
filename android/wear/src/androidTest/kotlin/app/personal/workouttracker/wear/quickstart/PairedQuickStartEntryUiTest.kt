package app.personal.workouttracker.wear.quickstart

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.WorkoutRepository
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
    }
}
