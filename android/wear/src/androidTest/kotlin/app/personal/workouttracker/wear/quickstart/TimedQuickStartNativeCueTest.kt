package app.personal.workouttracker.wear.quickstart

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.wear.compose.material.Text
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.*
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.cues.*
import app.personal.workouttracker.wear.session.ControllerSessionCueEmitter
import app.personal.workouttracker.wear.session.SessionScreen
import app.personal.workouttracker.wear.session.SessionViewModel
import app.personal.workouttracker.wear.ui.PasingotTheme
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real clock/native speech and production gate/store/engine; private files on the owned AVD only. */
@RunWith(AndroidJUnit4::class)
class TimedQuickStartNativeCueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val models = ViewModelStore()
    private val json = Json { encodeDefaults = true }
    private val speeches = Collections.synchronizedList(mutableListOf<Speech>())
    private val haptics = Collections.synchronizedList(mutableListOf<Pair<WatchCueKind, Long>>())
    private val clockReads = Collections.synchronizedList(mutableListOf<Long>())
    @Volatile private var sent = 0
    private lateinit var folder: File
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun guard(stage: String, recover: Boolean = false) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("timedNativeCueValidation") == "true")
        assertTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Pasingot_Timed_UI", shell("getprop ro.boot.qemu.avd_name"))
        val run = InstrumentationRegistry.getArguments().getString("timedNativeRunId") ?: "initial"
        require(run.matches(Regex("[A-Za-z0-9-]{1,64}")))
        folder = File(context.filesDir, "native-timed-validation/$run/$stage")
        if (recover) assertTrue(folder.isDirectory)
        else { assertFalse("Never overwrite previous evidence", folder.exists()); assertTrue(folder.mkdirs()) }
    }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun await(timeout: Long = 15_000, condition: suspend () -> Boolean) = withTimeout(timeout) {
        while (!condition()) delay(25)
    }
    private fun speechList() = synchronized(speeches) { speeches.toList() }
    private fun kinds() = synchronized(haptics) { haptics.map { it.first } }
    private fun snapshot(label: String, state: QuickStartRuntimeState) {
        File(folder, "$label.json").writeText(json.encodeToString(state))
    }
    private fun evidence() {
        File(folder, "speech.txt").writeText(speechList().joinToString("\n"))
        File(folder, "haptics.txt").writeText(synchronized(haptics) { haptics.toList() }.joinToString("\n"))
        File(folder, "focus-after.txt").writeText(shell("dumpsys audio"))
    }
    private fun scenario() = ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java)).also {
        it.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setContent { PasingotTheme { Text("Native timed validation") } }
        }
    }
    private suspend fun output(): RecordingOutput {
        val native = withContext(Dispatchers.Main) { AndroidTtsCueOutput(context) }
        try {
            await(20_000) { VoiceCueAvailabilityRegistry.availability.value !in
                listOf(VoiceCueAvailability.CHECKING, VoiceCueAvailability.ENGINE_AVAILABLE) }
            assertEquals(VoiceCueAvailability.AVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            assertFalse(native.talkBackEnabled)
            return RecordingOutput(native)
        } catch (failure: Throwable) { native.close(); throw failure }
    }
    private suspend fun <T> fixture(block: suspend (QuickStartRuntimeStore, WatchCueStore, WatchSessionPackageStore) -> T): T {
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                File(folder, "native.preferences_pb")
            }
            val key = stringPreferencesKey("fixture_package_json")
            val packages = WatchSessionPackageStore(object : QuickStartPackagePersistence {
                override suspend fun read() = data.data.first()[key]
                override suspend fun write(raw: String?) { data.edit { if (raw == null) it.remove(key) else it[key] = raw } }
            })
            return block(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(data)),
                WatchCueStore(DataStoreWatchCuePersistence(data)), packages)
        } finally { main { models.clear() }; job.cancelAndJoin(); evidence() }
    }
    private suspend fun start(runtime: QuickStartRuntimeStore, cues: WatchCueStore,
        packages: WatchSessionPackageStore, seconds: Int, sets: Int, rest: Int): QuickStartRuntimeState {
        val now = System.currentTimeMillis()
        val request = QuickStartRequest(UUID.randomUUID().toString(), createdAtMillis = now,
            expiresAtMillis = now + 300_000, targetNodeId = "native-fixture-watch",
            title = "Native timed cue validation", source = QuickStartSource.LIBRARY_PLAYLIST,
            exercises = listOf(QuickStartExercise("owned-plank", "plank", "Plank", sets, "$seconds sec", rest)))
        packages.accept(request, now, "native-fixture-phone")
        cues.setPreferences(WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true))
        return QuickStartStartCoordinator(GlobalSessionStartGate(LegacySessionSnapshotSource { emptyList() }, packages),
            runtime, QuickStartReceiptClient {
                val saved = runtime.current()!!
                assertEquals(saved.startedAcknowledgement, it)
                assertEquals(saved.session.elapsedStartedAtEpochMillis!! + seconds * 1_000,
                    saved.session.timedSetDeadlineEpochMillis)
                delay(750)
                error("intentional offline Started transport")
            }).start(request.requestId, request.revision).also { snapshot("started", it) }
    }
    private suspend fun open(runtime: QuickStartRuntimeStore, cues: WatchCueStore,
        scenario: ActivityScenario<WearMainActivity>): SessionViewModel {
        val id = runtime.current()!!.session.workoutEntryId
        val emitter = ControllerSessionCueEmitter(WatchCueController(cues, output()))
        lateinit var model: SessionViewModel
        main {
            model = SessionViewModel(id, QuickStartSessionStore(id, runtime, resultClient = QuickStartResultClient {
                assertEquals(it, runtime.current()!!.finalResult); sent++; error("intentional offline result")
            }), NoOpQuickStartLogSender,
                nowEpochMillis = { System.currentTimeMillis().also { clockReads += it } },
                legacyStartGate = null, canAdjustSets = false,
                canRestart = false, awaitsPhoneReceipt = true, cueEmitter = emitter)
            models.put("native", model)
        }
        await { !model.uiState.value.loading }
        scenario.onActivity { it.setContent { PasingotTheme { SessionScreen(model) {} } } }
        return model
    }
    private suspend fun assertNativeSpeech(expected: List<WatchCueKind>) {
        await { speechList().size == expected.size && speechList().all { it.finished } }
        assertEquals(expected, kinds())
        assertEquals(expected, speechList().map { it.kind })
        for (speech in speechList()) {
            assertTrue("Native TTS did not complete: $speech", speech.success)
            assertTrue("Native isSpeaking not observed: $speech", speech.observed)
        }
    }
    @Test fun nativeReadiness() = runBlocking {
        guard("readiness")
        scenario().use {
            val peers = Wearable.getNodeClient(context).connectedNodes.await()
            File(folder, "connected-peers.txt").writeText(peers.joinToString("\n") { node -> "${node.id} ${node.displayName}" })
            println("Connected phone peers=${peers.size}; paired acceptance requires its own exact-peer run")
            val native = output()
            try { assertTrue(native.speak("readiness|0|0|0|GO|", "Go.")); assertTrue(speechList().single().observed) }
            finally { native.close(); evidence() }
        }
    }
    @Test fun naturalTimedSetsRestAndNativeCues() = runBlocking {
        guard("natural")
        scenario().use { scenario -> fixture { runtime, cues, packages ->
            val started = start(runtime, cues, packages, seconds = 8, sets = 2, rest = 12)
            open(runtime, cues, scenario)
            assertEquals(started.session.timedSetDeadlineEpochMillis, runtime.current()!!.session.timedSetDeadlineEpochMillis)
            await { runtime.current()?.session?.status == SessionStatus.RESTING }
            val resting = runtime.current()!!
            val deadline = resting.session.restUntilEpochMillis!!
            assertTrue("Early/late first expiry", deadline - 12_000 - started.session.timedSetDeadlineEpochMillis!! in 0..2_000)
            snapshot("resting", resting)
            await(20_000) { runtime.current()?.session?.status == SessionStatus.ACTIVE }
            val second = runtime.current()!!
            assertEquals(1, second.outcomes.exercises.single().completedSets)
            assertTrue(second.session.timedSetDeadlineEpochMillis!! - deadline in 8_000..10_000)
            snapshot("second", second)
            await { runtime.current()?.finalResult != null }
            assertEquals(2, runtime.current()!!.outcomes.exercises.single().completedSets)
            assertEquals(1, sent)
            assertNativeSpeech(listOf(WatchCueKind.REST, WatchCueKind.FIVE_SECONDS, WatchCueKind.GO, WatchCueKind.WORKOUT_SUCCESS))
            val warning = speechList().single { it.kind == WatchCueKind.FIVE_SECONDS }
            val go = speechList().single { it.kind == WatchCueKind.GO }
            assertTrue(deadline - requireNotNull(warning.observedAt) in 3_000..5_500)
            assertTrue(requireNotNull(go.observedAt) - deadline in 0..2_000)
            snapshot("completed", runtime.current()!!)
            File(folder, "cue-state.json").writeText(json.encodeToString(cues.state()))
        } }
    }
    @Test fun hiddenActivityExpiryAndTerminalNonReplay() = runBlocking {
        guard("hidden")
        scenario().use { scenario -> fixture { runtime, cues, packages ->
            start(runtime, cues, packages, seconds = 8, sets = 2, rest = 0)
            val model = open(runtime, cues, scenario)
            scenario.moveToState(Lifecycle.State.STARTED)
            delay(500)
            val hidden = runtime.current()!!
            val ledger = cues.state()
            await(12_000) { System.currentTimeMillis() > hidden.session.timedSetDeadlineEpochMillis!! + 500 }
            assertEquals(hidden, runtime.current()); assertEquals(ledger, cues.state())
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty())
            snapshot("hidden-expired", hidden)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await { runtime.current()?.outcomes?.exercises?.single()?.completedSets == 1 }
            val second = runtime.current()!!
            assertEquals(2, second.session.currentSet)
            assertEquals(hidden.runtimeRevision + 1, second.runtimeRevision)
            assertTrue(second.session.timedSetDeadlineEpochMillis!! - System.currentTimeMillis() in 6_000..8_000)
            snapshot("caught-up", second)
            await { runtime.current()?.finalResult != null }
            val completed = runtime.current()!!
            assertNativeSpeech(listOf(WatchCueKind.WORKOUT_SUCCESS))
            main { model.onCompleteSet() }; delay(500); assertEquals(completed, runtime.current())
            scenario.onActivity { it.setContent { PasingotTheme { Text("Reopening") } } }
            main { models.clear() }
            open(runtime, cues, scenario); delay(750)
            assertEquals(completed, runtime.current())
            assertEquals(1, speechList().size); assertEquals(1, kinds().size); assertEquals(1, sent)
            snapshot("completed", completed)
            File(folder, "cue-state.json").writeText(json.encodeToString(cues.state()))
        } }
    }
    @Test fun prepareNativePausedRuntime() = runBlocking {
        guard("paused")
        scenario().use { scenario -> fixture { runtime, cues, packages ->
            start(runtime, cues, packages, seconds = 20, sets = 1, rest = 0)
            val model = open(runtime, cues, scenario); delay(1_250)
            main { model.onPause() }
            await { runtime.current()?.session?.status == SessionStatus.PAUSED }
            val paused = runtime.current()!!
            assertTrue(paused.session.pausedTimedSetRemainingMillis!! in 1..20_000)
            assertNull(paused.session.timedSetDeadlineEpochMillis)
            snapshot("paused", paused)
            File(folder, "paused-cues.json").writeText(json.encodeToString(cues.state()))
            File(folder, "prepared-boot.txt").writeText(shell("cat /proc/sys/kernel/random/boot_id"))
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty())
        } }
    }
    @Test fun recoverNativePausedRuntime() = runBlocking {
        guard("paused", recover = true)
        assertNotEquals(File(folder, "prepared-boot.txt").readText(), shell("cat /proc/sys/kernel/random/boot_id"))
        scenario().use { scenario -> fixture { runtime, cues, _ ->
            val paused = runtime.current()!!
            assertEquals(File(folder, "paused.json").readText(), json.encodeToString(paused))
            val ledger = cues.state()
            assertEquals(File(folder, "paused-cues.json").readText(), json.encodeToString(ledger))
            val model = open(runtime, cues, scenario)
            val started = json.decodeFromString<QuickStartRuntimeState>(File(folder, "started.json").readText())
            await(25_000) { System.currentTimeMillis() > started.session.timedSetDeadlineEpochMillis!! + 1_000 }
            assertEquals(paused, runtime.current()); assertEquals(ledger, cues.state())
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty())
            val resumeAt = System.currentTimeMillis()
            clockReads.clear()
            main { model.onResume() }
            await { runtime.current()?.session?.status == SessionStatus.ACTIVE }
            val resumed = runtime.current()!!
            val origin = resumed.session.timedSetDeadlineEpochMillis!! - paused.session.pausedTimedSetRemainingMillis!!
            val reads = synchronized(clockReads) { clockReads.toList() }
            assertTrue("Deadline must add exact frozen milliseconds to a real transition clock read", origin in reads)
            assertTrue(origin in resumeAt..System.currentTimeMillis())
            assertNull(resumed.session.pausedTimedSetRemainingMillis)
            assertEquals(paused.runtimeRevision + 1, resumed.runtimeRevision)
            assertEquals(paused.outcomes, resumed.outcomes)
            assertEquals(paused.startedAcknowledgement, resumed.startedAcknowledgement)
            File(folder, "resume-clock-reads.json").writeText(json.encodeToString(reads))
            snapshot("resumed", resumed)
            await(25_000) { runtime.current()?.finalResult != null }
            assertNativeSpeech(listOf(WatchCueKind.WORKOUT_SUCCESS)); assertEquals(1, sent)
            snapshot("completed", runtime.current()!!)
            File(folder, "cue-state.json").writeText(json.encodeToString(cues.state()))
        } }
    }
    @Test fun prepareSpeakingRestRecovery() = runBlocking {
        guard("speaking-rest")
        scenario().use { scenario -> fixture { runtime, cues, packages ->
            start(runtime, cues, packages, seconds = 6, sets = 2, rest = 30)
            val model = open(runtime, cues, scenario)
            await { runtime.current()?.session?.status == SessionStatus.RESTING }
            await { speechList().any { it.kind == WatchCueKind.REST && it.observed && !it.finished } }
            val resting = runtime.current()!!
            main {
                assertFalse("Pause must interrupt native REST", speechList().single().finished)
                model.onPause()
            }
            await { runtime.current()?.session?.status == SessionStatus.PAUSED && speechList().all { it.finished } }
            val paused = runtime.current()!!
            assertEquals(1, paused.outcomes.exercises.single().completedSets)
            assertTrue(paused.session.pausedRestRemainingSeconds!! in 6..30)
            assertNull(paused.session.restUntilEpochMillis)
            assertNull(paused.session.pausedTimedSetRemainingMillis)
            assertEquals(listOf(WatchCueKind.REST), kinds())
            assertFalse(speechList().single().success)
            snapshot("paused-rest", paused)
            File(folder, "paused-rest-cues.json").writeText(json.encodeToString(cues.state()))
            File(folder, "abandoned-rest-deadline.txt").writeText(resting.session.restUntilEpochMillis.toString())
            File(folder, "prepared-boot.txt").writeText(shell("cat /proc/sys/kernel/random/boot_id"))
            assertEquals(0, sent)
        } }
    }

    @Test fun recoverSpeakingRestAndStartNow() = runBlocking {
        guard("speaking-rest", recover = true)
        assertNotEquals(File(folder, "prepared-boot.txt").readText(), shell("cat /proc/sys/kernel/random/boot_id"))
        scenario().use { scenario -> fixture { runtime, cues, _ ->
            val paused = runtime.current()!!
            assertEquals(File(folder, "paused-rest.json").readText(), json.encodeToString(paused))
            val ledger = cues.state()
            assertEquals(File(folder, "paused-rest-cues.json").readText(), json.encodeToString(ledger))
            val model = open(runtime, cues, scenario)
            val oldDeadline = File(folder, "abandoned-rest-deadline.txt").readText().toLong()
            await(35_000) { System.currentTimeMillis() > oldDeadline + 1_000 }
            assertEquals(paused, runtime.current()); assertEquals(ledger, cues.state())
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty())
            clockReads.clear()
            main { model.onResume() }
            await { runtime.current()?.session?.status == SessionStatus.RESTING }
            val resumed = runtime.current()!!
            val deadline = resumed.session.restUntilEpochMillis!!
            assertTrue(deadline - paused.session.pausedRestRemainingSeconds!! * 1_000 in
                synchronized(clockReads) { clockReads.toList() })
            snapshot("resumed-rest", resumed)
            await { speechList().any { it.kind == WatchCueKind.REST && it.observed && !it.finished } }
            var startNowAt = 0L
            main {
                assertFalse("Start now must interrupt native in-flight REST", speechList().single().finished)
                startNowAt = System.currentTimeMillis()
                model.onStartNow()
            }
            await { speechList().any { it.kind == WatchCueKind.GO && it.observed } }
            val go = speechList().single { it.kind == WatchCueKind.GO }
            assertTrue(requireNotNull(go.observedAt) - startNowAt in 0..2_000)
            val active = runtime.current()!!
            assertEquals(SessionStatus.ACTIVE, active.session.status)
            assertEquals(1, active.outcomes.exercises.single().completedSets)
            assertEquals(2, active.session.currentSet)
            assertEquals(resumed.runtimeRevision + 1, active.runtimeRevision)
            assertNull(active.session.restUntilEpochMillis)
            snapshot("start-now-active", active)
            await { runtime.current()?.finalResult != null }
            await { speechList().size == 3 && speechList().all { it.finished } }
            assertEquals(listOf(WatchCueKind.REST, WatchCueKind.GO, WatchCueKind.WORKOUT_SUCCESS), kinds())
            assertEquals(kinds(), speechList().map { it.kind })
            assertFalse(speechList().first().success)
            assertTrue(speechList().drop(1).all { it.observed && it.success })
            val completed = runtime.current()!!
            assertEquals(2, completed.outcomes.exercises.single().completedSets)
            assertEquals(1, sent)
            val completedLedger = cues.state()
            // Keep the actual screen foreground through the abandoned resumed-rest deadline.
            await(35_000) { System.currentTimeMillis() > deadline + 1_000 }
            assertEquals(completed, runtime.current()); assertEquals(completedLedger, cues.state())
            assertEquals(3, speechList().size); assertEquals(3, kinds().size)
            assertFalse(kinds().contains(WatchCueKind.FIVE_SECONDS))
            snapshot("interrupted-completed", completed)
            File(folder, "interrupted-completed-cues.json").writeText(json.encodeToString(completedLedger))
            File(folder, "start-now-wall.txt").writeText(startNowAt.toString())
            File(folder, "resumed-abandoned-deadline.txt").writeText(deadline.toString())
            File(folder, "completed-boot.txt").writeText(shell("cat /proc/sys/kernel/random/boot_id"))
        } }
    }

    @Test fun prepareInterruptedReceiptCleanup() = runBlocking {
        guard("speaking-rest", recover = true)
        assertNotEquals(File(folder, "completed-boot.txt").readText(), shell("cat /proc/sys/kernel/random/boot_id"))
        scenario().use { scenario -> fixture { runtime, cues, packages ->
            val completed = runtime.current()!!
            assertEquals(File(folder, "interrupted-completed.json").readText(), json.encodeToString(completed))
            val ledger = cues.state()
            assertEquals(File(folder, "interrupted-completed-cues.json").readText(), json.encodeToString(ledger))
            val priorPackage = packages.current(System.currentTimeMillis())
            open(runtime, cues, scenario); delay(750)
            assertEquals(completed, runtime.current()); assertEquals(ledger, cues.state())
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty()); assertEquals(0, sent)
            val result = completed.finalResult!!
            val watch = completed.sessionPackage.request.targetNodeId
            val receipt = QuickStartResultReceipt(result.requestId, result.resultId, result.outcomeRevision,
                result.phoneNodeId, System.currentTimeMillis())
            fun payload(value: QuickStartResultReceipt) = encodeQuickStartResultReceiptEnvelope(
                QuickStartResultReceiptEnvelope(QUICK_START_RESULT_SCHEMA_VERSION, watch,
                    QuickStartResultReceiptStatus.PERSISTED, value))
            val path = quickStartResultReceiptPath(result.requestId, result.resultId)
            val coordinator = QuickStartRuntimeResultCoordinator(runtime, packages, cues::clearAcknowledgedSession)
            for ((raw, sender, incomingPath) in listOf(
                Triple(payload(receipt), "wrong-phone", path),
                Triple(payload(receipt.copy(outcomeRevision = receipt.outcomeRevision + 1)), result.phoneNodeId, path),
                Triple(payload(receipt), result.phoneNodeId, path + "-wrong"))) {
                assertEquals(QuickStartResultCleanupResult.Mismatch,
                    coordinator.acknowledgePayloadAndPrune(raw, incomingPath, sender, watch))
                assertEquals(completed, runtime.current()); assertEquals(ledger, cues.state())
                assertEquals(priorPackage, packages.current(System.currentTimeMillis()))
            }
            var failed = false
            val failing = QuickStartRuntimeResultCoordinator(runtime, packages) {
                assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
                error("intentional cue-pruning failure after durable receipt cleanup")
            }
            try { failing.acknowledgePayloadAndPrune(payload(receipt), path, result.phoneNodeId, watch) }
            catch (failure: IllegalStateException) {
                assertEquals("intentional cue-pruning failure after durable receipt cleanup", failure.message)
                failed = true
            }
            assertTrue(failed); assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
            assertEquals(ledger, cues.state())
            File(folder, "interrupted-receipt.json").writeText(json.encodeToString(receipt))
            File(folder, "partial-cleanup-boot.txt").writeText(shell("cat /proc/sys/kernel/random/boot_id"))
        } }
    }

    @Test fun recoverInterruptedReceiptCleanup() = runBlocking {
        guard("speaking-rest", recover = true)
        assertNotEquals(File(folder, "partial-cleanup-boot.txt").readText(), shell("cat /proc/sys/kernel/random/boot_id"))
        scenario().use { fixture { runtime, cues, packages ->
            val completed = json.decodeFromString<QuickStartRuntimeState>(File(folder, "interrupted-completed.json").readText())
            val result = completed.finalResult!!
            val watch = completed.sessionPackage.request.targetNodeId
            val receipt = json.decodeFromString<QuickStartResultReceipt>(File(folder, "interrupted-receipt.json").readText())
            assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
            assertEquals(File(folder, "interrupted-completed-cues.json").readText(), json.encodeToString(cues.state()))
            val payload = encodeQuickStartResultReceiptEnvelope(QuickStartResultReceiptEnvelope(
                QUICK_START_RESULT_SCHEMA_VERSION, watch, QuickStartResultReceiptStatus.PERSISTED, receipt))
            val path = quickStartResultReceiptPath(result.requestId, result.resultId)
            val coordinator = QuickStartRuntimeResultCoordinator(runtime, packages, cues::clearAcknowledgedSession)
            assertTrue(coordinator.acknowledgePayloadAndPrune(payload, path, result.phoneNodeId, watch)
                is QuickStartResultCleanupResult.AlreadyPruned)
            val tombstone = cues.state()
            assertTrue(tombstone.ledger.deliveredKeys.isEmpty()); assertNull(tombstone.ledgerSessionId)
            assertEquals(result.requestId, tombstone.acknowledgedSessionId)
            assertTrue(tombstone.acknowledgedWorkoutSuccess)
            val controller = WatchCueController(cues, output())
            try {
                for (kind in WatchCueKind.entries) assertEquals(WatchCueResult.DUPLICATE,
                    controller.emit(WatchCueEvent(result.requestId, 0, 0, 0, kind), "Go."))
            } finally { controller.close() }
            assertTrue(speechList().isEmpty()); assertTrue(kinds().isEmpty())
            assertEquals(tombstone, cues.state()); assertNull(runtime.current())
            assertTrue(runtime.initialize(completed.sessionPackage,
                SessionState(result.requestId, 0, 1, SessionStatus.ACTIVE), System.currentTimeMillis())
                is InitializeQuickStartRuntimeResult.AlreadyAcknowledged)
            assertTrue(packages.accept(completed.sessionPackage.request, System.currentTimeMillis(), result.phoneNodeId)
                is AcceptQuickStartResult.PreviouslyAcknowledged)
            assertNull(runtime.current()); assertNull(packages.current(System.currentTimeMillis()))
            assertTrue(coordinator.acknowledgePayloadAndPrune(payload, path, result.phoneNodeId, watch)
                is QuickStartResultCleanupResult.AlreadyPruned)
            File(folder, "pruned-cues.json").writeText(json.encodeToString(cues.state()))
        } }
    }

    private data class Speech(val kind: WatchCueKind, val started: Long, val text: String,
        @Volatile var observed: Boolean = false, @Volatile var finished: Boolean = false,
        @Volatile var success: Boolean = false, @Volatile var observedAt: Long? = null)
    private inner class RecordingOutput(private val native: AndroidTtsCueOutput) : WatchCueOutput {
        private val engine get() = AndroidTtsCueOutput::class.java.getDeclaredField("tts")
            .apply { isAccessible = true }.get(native) as TextToSpeech
        override val talkBackEnabled get() = native.talkBackEnabled
        override suspend fun speak(utteranceId: String, text: String): Boolean = coroutineScope {
            val record = Speech(WatchCueKind.valueOf(utteranceId.split('|')[4]), System.currentTimeMillis(), text)
            speeches += record
            val monitor = launch { while (isActive) { if (engine.isSpeaking) {
                record.observed = true
                if (record.observedAt == null) record.observedAt = System.currentTimeMillis()
            }; delay(20) } }
            try { native.speak(utteranceId, text).also { record.success = it } }
            finally { monitor.cancelAndJoin(); record.finished = true }
        }
        override fun haptic(kind: WatchCueKind) { haptics += kind to System.currentTimeMillis(); native.haptic(kind) }
        override fun cancel(reason: WatchCueCancellation) = native.cancel(reason)
        override fun close() = native.close()
    }
}
