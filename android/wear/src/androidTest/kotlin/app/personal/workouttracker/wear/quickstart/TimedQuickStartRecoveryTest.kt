package app.personal.workouttracker.wear.quickstart

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.*
import app.personal.workouttracker.wear.session.NoOpSessionCueEmitter
import app.personal.workouttracker.wear.session.SessionCueEmitter
import app.personal.workouttracker.wear.session.SessionViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit stages across operator force-stop/reboot; only an owned file on a blank AVD. */
@RunWith(AndroidJUnit4::class)
class TimedQuickStartRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val json = Json { encodeDefaults = true }
    private val clock = AtomicLong(NOW)
    private val models = ViewModelStore()
    @Volatile private var sent = 0
    @Volatile private var successes = 0
    private lateinit var folder: File

    private fun guard() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("timedQuickStartRecoveryValidation") == "true")
        assertTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val name = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("getprop ro.boot.qemu.avd_name")).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Timed_UI", name)
        folder = File(instrumentation.targetContext.filesDir, "timed-recovery-validation")
    }

    private fun bootId(): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
        .executeShellCommand("cat /proc/sys/kernel/random/boot_id")).bufferedReader().use {
            it.readText().trim().also { value -> require(value.matches(Regex("[0-9a-f-]{36}"))) }
        }
    private fun recordBoot(stage: String) {
        File(folder, "$stage-boot.txt").writeText(bootId())
        File(folder, "$stage-pid.txt").writeText(android.os.Process.myPid().toString())
    }
    private fun requireRebootAfter(stage: String) {
        assertNotEquals("Reboot the owned AVD between stages", File(folder, "$stage-boot.txt").readText(), bootId())
    }

    private fun fixture() = (validateQuickStartRequest(QuickStartRequest(
        requestId = ID, createdAtMillis = NOW, expiresAtMillis = NOW + 300_000,
        targetNodeId = "fixture-watch", title = "Native timed recovery", source = QuickStartSource.LIBRARY_PLAYLIST,
        exercises = listOf(QuickStartExercise("fixture-item", "plank", "Plank", 2, "30 sec", 0)),
    ), NOW) as QuickStartValidationResult.Valid).sessionPackage.copy(
        state = QuickStartPackageState.STARTING, sourcePhoneNodeId = PHONE,
    )

    private suspend fun <T> withStore(block: suspend (QuickStartRuntimeStore) -> T): T {
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                File(folder, "runtime.preferences_pb")
            }
            return block(QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(data)))
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            job.cancelAndJoin()
        }
    }

    private fun open(store: QuickStartRuntimeStore): SessionViewModel {
        lateinit var model: SessionViewModel
        val adapter = QuickStartSessionStore(ID, store, clock::get, QuickStartResultClient {
            assertEquals(it, store.current()!!.finalResult)
            sent++
            error("intentional offline transport")
        })
        val cues = object : SessionCueEmitter by NoOpSessionCueEmitter {
            override suspend fun workoutSuccess(entry: DownloadedWorkoutEntry, session: SessionState) {
                assertNotNull(store.current()!!.finalResult)
                successes++
            }
        }
        instrumentation.runOnMainSync {
            model = SessionViewModel(ID, adapter, NoOpQuickStartLogSender, clock::get,
                legacyStartGate = null, canAdjustSets = false, canRestart = false,
                awaitsPhoneReceipt = true, cueEmitter = cues)
            models.put("timed", model)
        }
        return model
    }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(25)
    }
    private fun snapshot(label: String, state: QuickStartRuntimeState) {
        File(folder, "$label.json").writeText(json.encodeToString(state))
    }
    private fun assertSnapshot(label: String, state: QuickStartRuntimeState) {
        assertEquals(File(folder, "$label.json").readText(), json.encodeToString(state))
    }

    @Test fun preparePausedRuntime() = runBlocking {
        guard()
        assertFalse("Do not overwrite an existing run; retain its evidence", folder.exists())
        assertTrue(folder.mkdirs())
        withStore { store ->
            assertTrue(store.initialize(fixture(), SessionState(ID, 0, 1, SessionStatus.ACTIVE,
                elapsedStartedAtEpochMillis = NOW, timedSetDeadlineEpochMillis = NOW + 30_000), NOW)
                is InitializeQuickStartRuntimeResult.Initialized)
            val model = open(store)
            await { !model.uiState.value.loading }
            val active = store.current()!!
            snapshot("active", active)
            clock.set(NOW + 1_250)
            main { model.onPause() }
            await { store.current()?.session?.status == SessionStatus.PAUSED }
            val paused = store.current()!!
            assertEquals(28_750L, paused.session.pausedTimedSetRemainingMillis)
            assertNull(paused.session.timedSetDeadlineEpochMillis)
            assertEquals(active.startedAcknowledgement, paused.startedAcknowledgement)
            assertEquals(0, paused.outcomes.exercises.single().completedSets)
            snapshot("paused", paused)
            recordBoot("paused")
            assertEquals(0, sent)
            assertEquals(0, successes)
        }
    }

    @Test fun recoverPausedAndPrepareHiddenRuntime() = runBlocking {
        guard()
        assertTrue(File(folder, "paused.json").isFile)
        requireRebootAfter("paused")
        clock.set(NOW + 90_000)
        withStore { store ->
            val paused = store.current()!!
            assertSnapshot("paused", paused)
            val model = open(store)
            await { !model.uiState.value.loading }
            main { model.onScreenVisibilityChanged(true) }
            delay(500)
            assertEquals(paused, store.current())
            assertEquals(28_750L, model.uiState.value.timedSetRemainingMillis)
            main { model.onResume() }
            await { store.current()?.session?.status == SessionStatus.ACTIVE }
            main { model.onScreenVisibilityChanged(false) }
            val hidden = store.current()!!
            assertEquals(NOW + 118_750, hidden.session.timedSetDeadlineEpochMillis)
            assertNull(hidden.session.pausedTimedSetRemainingMillis)
            assertEquals(paused.startedAcknowledgement, hidden.startedAcknowledgement)
            assertEquals(0, hidden.outcomes.exercises.single().completedSets)
            snapshot("hidden", hidden)
            recordBoot("hidden")
            assertEquals(0, sent)
            assertEquals(0, successes)
        }
    }

    @Test fun recoverHiddenCompleteOfflineAndReceipt() = runBlocking {
        guard()
        assertTrue(File(folder, "hidden.json").isFile)
        requireRebootAfter("hidden")
        clock.set(NOW + 200_000)
        withStore { store ->
            val hidden = store.current()!!
            assertSnapshot("hidden", hidden)
            val model = open(store)
            await { !model.uiState.value.loading }
            delay(500)
            assertEquals(hidden, store.current())
            assertEquals(0L, model.uiState.value.timedSetRemainingMillis)
            assertEquals(0, sent)
            main { model.onScreenVisibilityChanged(true) }
            await { store.current()?.outcomes?.exercises?.single()?.completedSets == 1 }
            val caughtUp = store.current()!!
            assertEquals(2, caughtUp.session.currentSet)
            assertEquals(NOW + 230_000, caughtUp.session.timedSetDeadlineEpochMillis)
            assertEquals(hidden.runtimeRevision + 1, caughtUp.runtimeRevision)
            snapshot("caught-up", caughtUp)
            clock.set(NOW + 230_000)
            await { store.current()?.finalResult != null && successes == 1 }
            val completed = store.current()!!
            assertEquals(SessionStatus.COMPLETED, completed.session.status)
            assertEquals(2, completed.outcomes.exercises.single().completedSets)
            assertNull(completed.session.timedSetDeadlineEpochMillis)
            assertEquals(1, sent)
            main { model.onCompleteSet() }
            delay(500)
            assertEquals(completed, store.current())
            assertEquals(1, successes)
            snapshot("completed", completed)
            main { models.clear() }
            val reopened = open(store)
            await { !reopened.uiState.value.loading }
            main { reopened.onScreenVisibilityChanged(true) }
            delay(500)
            assertEquals(completed, store.current())
            assertEquals(1, sent)
            assertEquals(1, successes)
            val result = completed.finalResult!!
            val receipt = QuickStartResultReceipt(ID, result.resultId, result.outcomeRevision, PHONE, NOW + 231_000)
            assertEquals(ClearQuickStartRuntimeResult.MISMATCH,
                store.clearAcknowledged(receipt.copy(outcomeRevision = result.outcomeRevision + 1), PHONE))
            assertEquals(completed, store.current())
            assertEquals(ClearQuickStartRuntimeResult.CLEARED, store.clearAcknowledged(receipt, PHONE))
            File(folder, "receipt.json").writeText(json.encodeToString(receipt))
            recordBoot("receipt")
        }
    }

    @Test fun verifyReceiptAfterFreshProcess() = runBlocking {
        guard()
        requireRebootAfter("receipt")
        val receipt = json.decodeFromString<QuickStartResultReceipt>(File(folder, "receipt.json").readText())
        withStore { store ->
            assertNull(store.current())
            assertEquals(ClearQuickStartRuntimeResult.ALREADY_CLEARED, store.clearAcknowledged(receipt, PHONE))
            assertEquals(InitializeQuickStartRuntimeResult.AlreadyAcknowledged(receipt),
                store.initialize(fixture(), SessionState(ID, 0, 1, SessionStatus.ACTIVE), NOW))
            assertNull(store.current())
        }
    }

    private companion object {
        const val ID = "523e4567-e89b-12d3-a456-426614174082"
        const val PHONE = "fixture-phone"
        const val NOW = 2_000_000_000_000L
    }
}
