package app.personal.workouttracker.wear.quickstart

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.session.SessionViewModel
import app.personal.workouttracker.wear.cues.DataStoreWatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCuePreferences
import app.personal.workouttracker.wear.cues.WatchCueStore
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual round-watch UI actions; native fixtures supply transport but never drive session transitions. */
@RunWith(AndroidJUnit4::class)
class PairedQuickStartUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val artifacts by lazy { File(instrumentation.targetContext.getExternalFilesDir(null), "ui-acceptance").apply { mkdirs() } }

    private fun nodes(): List<AccessibilityNodeInfo> {
        // Timer semantics may change without an accessibility event being delivered
        // while instrumentation waits on durable state. Query the current tree.
        automation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.rootInActiveWindow?.let(::visit)
        return result
    }

    private fun label(node: AccessibilityNodeInfo) = listOfNotNull(node.text, node.contentDescription).joinToString(" ")
    private fun find(text: String, contains: Boolean = false) = nodes().firstOrNull {
        if (contains) label(it).contains(text, ignoreCase = true) else
            it.text?.toString() == text || it.contentDescription?.toString() == text
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var candidate = node
        while (!candidate.isClickable && !candidate.isCheckable) candidate = candidate.parent ?: error("No actionable parent for ${label(node)}")
        return candidate
    }
    private fun scroll(forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return nodes().firstOrNull { node -> node.actionList.any { it.id == action } }?.performAction(action) == true
    }
    private suspend fun awaitLabel(text: String, contains: Boolean = false, allowScroll: Boolean = true): AccessibilityNodeInfo {
        return withTimeout(15_000) {
            var attempts = 0
            while (true) {
                find(text, contains)?.let { return@withTimeout it }
                if (allowScroll && attempts++ % 3 == 2) scroll((attempts / 3) % 12 < 6)
                delay(150)
            }
            @Suppress("UNREACHABLE_CODE") error("Missing $text")
        }
    }
    private suspend fun tap(text: String) {
        val target = clickable(awaitLabel(text))
        assertTrue("Disabled UI action: $text", target.isEnabled)
        assertTrue("UI action failed: $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private suspend fun awaitChecked(text: String, checked: Boolean) {
        withTimeout(5_000) {
            while (clickable(awaitLabel(text)).isChecked != checked) delay(100)
        }
    }
    private suspend fun capture(name: String) {
        // Durable writes and semantics can finish before the toggle animation.
        // Capture a settled rendered frame as well as the current semantics.
        delay(500)
        automation.waitForIdle(100, 2_000)
        File(artifacts, "$name.txt").writeText(nodes().joinToString("\n") { "${label(it)} | enabled=${it.isEnabled} clickable=${it.isClickable} checked=${it.isChecked}" })
        automation.takeScreenshot()?.let { bitmap ->
            File(artifacts, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        println("UI acceptance: $name")
    }

    @Test fun countdownRestAndRecoveryThroughRealUi() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val avdName = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        ).bufferedReader().use { it.readText().trim() }
        assertEquals("Use the isolated UI validation AVD", "Pasingot_Matrix_Wear", avdName)
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val repository = WorkoutRepository(context)
        val beforeEntries = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val beforePreferences = cues.state().preferences
        val replies = Channel<String>(Channel.UNLIMITED)
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener {
            if (it.sourceNodeId == peer && it.path == PairedQuickStartTransportTest.REPLY_PATH) replies.trySend(it.data.toString(Charsets.UTF_8))
        }
        messages.addListener(listener).await()
        suspend fun probe(command: String): String {
            messages.sendMessage(peer, PairedQuickStartTransportTest.PROBE_PATH, command.toByteArray()).await()
            return withTimeout(30_000) { replies.receive() }.also { assertFalse(it, it.startsWith("error:")) }
        }
        var scenario: ActivityScenario<WearMainActivity>? = null
        try {
            runtime.current()?.let { interrupted ->
                assertEquals("Preserve unrelated workouts", "Emulator UI acceptance", interrupted.sessionPackage.request.title)
                val models = ViewModelStore()
                val model = withContext(Dispatchers.Main) {
                    SessionViewModel.QuickStartFactory(interrupted.sessionPackage.request.requestId, context)
                        .create(SessionViewModel::class.java).also { models.put("interrupted-ui-fixture", it) }
                }
                try {
                    withTimeout(15_000) { while (model.uiState.value.loading) delay(100) }
                    assertNull(model.uiState.value.error)
                    withContext(Dispatchers.Main) { model.onEndWorkout() }
                    withTimeout(15_000) { while (runtime.current() != null) delay(100) }
                } finally { withContext(Dispatchers.Main) { models.clear() } }
            }
            assertNull(runtime.current())
            val pending = packages.current(System.currentTimeMillis())
            if (pending != null) {
                assertEquals("Preserve unrelated offers", "Emulator UI acceptance", pending.request.title)
                assertEquals(QuickStartPackageState.READY, pending.state)
                assertEquals("pending:cleared", probe("ui_cancel_pending"))
                withTimeout(15_000) { while (packages.current(System.currentTimeMillis()) != null) delay(100) }
            }
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_offer"))
            withTimeout(15_000) { while (packages.current(System.currentTimeMillis())?.request != request) delay(100) }
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            awaitLabel("Emulator UI acceptance")
            awaitLabel("Start")
            capture("01-ready")
            tap("Start")
            awaitLabel("Starting in", contains = true, allowScroll = false)
            capture("02-countdown")
            scenario.moveToState(Lifecycle.State.CREATED)
            delay(6_000)
            assertNull("Countdown must not start unseen", runtime.current())
            assertEquals(QuickStartPackageState.READY, packages.current(System.currentTimeMillis())?.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            tap("Start")
            withTimeout(12_000) { while (runtime.current()?.session?.status != SessionStatus.ACTIVE) delay(100) }
            assertEquals("started:${request.requestId}", probe("ui_started"))
            awaitLabel("Complete set")
            capture("03-active")
            tap("Complete set")
            withTimeout(10_000) { while (runtime.current()?.session?.status != SessionStatus.RESTING) delay(100) }
            for (seconds in listOf(5, 10, 30)) {
                val prior = requireNotNull(runtime.current()?.session?.restUntilEpochMillis)
                tap("+${seconds}s")
                withTimeout(5_000) { while (runtime.current()?.session?.restUntilEpochMillis == prior) delay(50) }
                assertEquals(prior + seconds * 1_000L, runtime.current()?.session?.restUntilEpochMillis)
            }
            capture("04-rest-extensions")
            withTimeout(65_000) { while (runtime.current()?.session?.restFinalCountdownStarted != true) delay(150) }
            val deadline = runtime.current()?.session?.restUntilEpochMillis
            withTimeout(2_000) {
                while (listOf(5, 10, 30).any { seconds ->
                        find("+${seconds}s")?.let(::clickable)?.isEnabled != false
                    }) delay(50)
            }
            for (seconds in listOf(5, 10, 30)) {
                val node = clickable(awaitLabel("+${seconds}s"))
                assertFalse("Final-five-second extension remained enabled", node.isEnabled)
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            assertEquals(deadline, runtime.current()?.session?.restUntilEpochMillis)
            capture("05-final-rest-lock")
            withTimeout(10_000) { while (runtime.current()?.session?.status != SessionStatus.ACTIVE) delay(100) }
            tap("Complete set")
            withTimeout(10_000) { while (runtime.current()?.session?.exerciseIndex != 1) delay(100) }
            awaitLabel("EXERCISE COMPLETE")
            awaitLabel("Emulator UI B")
            capture("06-exercise-success")
            tap("Pause")
            withTimeout(10_000) { while (runtime.current()?.session?.status != SessionStatus.PAUSED) delay(100) }
            val paused = runtime.current()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(paused, runtime.current())
            tap("Resume")
            withTimeout(5_000) { while (runtime.current()?.session?.status != SessionStatus.RESTING) delay(100) }
            assertEquals(paused?.session?.progress, runtime.current()?.session?.progress)
            assertEquals(1, runtime.current()?.session?.exerciseIndex)
            tap("Start now")
            withTimeout(10_000) { while (runtime.current()?.session?.status != SessionStatus.ACTIVE) delay(100) }
            capture("07-resumed")
            tap("Complete set")
            assertEquals("completed:${request.requestId}", probe("await_completed"))
            val phoneRecord = Json.parseToJsonElement(probe("record")).jsonObject
            val completed = Json.decodeFromString<FinalQuickStartResult>(requireNotNull(phoneRecord["finalResult"]).toString())
            assertEquals(request.requestId, completed.requestId)
            assertNotNull(completed.summary)
            assertNull(completed.endedSummary)
            assertEquals(3, completed.snapshot.exercises.sumOf { it.completedSets })
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            withTimeout(15_000) { while (runtime.current() != null) delay(100) }
            assertNull("Synced summary must stop claiming pending sync", find("Waiting to sync"))
            capture("08-saved-summary")
            assertEquals(beforeEntries, repository.entries.first())
            tap("Back to workouts")
            tap("Schedule")
            if (!cues.state().preferences.voiceEnabled) tap("Voice cues")
            withTimeout(5_000) { while (!cues.state().preferences.voiceEnabled) delay(100) }
            val categories = listOf(
                "Start briefing" to { p: WatchCuePreferences -> p.startBriefing },
                "Rest announcements" to { p: WatchCuePreferences -> p.restAnnouncements },
                "Countdown cue" to { p: WatchCuePreferences -> p.countdown },
                "Completion cue" to { p: WatchCuePreferences -> p.completion },
            )
            for ((name, enabled) in categories) {
                if (enabled(cues.state().preferences)) tap(name)
                withTimeout(5_000) { while (enabled(cues.state().preferences)) delay(100) }
                awaitChecked(name, false)
            }
            capture("09-categories-disabled")
            val disabledCategories = cues.state().preferences
            scenario.recreate()
            awaitLabel("Voice cues")
            assertEquals(disabledCategories, cues.state().preferences)
            for ((name, _) in categories) awaitChecked(name, false)
            tap("Voice cues")
            withTimeout(5_000) { while (cues.state().preferences.voiceEnabled) delay(100) }
            awaitChecked("Voice cues", false)
            capture("10-voice-disabled")
            scenario.recreate()
            awaitLabel("Voice cues")
            assertFalse(cues.state().preferences.voiceEnabled)
            awaitChecked("Voice cues", false)
        } catch (failure: Throwable) {
            capture("failure")
            throw failure
        } finally {
            scenario?.close()
            cues.setPreferences(beforePreferences)
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }

    @Test fun shortRestCuesAndScreenOffRecoveryThroughRealUi() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartRecoveryUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Wear", shell("getprop ro.boot.qemu.avd_name"))
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val repository = WorkoutRepository(context)
        val beforeEntries = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val beforePreferences = cues.state().preferences
        assertNull("Preserve existing workouts", runtime.current())
        assertNull("Preserve existing offers", packages.current(System.currentTimeMillis()))
        val runArtifacts = File(artifacts, "recovery-${System.currentTimeMillis()}").apply { mkdirs() }
        suspend fun evidence(name: String) {
            File(runArtifacts, "$name-cues.json").writeText(Json.encodeToString(cues.state()))
            File(runArtifacts, "$name-runtime.json").writeText(Json.encodeToString(runtime.current()))
            capture("${runArtifacts.name}/$name")
        }
        suspend fun awaitState(predicate: (QuickStartRuntimeState) -> Boolean): QuickStartRuntimeState = withTimeout(30_000) {
            while (true) {
                runtime.current()?.takeIf(predicate)?.let { return@withTimeout it }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("Missing runtime state")
        }
        val replies = Channel<String>(Channel.UNLIMITED)
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener {
            if (it.sourceNodeId == peer && it.path == PairedQuickStartTransportTest.REPLY_PATH)
                replies.trySend(it.data.toString(Charsets.UTF_8))
        }
        messages.addListener(listener).await()
        suspend fun probe(command: String): String {
            messages.sendMessage(peer, PairedQuickStartTransportTest.PROBE_PATH, command.toByteArray()).await()
            return withTimeout(30_000) { replies.receive() }.also { assertFalse(it, it.startsWith("error:")) }
        }
        var scenario: ActivityScenario<WearMainActivity>? = null
        try {
            // Reserve production cues with voice disabled to observe the durable haptic path.
            // This does not claim audible quality, delivery timing, or Bluetooth routing.
            cues.setPreferences(beforePreferences.copy(voiceEnabled = false, voicePromptResolved = true))
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_recovery_offer"))
            File(runArtifacts, "request.json").writeText(Json.encodeToString(request))
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            awaitLabel("Emulator cue recovery")
            tap("Start")
            awaitState { it.session.status == SessionStatus.ACTIVE }
            assertEquals("started:${request.requestId}", probe("ui_started"))
            for ((index, seconds) in listOf(0, 3, 5, 6, 8, 10, 12).withIndex()) {
                val prior = awaitState { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index && it.session.currentSet == 1 }
                val priorKeys = cues.state().ledger.deliveredKeys
                tap("Complete set")
                val active = awaitState { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index && it.session.currentSet == 2 }
                assertEquals(prior.outcomes.exercises[index].completedSets + 1,
                    active.outcomes.exercises[index].completedSets)
                val expected = when {
                    seconds == 0 -> emptyList()
                    seconds > 10 -> listOf("REST", "FIVE_SECONDS", "GO")
                    else -> listOf("FIVE_SECONDS", "GO")
                }
                withTimeout(5_000) {
                    while (cues.state().ledger.deliveredKeys.filter { it !in priorKeys }.size < expected.size) delay(50)
                }
                val newKeys = cues.state().ledger.deliveredKeys.filter { it !in priorKeys }
                assertEquals("Rest $seconds cue sequence", expected, newKeys.map { it.split('|')[4] })
                assertEquals(newKeys.size, newKeys.distinct().size)
                evidence("rest-$seconds")
                tap("Complete set")
                val advanced = awaitState { it.session.exerciseIndex == index + 1 }
                assertEquals(index, advanced.session.progress?.successExerciseIndex)
                withTimeout(5_000) {
                    while (cues.state().ledger.deliveredKeys.none {
                        val parts = it.split('|'); parts[2] == index.toString() && parts[4] == "EXERCISE_SUCCESS"
                    }) delay(50)
                }
                if (index == 6) {
                    awaitLabel("EXERCISE COMPLETE")
                    tap("Pause")
                    val paused = awaitState { it.session.status == SessionStatus.PAUSED }
                    val ledger = cues.state()
                    scenario.recreate()
                    awaitLabel("Resume")
                    assertEquals(paused, runtime.current())
                    assertEquals(ledger, cues.state())
                    tap("Resume")
                    awaitState { it.session.status == SessionStatus.RESTING }
                    awaitLabel("EXERCISE COMPLETE")
                    evidence("exercise-success-recreated")
                }
                if (runtime.current()?.session?.status == SessionStatus.RESTING) tap("Start now")
                awaitState { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index + 1 }
            }
            tap("Complete set")
            val resting = awaitState { it.session.status == SessionStatus.RESTING }
            withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.none {
                it.contains("|REST|") && it.endsWith("|${resting.session.restUntilEpochMillis}")
            }) delay(50) }
            evidence("before-screen-off")
            shell("input keyevent KEYCODE_SLEEP")
            withTimeout(10_000) {
                while (!shell("dumpsys power").contains("mWakefulness=Asleep") &&
                    !shell("dumpsys power").contains("mWakefulness=Dozing")) delay(150)
            }
            val hiddenLedger = cues.state()
            scenario.onActivity { activity ->
                val field = WearMainActivity::class.java.getDeclaredField("presentationPolicy").apply { isAccessible = true }
                val policy = (field.get(activity) as androidx.compose.runtime.State<*>).value
                File(runArtifacts, "screen-off-presentation.txt").writeText(policy.toString())
            }
            automation.takeScreenshot()?.let { bitmap ->
                File(runArtifacts, "screen-off.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            File(runArtifacts, "screen-off-power.txt").writeText(shell("dumpsys power"))
            File(runArtifacts, "screen-off-activity.txt").writeText(shell("dumpsys activity activities"))
            delay(23_000)
            assertEquals("Hidden rest must preserve exact progress/deadline", resting, runtime.current())
            assertEquals("Hidden rest must not reserve unseen countdown cues", hiddenLedger, cues.state())
            File(runArtifacts, "hidden-deadline-runtime.json").writeText(Json.encodeToString(runtime.current()))
            File(runArtifacts, "hidden-deadline-cues.json").writeText(Json.encodeToString(cues.state()))
            shell("input keyevent KEYCODE_WAKEUP")
            val recovered = awaitState { it.session.status == SessionStatus.ACTIVE }
            assertEquals(resting.session.progress, recovered.session.progress)
            assertEquals(resting.outcomes, recovered.outcomes)
            assertEquals(2, recovered.session.currentSet)
            withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.size == hiddenLedger.ledger.deliveredKeys.size) delay(50) }
            assertEquals(listOf("GO"), cues.state().ledger.deliveredKeys.filter {
                it !in hiddenLedger.ledger.deliveredKeys }.map { it.split('|')[4] })
            evidence("screen-off-recovered")
            tap("Complete set")
            assertEquals("completed:${request.requestId}", probe("await_completed"))
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            withTimeout(15_000) { while (runtime.current() != null || !cues.state().acknowledgedWorkoutSuccess) delay(100) }
            val record = Json.parseToJsonElement(probe("record")).jsonObject
            val result = Json.decodeFromString<FinalQuickStartResult>(requireNotNull(record["finalResult"]).toString())
            assertEquals(16, result.snapshot.exercises.sumOf { it.completedSets })
            assertEquals(request.requestId, result.requestId)
            File(runArtifacts, "phone-record.json").writeText(record.toString())
            val terminalCues = cues.state()
            assertTrue(terminalCues.ledger.deliveredKeys.isEmpty())
            scenario.recreate()
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            assertNull(find("Waiting to sync"))
            assertEquals(terminalCues, cues.state())
            assertEquals(record, Json.parseToJsonElement(probe("record")).jsonObject)
            assertEquals(beforeEntries, repository.entries.first())
            evidence("workout-success-recreated")
        } catch (failure: Throwable) {
            shell("input keyevent KEYCODE_WAKEUP")
            evidence("failure")
            // End only this run's synthetic fixture through the normal engine.
            // Keep the original assertion as the primary failure if cleanup fails.
            runCatching {
                scenario?.close()
                scenario = null
                runtime.current()?.let { interrupted ->
                    assertEquals("Emulator cue recovery", interrupted.sessionPackage.request.title)
                    val models = ViewModelStore()
                    val model = withContext(Dispatchers.Main) {
                        SessionViewModel.QuickStartFactory(interrupted.sessionPackage.request.requestId, context)
                            .create(SessionViewModel::class.java).also { models.put("recovery-cleanup", it) }
                    }
                    try {
                        withTimeout(15_000) { while (model.uiState.value.loading) delay(100) }
                        assertNull(model.uiState.value.error)
                        withContext(Dispatchers.Main) { model.onEndWorkout() }
                        withTimeout(15_000) { while (runtime.current() != null) delay(100) }
                    } finally { withContext(Dispatchers.Main) { models.clear() } }
                }
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            scenario?.close()
            cues.setPreferences(beforePreferences)
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }
}
