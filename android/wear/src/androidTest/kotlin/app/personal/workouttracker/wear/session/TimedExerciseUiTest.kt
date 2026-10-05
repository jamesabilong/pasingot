package app.personal.workouttracker.wear.session

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.shared.*
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.WorkoutSessionStore
import app.personal.workouttracker.wear.data.WorkoutSessionEffects
import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.wear.ui.PasingotTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Disposable blank AVD, detached in-memory workout; no user history or cue preferences mutated. */
@RunWith(AndroidJUnit4::class)
class TimedExerciseUiTest {
    @Test fun countdownPauseResumeAutoRestAndRepLayout() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("timedExerciseUiValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val ui = instrumentation.uiAutomation
        val name = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("getprop ro.boot.qemu.avd_name"))
            .bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Timed_UI", name)
        val store = object : WorkoutSessionStore {
            @Volatile var entry = DownloadedWorkoutEntry("timed-ui", "2026-10-05", "UI fixture",
                listOf(WorkoutExercise("Plank", "20 sec", 1, 8), WorkoutExercise("Squat", "8", 2, 10)),
                sessionState = SessionState("timed-ui", 0, 1, SessionStatus.ACTIVE,
                    elapsedStartedAtEpochMillis = System.currentTimeMillis()))
            override suspend fun getEntry(entryId: String) = entry
            override suspend fun commitSession(expected: DownloadedWorkoutEntry, updated: DownloadedWorkoutEntry,
                effects: WorkoutSessionEffects?, action: SessionOutcomeAction?): Boolean {
                if (entry != expected) return false
                entry = updated
                return true
            }
            override suspend fun flushPendingEffects(sender: LogSender) = Unit
        }
        val sender = object : LogSender {
            override suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?) = Unit
            override suspend fun sendEntry(entry: LogEntry) = Unit
            override suspend fun sendSessionSnapshot(snapshot: WatchSessionSnapshot) = Unit
            override suspend fun sendSessionEvent(event: WorkoutSessionEvent) = Unit
        }
        val models = ViewModelStore()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "timed-ui").apply { mkdirs() }
        fun capture(label: String) {
            requireNotNull(ui.takeScreenshot()).also { bitmap ->
                File(folder, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            ui.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                result += node
                for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
            }
            ui.rootInActiveWindow?.let(::visit)
            return result
        }
        suspend fun awaitLabel(text: String, allowScroll: Boolean = false): AccessibilityNodeInfo = withTimeout(15_000) {
            var attempts = 0
            while (true) {
                val current = nodes()
                current.firstOrNull { it.text?.toString() == text }?.let { return@withTimeout it }
                if (allowScroll && attempts++ % 3 == 2) {
                    val action = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    current.firstOrNull { node -> node.actionList.any { it.id == action } }?.performAction(action)
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("Missing $text")
        }
        suspend fun tap(text: String) {
            var control = awaitLabel(text, allowScroll = true)
            while (!control.isClickable) control = requireNotNull(control.parent)
            assertTrue(control.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        try {
            ActivityScenario.launch<WearMainActivity>(Intent(instrumentation.targetContext, WearMainActivity::class.java)).use { scenario ->
                lateinit var viewModel: SessionViewModel
                scenario.onActivity { activity ->
                    viewModel = SessionViewModel("timed-ui", store, sender, legacyStartGate = null)
                    models.put("timed", viewModel)
                    activity.setContent { PasingotTheme { SessionScreen(viewModel) {} } }
                }
                withTimeout(10_000) { while (viewModel.uiState.value.timedSetRemainingMillis == null) delay(50) }
                delay(750)
                capture("active")
                tap("Pause")
                withTimeout(5_000) { while (store.entry.sessionState?.status != SessionStatus.PAUSED) delay(50) }
                val frozen = store.entry.sessionState
                delay(750)
                capture("paused")
                delay(1_000)
                assertEquals(frozen, store.entry.sessionState)
                tap("Resume")
                withTimeout(5_000) { while (store.entry.sessionState?.status != SessionStatus.ACTIVE) delay(50) }
                withTimeout(25_000) { while ((viewModel.uiState.value.timedSetRemainingMillis ?: Long.MAX_VALUE) > 4_500) delay(50) }
                awaitLabel("Finishing")
                delay(350)
                capture("final-five")
                withTimeout(10_000) { while (store.entry.sessionState?.status != SessionStatus.RESTING) delay(50) }
                assertEquals(1, store.entry.sessionState?.progress?.completedSets?.first())
                delay(500)
                capture("rest")
                withTimeout(15_000) { while (store.entry.sessionState?.status != SessionStatus.ACTIVE) delay(50) }
                assertNull(viewModel.uiState.value.timedSetRemainingMillis)
                assertEquals(1, store.entry.sessionState?.exerciseIndex)
                awaitLabel("Complete set", allowScroll = true)
                delay(500)
                capture("reps")
                tap("Pause")
                File(folder, "checks.txt").writeText("native round layout; pause exact; resume; final five; auto rest once; rep layout manual\n")
            }
        } finally { instrumentation.runOnMainSync { models.clear() } }
    }
}
