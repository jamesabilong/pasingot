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
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.session.SessionViewModel
import app.personal.workouttracker.wear.cues.DataStoreWatchCuePersistence
import app.personal.workouttracker.wear.cues.WatchCuePreferences
import app.personal.workouttracker.wear.cues.WatchCueStore
import app.personal.workouttracker.wear.cues.PersistedWatchCueState
import app.personal.workouttracker.wear.ui.WatchPresentationPolicy
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
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
    private val automation by lazy { instrumentation.uiAutomation }
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
        fun presentationShell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val beforeAnimatorScale = if (args.getString("reducedMotionValidation") == "true")
            presentationShell("settings get global animator_duration_scale") else null
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
        suspend fun assertReducedMotion() {
            if (args.getString("reducedMotionValidation") != "true") return
            presentationShell("settings put global animator_duration_scale 0")
            var attempts = 0
            withTimeout(5_000) {
                while (true) {
                    var reduced = false
                    requireNotNull(scenario).onActivity { activity ->
                        val field = WearMainActivity::class.java.getDeclaredField("presentationPolicy").apply { isAccessible = true }
                        @Suppress("UNCHECKED_CAST")
                        val policy = (field.get(activity) as androidx.compose.runtime.State<WatchPresentationPolicy>).value
                        reduced = policy.reducedMotion
                        if (!reduced && attempts++ < 3) println("Reduced-motion diagnostic: policy=$policy nativeScale=${android.provider.Settings.Global.getFloat(activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, -1f)} lifecycle=${activity.lifecycle.currentState}")
                        if (reduced) { assertFalse(policy.showDecorativeProgress); assertTrue(policy.allowInteraction) }
                    }
                    if (reduced) break
                    if (attempts <= 3) println("Reduced-motion shell scale=${presentationShell("settings get global animator_duration_scale")}")
                    delay(50)
                }
            }
            assertEquals("0", presentationShell("settings get global animator_duration_scale"))
        }
        try {
            // UiAutomation itself can reset animation scales when it connects.
            // Apply the acceptance setting after that connection, then restore it.
            if (beforeAnimatorScale != null) presentationShell("settings put global animator_duration_scale 0")
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
            if (args.getString("unavailableVoiceUiValidation") == "true") {
                cues.setPreferences(WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true))
            }
            val pending = packages.current(System.currentTimeMillis())
            if (pending != null) {
                assertEquals("Preserve unrelated offers", "Emulator UI acceptance", pending.request.title)
                assertEquals(QuickStartPackageState.READY, pending.state)
                assertEquals("pending:cleared", probe("ui_cancel_pending"))
                withTimeout(15_000) { while (packages.current(System.currentTimeMillis()) != null) delay(100) }
            }
            if (args.getString("reducedMotionValidation") == "true") assertEquals("cleanup:preserved", probe("ui_cleanup_complete"))
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_offer"))
            withTimeout(15_000) { while (packages.current(System.currentTimeMillis())?.request != request) delay(100) }
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            if (beforeAnimatorScale != null) {
                presentationShell("settings put global animator_duration_scale 0")
                assertEquals("0", presentationShell("settings get global animator_duration_scale"))
            }
            assertReducedMotion()
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
            assertReducedMotion()
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
            assertReducedMotion()
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
            assertReducedMotion()
            capture("06-exercise-success")
            tap("Pause")
            withTimeout(10_000) { while (runtime.current()?.session?.status != SessionStatus.PAUSED) delay(100) }
            val paused = runtime.current()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertReducedMotion()
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
            assertReducedMotion()
            capture("08-saved-summary")
            assertEquals(beforeEntries, repository.entries.first())
            tap("Back to workouts")
            tap("Schedule")
            if (args.getString("unavailableVoiceUiValidation") == "true") {
                awaitLabel("No system voice service")
                awaitLabel("Visual cues and haptics stay active", contains = true)
                capture("09-voice-unavailable")
            }
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
            assertReducedMotion()
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
            failure.printStackTrace()
            capture("failure")
            throw failure
        } finally {
            scenario?.close()
            cues.setPreferences(beforePreferences)
            if (beforeAnimatorScale != null) {
                presentationShell(if (beforeAnimatorScale == "null") "settings delete global animator_duration_scale"
                    else "settings put global animator_duration_scale $beforeAnimatorScale")
                assertEquals(beforeAnimatorScale, presentationShell("settings get global animator_duration_scale"))
            }
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

    private fun lifecycleShell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    /** Read the on-screen production owner's native state; never substitute its output/listener. */
    private fun productionCueController(scenario: ActivityScenario<WearMainActivity>): Any {
        var owner: Any? = null
        scenario.onActivity { activity ->
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ViewModelStore, Boolean>())
            fun values(instance: Any): List<Any> = generateSequence(instance.javaClass as Class<*>?) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .filter { java.util.Map::class.java.isAssignableFrom(it.type) }
                .flatMap { field -> field.isAccessible = true; (field.get(instance) as? Map<*, *>)?.values.orEmpty().filterNotNull().asSequence() }
                .toList()
            fun visit(store: ViewModelStore) {
                if (!seen.add(store)) return
                for (model in values(store)) {
                    if (model is SessionViewModel) {
                        assertNull("Multiple session speech owners", owner)
                        fun field(instance: Any, name: String): Any = instance.javaClass.getDeclaredField(name)
                            .apply { isAccessible = true }.get(instance)!!
                        owner = field(field(model, "cueEmitter"), "controller")
                    } else if (model.javaClass.simpleName == "NavControllerViewModel") {
                        values(model).filterIsInstance<ViewModelStore>().forEach(::visit)
                    }
                }
            }
            visit(activity.viewModelStore)
        }
        return requireNotNull(owner) { "No on-screen production SessionViewModel" }
    }

    private fun productionSpeechOwner(scenario: ActivityScenario<WearMainActivity>): Any =
        productionCueController(scenario).let { requireNotNull(it.javaClass.getDeclaredField("output").apply { isAccessible = true }.get(it)) }
            .also { assertEquals("AndroidTtsCueOutput", it.javaClass.simpleName) }

    @Test fun voiceEnabledCancellationThroughRealUi() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartSpeechUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Pasingot_Matrix_Wear", lifecycleShell("getprop ro.boot.qemu.avd_name"))
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val repository = WorkoutRepository(context)
        val beforeEntries = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val originalPreferences = cues.state().preferences
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
        var folder: File? = null
        fun engine(owner: Any) = owner.javaClass.getDeclaredField("tts").apply { isAccessible = true }
            .get(owner) as? android.speech.tts.TextToSpeech
        fun focusStack(dump: String) = dump.substringAfter("Audio Focus stack entries (last is top of stack):")
            .substringBefore("No external focus policy").substringBefore("External focus policy")
        fun hasFocus(dump: String) = focusStack(dump).contains("pack: app.personal.workouttracker")
        suspend fun silent(owner: Any, name: String, timeout: Long = 1_500) {
            withTimeout(timeout) {
                while (engine(owner)?.isSpeaking == true || hasFocus(lifecycleShell("dumpsys audio"))) delay(25)
            }
            File(requireNotNull(folder), "$name-audio.txt").writeText(lifecycleShell("dumpsys audio"))
        }
        suspend fun speaking(owner: Any, name: String) {
            withTimeout(10_000) { while (engine(owner)?.isSpeaking != true) delay(10) }
            val dump = lifecycleShell("dumpsys audio")
            assertTrue("Production speech must own transient focus", hasFocus(dump))
            File(requireNotNull(folder), "$name-speaking-audio.txt").writeText(dump)
            File(requireNotNull(folder), "$name-speaking-cues.json").writeText(Json.encodeToString(cues.state()))
            File(requireNotNull(folder), "$name-speaking-runtime.json").writeText(Json.encodeToString(runtime.current()))
        }
        suspend fun evidence(name: String) = lifecycleEvidence(requireNotNull(folder), name)
        suspend fun endOwnedFixture() {
            runtime.current()?.let { interrupted ->
                assertEquals("Preserve unrelated workouts", "Emulator UI speech", interrupted.sessionPackage.request.title)
                val models = ViewModelStore()
                val model = withContext(Dispatchers.Main) {
                    SessionViewModel.QuickStartFactory(interrupted.sessionPackage.request.requestId, context)
                        .create(SessionViewModel::class.java).also { models.put("speech-ui-cleanup", it) }
                }
                try {
                    withTimeout(15_000) { while (model.uiState.value.loading) delay(100) }
                    assertNull(model.uiState.value.error)
                    withContext(Dispatchers.Main) { model.onEndWorkout() }
                    withTimeout(15_000) { while (runtime.current() != null) delay(100) }
                } finally { withContext(Dispatchers.Main) { models.clear() } }
            }
        }
        try {
            endOwnedFixture()
            packages.current(System.currentTimeMillis())?.let {
                assertEquals("Emulator UI speech", it.request.title)
                assertEquals(QuickStartPackageState.READY, it.state)
                assertEquals("pending:cleared", probe("ui_cancel_pending"))
                withTimeout(15_000) { while (packages.current(System.currentTimeMillis()) != null) delay(100) }
            }
            assertEquals("cleanup:preserved", probe("ui_speech_cleanup_complete"))
            cues.setPreferences(originalPreferences.copy(voiceEnabled = true, voicePromptResolved = true,
                startBriefing = false, countdown = true, completion = false, restAnnouncements = true))
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_speech_offer"))
            folder = File(artifacts, "speech-${request.requestId}").apply { mkdirs() }
            File(folder, "request.json").writeText(Json.encodeToString(request))
            File(folder, "before-entries.json").writeText(Json.encodeToString(beforeEntries))
            File(folder, "before-preferences.json").writeText(Json.encodeToString(originalPreferences))
            withTimeout(15_000) { while (packages.current(System.currentTimeMillis())?.request != request) delay(50) }
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            awaitLabel("Emulator UI speech")
            tap("Start")
            awaitLifecycleState { it.session.status == SessionStatus.ACTIVE }
            assertEquals("started:${request.requestId}", probe("ui_started"))
            awaitLabel("Complete set")
            var owner = productionSpeechOwner(scenario)
            withTimeout(10_000) {
                while (owner.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.get(owner) != true ||
                    owner.javaClass.getDeclaredField("languageSupported").apply { isAccessible = true }.get(owner) != true) delay(50)
            }
            silent(owner, "initial-idle", 5_000)
            evidence("01-active")

            // Same-exercise REST is the real short production script, so query/click
            // promptly and prove native playback is still active at each action.
            tap("Complete set")
            val firstRest = awaitLifecycleState { it.session.status == SessionStatus.RESTING }
            speaking(owner, "pause")
            val pause = clickable(awaitLabel("Pause"))
            assertTrue("Pause must interrupt live native speech", engine(owner)?.isSpeaking == true)
            val pauseLedger = cues.state()
            assertTrue(pause.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val paused = awaitLifecycleState { it.session.status == SessionStatus.PAUSED }
            assertEquals(firstRest.session.progress, paused.session.progress)
            assertEquals(2, paused.session.currentSet)
            silent(owner, "pause-released")
            assertEquals(pauseLedger, cues.state())
            evidence("02-paused")

            tap("Resume")
            awaitLifecycleState { it.session.status == SessionStatus.RESTING }
            speaking(owner, "start-now")
            val startNow = clickable(awaitLabel("Start now"))
            assertTrue("Start now must replace live native speech", engine(owner)?.isSpeaking == true)
            val beforeGo = cues.state().ledger.deliveredKeys
            assertTrue(startNow.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val active = awaitLifecycleState { it.session.status == SessionStatus.ACTIVE }
            assertEquals(paused.session.progress, active.session.progress)
            withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.size == beforeGo.size) delay(25) }
            assertEquals(listOf("GO"), cues.state().ledger.deliveredKeys.filter { it !in beforeGo }.map { it.split('|')[4] })
            silent(owner, "start-now-released", 3_000)
            evidence("03-start-now")

            tap("Complete set")
            val secondRest = awaitLifecycleState { it.session.status == SessionStatus.RESTING }
            speaking(owner, "back")
            val beforeBack = cues.state()
            assertTrue("Back must interrupt live native speech", engine(owner)?.isSpeaking == true)
            lifecycleShell("input keyevent KEYCODE_BACK")
            silent(owner, "back-released")
            val saved = requireNotNull(runtime.current())
            assertEquals("Back preserves the exact running rest for deadline recovery", secondRest, saved)
            assertEquals(3, saved.session.currentSet)
            assertEquals(beforeBack, cues.state())
            awaitLabel("Resume")
            evidence("04-back-saved")

            tap("Resume")
            awaitLabel("Start now")
            owner = productionSpeechOwner(scenario)
            withTimeout(10_000) { while (owner.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.get(owner) != true) delay(25) }
            delay(500)
            assertEquals(saved, runtime.current())
            assertEquals("Reopening the same rest must not replay its reservation", beforeBack, cues.state())
            silent(owner, "reopened-rest-silent")
            tap("Pause")
            awaitLifecycleState { it.session.status == SessionStatus.PAUSED }
            tap("Resume")
            awaitLifecycleState { it.session.status == SessionStatus.RESTING }
            speaking(owner, "end-pause")
            val endPause = clickable(awaitLabel("Pause"))
            assertTrue(engine(owner)?.isSpeaking == true)
            assertTrue(endPause.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val endPaused = awaitLifecycleState { it.session.status == SessionStatus.PAUSED }
            assertEquals(saved.session.progress, endPaused.session.progress)
            silent(owner, "end-pause-released")
            val beforeEnd = cues.state()
            tap("End workout")
            awaitLabel("End workout?")
            evidence("05-end-confirmation")
            assertEquals(endPaused, runtime.current())
            assertEquals(beforeEnd, cues.state())
            tap("End workout")
            assertEquals("completed:${request.requestId}", probe("await_completed"))
            val record = Json.parseToJsonElement(probe("record")).jsonObject
            val result = Json.decodeFromString<FinalQuickStartResult>(requireNotNull(record["finalResult"]).toString())
            assertEquals(request.requestId, result.requestId)
            assertNotNull(result.endedSummary)
            assertNull(result.summary)
            assertEquals(2, result.snapshot.exercises.sumOf { it.completedSets })
            File(folder, "phone-record.json").writeText(record.toString())
            awaitLabel("Workout ended")
            withTimeout(15_000) { while (runtime.current() != null) delay(50) }
            assertNull(packages.current(System.currentTimeMillis()))
            assertTrue(cues.state().ledger.deliveredKeys.none { "|WORKOUT_SUCCESS|" in it })
            silent(owner, "end-released")
            val terminal = cues.state()
            delay(1_000)
            assertEquals(terminal, cues.state())
            assertEquals(record, Json.parseToJsonElement(probe("record")).jsonObject)
            assertEquals(beforeEntries, repository.entries.first())
            evidence("06-ended")
            File(folder, "after-entries.json").writeText(Json.encodeToString(repository.entries.first()))
            println("Speech UI request: ${request.requestId}")
        } catch (failure: Throwable) {
            folder?.let { evidence("failure") }
            runCatching { scenario?.close(); scenario = null; endOwnedFixture() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            scenario?.close()
            cues.setPreferences(originalPreferences)
            assertEquals(originalPreferences, cues.state().preferences)
            folder?.let { File(it, "after-preferences.json").writeText(Json.encodeToString(cues.state().preferences)) }
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }

    @Test fun voiceEnabledShortRestsAndTransitionsThroughRealUi() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartVoiceMatrixUiValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Pasingot_Matrix_Wear", lifecycleShell("getprop ro.boot.qemu.avd_name"))
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val repository = WorkoutRepository(context)
        val beforeEntries = repository.entries.first()
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val originalPreferences = cues.state().preferences
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
        suspend fun state(predicate: (QuickStartRuntimeState) -> Boolean): QuickStartRuntimeState = withTimeout(35_000) {
            while (true) {
                runtime.current()?.takeIf(predicate)?.let { return@withTimeout it }
                delay(25)
            }
            @Suppress("UNREACHABLE_CODE") error("Missing voice matrix state")
        }
        fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(instance)
        fun engine(owner: Any) = field(owner, "tts") as? android.speech.tts.TextToSpeech
        fun active(controller: Any) = field(controller, "active") as? app.personal.workouttracker.wear.cues.WatchCueEvent
        fun focusStack(dump: String) = dump.substringAfter("Audio Focus stack entries (last is top of stack):")
            .substringBefore("No external focus policy").substringBefore("External focus policy")
        data class Observation(val wall: Long, val elapsed: Long, val key: String?, val speaking: Boolean)
        val trace = mutableListOf<Observation>()
        var sampler: kotlinx.coroutines.Job? = null
        var scenario: ActivityScenario<WearMainActivity>? = null
        var folder: File? = null
        suspend fun idle(controller: Any, owner: Any) {
            withTimeout(7_000) { while (active(controller) != null || engine(owner)?.isSpeaking == true) delay(25) }
            assertFalse("Cue focus must release after native output", focusStack(lifecycleShell("dumpsys audio"))
                .contains("pack: app.personal.workouttracker"))
        }
        suspend fun evidence(name: String) = lifecycleEvidence(requireNotNull(folder), name)
        suspend fun endOwnedFixture() {
            runtime.current()?.let { interrupted ->
                assertEquals("Preserve unrelated workouts", "Emulator voice matrix", interrupted.sessionPackage.request.title)
                val models = ViewModelStore()
                val model = withContext(Dispatchers.Main) {
                    SessionViewModel.QuickStartFactory(interrupted.sessionPackage.request.requestId, context)
                        .create(SessionViewModel::class.java).also { models.put("voice-matrix-cleanup", it) }
                }
                try {
                    withTimeout(15_000) { while (model.uiState.value.loading) delay(100) }
                    assertNull(model.uiState.value.error)
                    withContext(Dispatchers.Main) { model.onEndWorkout() }
                    withTimeout(15_000) { while (runtime.current() != null) delay(100) }
                } finally { withContext(Dispatchers.Main) { models.clear() } }
            }
        }
        try {
            endOwnedFixture()
            packages.current(System.currentTimeMillis())?.let {
                assertEquals("Emulator voice matrix", it.request.title)
                assertEquals(QuickStartPackageState.READY, it.state)
                assertEquals("pending:cleared", probe("ui_cancel_pending"))
                withTimeout(15_000) { while (packages.current(System.currentTimeMillis()) != null) delay(100) }
            }
            assertEquals("cleanup:preserved", probe("ui_voice_matrix_cleanup_complete"))
            cues.setPreferences(WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true))
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_voice_matrix_offer"))
            folder = File(artifacts, "voice-matrix-${request.requestId}").apply { mkdirs() }
            File(folder, "request.json").writeText(Json.encodeToString(request))
            File(folder, "before-entries.json").writeText(Json.encodeToString(beforeEntries))
            File(folder, "before-preferences.json").writeText(Json.encodeToString(originalPreferences))
            withTimeout(15_000) { while (packages.current(System.currentTimeMillis())?.request != request) delay(50) }
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            awaitLabel("Emulator voice matrix")
            tap("Start")
            state { it.session.status == SessionStatus.ACTIVE }
            assertEquals("started:${request.requestId}", probe("ui_started"))
            awaitLabel("Complete set")
            val controller = productionCueController(scenario)
            val owner = requireNotNull(field(controller, "output"))
            assertEquals("AndroidTtsCueOutput", owner.javaClass.simpleName)
            withTimeout(10_000) {
                while (field(owner, "initialized") != true || field(owner, "languageSupported") != true) delay(25)
            }
            idle(controller, owner)
            sampler = launch {
                while (isActive) {
                    val observation = withContext(Dispatchers.Main) {
                        Observation(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(),
                            active(controller)?.key, engine(owner)?.isSpeaking == true)
                    }
                    val prior = trace.lastOrNull()
                    if (prior == null || prior.key != observation.key || prior.speaking != observation.speaking) trace += observation
                    delay(20)
                }
            }
            val checks = mutableListOf<String>()
            for ((index, seconds) in listOf(0, 3, 5, 6, 8, 10, 12, 20).withIndex()) {
                val prior = state { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index && it.session.currentSet == 1 }
                idle(controller, owner)
                val beforeRest = cues.state().ledger.deliveredKeys
                val traceStart = trace.size
                tap("Complete set")
                val entered = state { it.session.exerciseIndex == index && it.session.currentSet == 2 }
                val deadline = entered.session.restUntilEpochMillis
                val expectedRest = when { seconds == 0 -> emptyList()
                    seconds > 10 -> listOf("REST", "FIVE_SECONDS", "GO")
                    else -> listOf("FIVE_SECONDS", "GO") }
                val afterRest = state { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index && it.session.currentSet == 2 }
                assertEquals(prior.outcomes.exercises[index].completedSets + 1, afterRest.outcomes.exercises[index].completedSets)
                withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.filter { it !in beforeRest }.size < expectedRest.size) delay(25) }
                val restKeys = cues.state().ledger.deliveredKeys.filter { it !in beforeRest }
                assertEquals("Same-exercise rest $seconds", expectedRest, restKeys.map { it.split('|')[4] })
                assertEquals(restKeys.size, restKeys.distinct().size)
                if (seconds > 0) {
                    assertNotNull(deadline)
                    assertTrue("Rest must not advance before its deadline", System.currentTimeMillis() >= requireNotNull(deadline))
                    assertTrue(restKeys.all { it.endsWith("|$deadline") })
                }
                idle(controller, owner)
                val samples = trace.drop(traceStart)
                for (kind in expectedRest) {
                    assertTrue("Native playback missing for rest $seconds $kind: $samples", samples.any { it.speaking && it.key?.split('|')?.get(4) == kind })
                }
                if (deadline != null) {
                    assertTrue(samples.filter { it.speaking && it.key?.contains("|GO|") == true }.all { it.wall >= deadline })
                    assertTrue(samples.filter { it.speaking && it.key?.contains("|FIVE_SECONDS|") == true }.all { it.wall >= deadline - 5_000 })
                }
                checks += "same-exercise rest=$seconds deadline=$deadline keys=${restKeys.joinToString()} nativeKinds=${samples.filter { it.speaking }.mapNotNull { it.key?.split('|')?.get(4) }.distinct()}"
                evidence("rest-$seconds")
                val beforeSuccess = cues.state().ledger.deliveredKeys
                val transitionTrace = trace.size
                tap("Complete set")
                if (index == 7) break
                val advanced = state { it.session.exerciseIndex == index + 1 }
                assertEquals(index, advanced.session.progress?.successExerciseIndex)
                val transitionDeadline = advanced.session.restUntilEpochMillis
                state { it.session.status == SessionStatus.ACTIVE && it.session.exerciseIndex == index + 1 }
                val expectedSuccess = if (seconds == 0) listOf("EXERCISE_SUCCESS") else listOf("EXERCISE_SUCCESS", "FIVE_SECONDS", "GO")
                withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.filter { it !in beforeSuccess }.size < expectedSuccess.size) delay(25) }
                val successKeys = cues.state().ledger.deliveredKeys.filter { it !in beforeSuccess }
                assertEquals("Exercise transition rest $seconds", expectedSuccess, successKeys.map { it.split('|')[4] })
                if (seconds > 0) assertTrue(successKeys.drop(1).all { it.endsWith("|$transitionDeadline") })
                idle(controller, owner)
                val transitionSamples = trace.drop(transitionTrace)
                // Short success speech may be preempted by the warning. Its
                // durable reservation must exist even when it has no playback sample.
                for (kind in expectedSuccess.filter { it != "EXERCISE_SUCCESS" || seconds == 0 || seconds > 5 }) {
                    assertTrue("Native transition $seconds $kind missing: $transitionSamples", transitionSamples.any { it.speaking && it.key?.split('|')?.get(4) == kind })
                }
                checks += "exercise-transition rest=$seconds deadline=$transitionDeadline keys=${successKeys.joinToString()} nativeKinds=${transitionSamples.filter { it.speaking }.mapNotNull { it.key?.split('|')?.get(4) }.distinct()}"
                if (seconds >= 8) evidence("transition-$seconds")
                File(folder, "checks.txt").writeText(checks.joinToString("\n"))
            }
            File(folder, "checks.txt").writeText(checks.joinToString("\n"))
            assertEquals("completed:${request.requestId}", probe("await_completed"))
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            idle(controller, owner)
            assertTrue("Final success must reach native playback", trace.any { it.speaking && it.key?.contains("|WORKOUT_SUCCESS|") == true })
            withTimeout(15_000) { while (runtime.current() != null || !cues.state().acknowledgedWorkoutSuccess) delay(50) }
            assertNull(packages.current(System.currentTimeMillis()))
            val record = Json.parseToJsonElement(probe("record")).jsonObject
            val result = Json.decodeFromString<FinalQuickStartResult>(requireNotNull(record["finalResult"]).toString())
            assertEquals(request.requestId, result.requestId)
            assertNotNull(result.summary)
            assertNull(result.endedSummary)
            assertEquals(16, result.snapshot.exercises.sumOf { it.completedSets })
            File(folder, "phone-record.json").writeText(record.toString())
            val terminal = cues.state()
            assertTrue(terminal.ledger.deliveredKeys.isEmpty())
            evidence("completed")
            sampler.cancelAndJoin()
            sampler = null
            scenario.recreate()
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            val recoveredController = productionCueController(scenario)
            val recoveredOwner = requireNotNull(field(recoveredController, "output"))
            repeat(100) {
                assertNull("Terminal success replayed after recreation", active(recoveredController))
                assertFalse(engine(recoveredOwner)?.isSpeaking == true)
                delay(25)
            }
            idle(recoveredController, recoveredOwner)
            assertEquals(terminal, cues.state())
            assertEquals(record, Json.parseToJsonElement(probe("record")).jsonObject)
            assertEquals(beforeEntries, repository.entries.first())
            File(folder, "after-entries.json").writeText(Json.encodeToString(repository.entries.first()))
            evidence("completed-recreated")
            println("Voice matrix request: ${request.requestId}")
        } catch (failure: Throwable) {
            folder?.let { evidence("failure") }
            runCatching { scenario?.close(); scenario = null; endOwnedFixture() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            sampler?.cancelAndJoin()
            folder?.let { File(it, "native-timeline.tsv").writeText("wallMillis\telapsedMillis\tcontrollerKey\tnativeIsSpeaking\n" +
                trace.joinToString("\n") { row -> "${row.wall}\t${row.elapsed}\t${row.key.orEmpty()}\t${row.speaking}" }) }
            scenario?.close()
            cues.setPreferences(originalPreferences)
            assertEquals(originalPreferences, cues.state().preferences)
            folder?.let { File(it, "after-preferences.json").writeText(Json.encodeToString(cues.state().preferences)) }
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }

    private fun requireLifecycleCopy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("quickStartLifecycleUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Pasingot_Matrix_Wear", lifecycleShell("getprop ro.boot.qemu.avd_name"))
    }

    private fun lifecycleFolder(): File {
        val id = java.util.UUID.fromString(requireNotNull(InstrumentationRegistry.getArguments().getString("lifecycleRequestId"))).toString()
        return File(artifacts, "lifecycle-$id").also { require(it.isDirectory) }
    }

    private suspend fun awaitLifecycleState(predicate: (QuickStartRuntimeState) -> Boolean): QuickStartRuntimeState {
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(instrumentation.targetContext))
        return withTimeout(20_000) {
            while (true) {
                runtime.current()?.takeIf(predicate)?.let { return@withTimeout it }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("Missing lifecycle state")
        }
    }

    private suspend fun assertLifecycleAmbient(scenario: ActivityScenario<WearMainActivity>, expected: Boolean) {
        withTimeout(10_000) {
            var ambient = !expected
            while (ambient != expected) {
                scenario.onActivity { activity ->
                    val field = WearMainActivity::class.java.getDeclaredField("presentationPolicy").apply { isAccessible = true }
                    ambient = ((field.get(activity) as androidx.compose.runtime.State<*>).value as WatchPresentationPolicy).ambient
                }
                if (ambient != expected) delay(100)
            }
        }
    }

    private suspend fun lifecycleEvidence(folder: File, name: String) {
        val context = instrumentation.targetContext
        File(folder, "$name-runtime.json").writeText(Json.encodeToString(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context)).current()))
        File(folder, "$name-cues.json").writeText(Json.encodeToString(WatchCueStore(DataStoreWatchCuePersistence(context)).state()))
        capture("${folder.name}/$name")
    }

    @Test fun prepareCountdownAmbientAndActiveSleepRecovery() = runBlocking {
        requireLifecycleCopy()
        val context = instrumentation.targetContext
        val peer = requireNotNull(InstrumentationRegistry.getArguments().getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val repository = WorkoutRepository(context)
        assertTrue(WorkoutRepositorySessionSnapshotSource(repository).entries().blockingSessions().isEmpty())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        assertNull(runtime.current())
        assertNull(packages.current(System.currentTimeMillis()))
        val originalPreferences = cues.state().preferences
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
        var folder: File? = null
        try {
            cues.setPreferences(originalPreferences.copy(voiceEnabled = false, voicePromptResolved = true))
            val request = Json.decodeFromString<QuickStartRequest>(probe("ui_lifecycle_offer"))
            folder = File(artifacts, "lifecycle-${request.requestId}").apply { mkdirs() }
            File(folder, "request.json").writeText(Json.encodeToString(request))
            File(folder, "legacy-entries.json").writeText(Json.encodeToString(repository.entries.first()))
            scenario = ActivityScenario.launch(Intent(context, WearMainActivity::class.java))
            awaitLabel("Emulator lifecycle acceptance")
            tap("Start")
            awaitLabel("Starting in", contains = true, allowScroll = false)
            lifecycleShell("input keyevent KEYCODE_SLEEP")
            assertLifecycleAmbient(scenario, true)
            delay(6_000)
            assertNull("Ambient countdown must not start unseen", runtime.current())
            assertEquals(QuickStartPackageState.READY, packages.current(System.currentTimeMillis())?.state)
            val cancelledKeys = cues.state().ledger.deliveredKeys
            assertTrue(cancelledKeys.none { "|GO|" in it })
            File(folder, "countdown-ambient-power.txt").writeText(lifecycleShell("dumpsys power"))
            lifecycleEvidence(folder, "countdown-ambient-cancelled")
            lifecycleShell("input keyevent KEYCODE_WAKEUP")
            assertLifecycleAmbient(scenario, false)
            awaitLabel("Start")
            tap("Start")
            val active = awaitLifecycleState { it.session.status == SessionStatus.ACTIVE }
            assertEquals(request, active.sessionPackage.request)
            assertEquals("started:${request.requestId}", probe("ui_started"))
            awaitLabel("Complete set")
            val retryKeys = cues.state().ledger.deliveredKeys.filter { it !in cancelledKeys }
            assertEquals(listOf("BRIEFING", "FIVE_SECONDS", "GO"), retryKeys.map { it.split('|')[4] })
            val activeCues = cues.state()
            lifecycleShell("input keyevent KEYCODE_SLEEP")
            assertLifecycleAmbient(scenario, true)
            delay(8_000)
            assertEquals("Ambient must preserve the active set", active, runtime.current())
            assertEquals(activeCues, cues.state())
            lifecycleEvidence(folder, "active-ambient")
            lifecycleShell("input keyevent KEYCODE_WAKEUP")
            assertLifecycleAmbient(scenario, false)
            awaitLabel("Complete set")
            assertEquals(active, runtime.current())
            assertEquals(activeCues, cues.state())
            tap("Complete set")
            awaitLifecycleState { it.session.exerciseIndex == 1 }
            awaitLabel("EXERCISE COMPLETE")
            withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.none { "|EXERCISE_SUCCESS|" in it }) delay(50) }
            tap("Pause")
            awaitLifecycleState { it.session.status == SessionStatus.PAUSED }
            scenario.close()
            scenario = null
            cues.setPreferences(originalPreferences)
            lifecycleEvidence(folder, "before-exercise-process-death")
            File(folder, "before-exercise-pid.txt").writeText(android.os.Process.myPid().toString())
            assertEquals(Json.decodeFromString<List<DownloadedWorkoutEntry>>(File(folder, "legacy-entries.json").readText()), repository.entries.first())
            println("Lifecycle request: ${request.requestId}")
        } catch (failure: Throwable) {
            lifecycleShell("input keyevent KEYCODE_WAKEUP")
            folder?.let { lifecycleEvidence(it, "prepare-failure") }
            throw failure
        } finally {
            scenario?.close()
            cues.setPreferences(originalPreferences)
            try { assertEquals("finished", probe("finish")) }
            finally { messages.removeListener(listener).await(); replies.close() }
        }
    }

    @Test fun recoverExerciseSuccessAndCompleteOffline() = runBlocking {
        requireLifecycleCopy()
        val context = instrumentation.targetContext
        withTimeout(45_000) { while (Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()) delay(200) }
        val folder = lifecycleFolder()
        assertNotEquals(File(folder, "before-exercise-pid.txt").readText(), android.os.Process.myPid().toString())
        File(folder, "exercise-recovered-pid.txt").writeText(android.os.Process.myPid().toString())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val prior = Json.decodeFromString<QuickStartRuntimeState>(File(folder, "before-exercise-process-death-runtime.json").readText())
        val priorCues = Json.decodeFromString<PersistedWatchCueState>(File(folder, "before-exercise-process-death-cues.json").readText())
        assertEquals(prior, runtime.current())
        assertEquals(priorCues, cues.state())
        val preferences = cues.state().preferences
        try {
            cues.setPreferences(preferences.copy(voiceEnabled = false, voicePromptResolved = true))
            ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java)).use {
                tap("Resume")
                awaitLabel("PAUSED")
                awaitLabel("Resume")
                assertEquals(prior, runtime.current())
                val beforeResume = cues.state()
                tap("Resume")
                awaitLifecycleState { it.session.status == SessionStatus.ACTIVE }
                awaitLabel("EXERCISE COMPLETE")
                assertEquals(prior.session.progress, runtime.current()?.session?.progress)
                assertEquals(beforeResume, cues.state())
                lifecycleEvidence(folder, "exercise-success-process-recovered")
                tap("Complete set")
                val completed = awaitLifecycleState { it.finalResult != null }
                assertEquals(2, completed.finalResult?.snapshot?.exercises?.sumOf { it.completedSets })
                awaitLabel("Workout complete")
                awaitLabel("Saved on watch")
                awaitLabel("Waiting to sync")
                withTimeout(5_000) { while (cues.state().ledger.deliveredKeys.none { "|WORKOUT_SUCCESS|" in it }) delay(50) }
                assertEquals(1, cues.state().ledger.deliveredKeys.count { "|WORKOUT_SUCCESS|" in it })
                lifecycleEvidence(folder, "offline-completed")
            }
        } finally { cues.setPreferences(preferences) }
        lifecycleEvidence(folder, "before-final-process-death")
        File(folder, "before-final-pid.txt").writeText(android.os.Process.myPid().toString())
        assertEquals(Json.decodeFromString<List<DownloadedWorkoutEntry>>(File(folder, "legacy-entries.json").readText()), WorkoutRepository(context).entries.first())
    }

    @Test fun recoverOfflineFinalSuccessAfterProcessDeath() = runBlocking {
        requireLifecycleCopy()
        val context = instrumentation.targetContext
        assertTrue(Wearable.getNodeClient(context).connectedNodes.await().isEmpty())
        val folder = lifecycleFolder()
        assertNotEquals(File(folder, "before-final-pid.txt").readText(), android.os.Process.myPid().toString())
        File(folder, "final-recovered-pid.txt").writeText(android.os.Process.myPid().toString())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        val prior = Json.decodeFromString<QuickStartRuntimeState>(File(folder, "before-final-process-death-runtime.json").readText())
        val priorCues = Json.decodeFromString<PersistedWatchCueState>(File(folder, "before-final-process-death-cues.json").readText())
        assertNotNull(prior.finalResult)
        assertEquals(prior, runtime.current())
        assertEquals(priorCues, cues.state())
        ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java)).use {
            tap("Resume")
            awaitLabel("Workout complete")
            awaitLabel("Saved on watch")
            awaitLabel("Waiting to sync")
            assertEquals(prior, runtime.current())
            assertEquals(priorCues, cues.state())
            lifecycleEvidence(folder, "final-success-process-recovered")
        }
        assertEquals(prior, runtime.current())
        assertEquals(priorCues, cues.state())
        assertEquals(Json.decodeFromString<List<DownloadedWorkoutEntry>>(File(folder, "legacy-entries.json").readText()), WorkoutRepository(context).entries.first())
    }

    @Test fun verifyLifecyclePrunedAfterReceipt() = runBlocking {
        requireLifecycleCopy()
        val context = instrumentation.targetContext
        val peer = requireNotNull(InstrumentationRegistry.getArguments().getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val folder = lifecycleFolder()
        val prior = Json.decodeFromString<QuickStartRuntimeState>(File(folder, "before-final-process-death-runtime.json").readText())
        val runtime = QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context))
        val packages = WatchSessionPackageStore(DataStoreQuickStartPackagePersistence(context))
        val cues = WatchCueStore(DataStoreWatchCuePersistence(context))
        withTimeout(45_000) { while (runtime.current() != null || cues.state().acknowledgedSessionId != prior.session.workoutEntryId) delay(100) }
        assertNull(packages.current(System.currentTimeMillis()))
        assertTrue(cues.state().ledger.deliveredKeys.isEmpty())
        assertTrue(cues.state().acknowledgedWorkoutSuccess)
        val priorCues = Json.decodeFromString<PersistedWatchCueState>(File(folder, "before-final-process-death-cues.json").readText())
        assertEquals(priorCues.preferences, cues.state().preferences)
        assertEquals(Json.decodeFromString<List<DownloadedWorkoutEntry>>(File(folder, "legacy-entries.json").readText()), WorkoutRepository(context).entries.first())
        lifecycleEvidence(folder, "receipt-pruned")
    }
}
