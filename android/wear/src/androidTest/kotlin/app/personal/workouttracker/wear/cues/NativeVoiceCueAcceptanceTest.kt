package app.personal.workouttracker.wear.cues

import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.wear.WearMainActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native output, isolated cue persistence: never changes a user's session or preferences. */
@RunWith(AndroidJUnit4::class)
class NativeVoiceCueAcceptanceTest {
    @Test fun foregroundAvailabilityCategoriesAndAccessibilitySuppression() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("nativeVoiceCueValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Ordinary UiAutomation suppresses enabled accessibility services.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val avd = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        ).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Wear", avd)
        val context = instrumentation.targetContext
        val expectedTalkBack = args.getString("expectTalkBack") == "true"
        val evidence = mutableListOf<String>()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        withTimeout(15_000) {
            while ((manager.isEnabled && manager.isTouchExplorationEnabled) != expectedTalkBack) delay(100)
        }
        val services = manager.getEnabledAccessibilityServiceList(-1).map { it.resolveInfo.serviceInfo.packageName }
        if (expectedTalkBack) assertTrue("An actual TalkBack service is required", services.any { "talkback" in it })
        evidence += "touchExploration=$expectedTalkBack services=$services"
        val discovery = AndroidVoiceCueAvailabilityProbe(context).check()
        evidence += "discovery=$discovery"
        val unavailable = args.getString("expectUnavailableVoice") == "true"
        if (unavailable) assertEquals(VoiceCueAvailability.SERVICE_UNAVAILABLE, discovery)
        val persistence = object : WatchCuePersistence {
            var raw: String? = null
            override suspend fun read() = raw
            override suspend fun write(value: String) { raw = value }
        }
        val store = WatchCueStore(persistence)
        val scenario = ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java))
        var controller: WatchCueController? = null
        try {
            val native = withContext(Dispatchers.Main) { AndroidTtsCueOutput(context) }
            val output = RecordingOutput(native)
            val activeController = WatchCueController(store, output)
            controller = activeController
            withTimeout(20_000) {
                while (VoiceCueAvailabilityRegistry.availability.value == VoiceCueAvailability.CHECKING) delay(100)
            }
            assertEquals(expectedTalkBack, native.talkBackEnabled)
            evidence += "initialized=${VoiceCueAvailabilityRegistry.availability.value}"
            if (unavailable) assertEquals(VoiceCueAvailability.INITIALIZATION_FAILED, VoiceCueAvailabilityRegistry.availability.value)
            var revision = 0L
            suspend fun exercise(kind: WatchCueKind, preferences: WatchCuePreferences, suppressed: Boolean) {
                store.setPreferences(preferences)
                val event = WatchCueEvent("native-voice-acceptance", revision++, 0, 0, kind)
                val beforeSpeech = output.speechCalls
                val beforeHaptics = output.hapticCalls
                val result = activeController.emit(event, "Go.")
                assertEquals(beforeHaptics + 1, output.hapticCalls)
                assertEquals(beforeSpeech + if (suppressed) 0 else 1, output.speechCalls)
                if (suppressed) assertEquals(WatchCueResult.HAPTIC_ONLY, result)
                else assertTrue(result in listOf(WatchCueResult.SPOKEN, WatchCueResult.HAPTIC_ONLY))
                if (unavailable) assertEquals(WatchCueResult.HAPTIC_ONLY, result)
                assertTrue(event.key in store.state().ledger.deliveredKeys)
                // Recreate the controller/store against the same persisted record.
                val recovered = WatchCueController(WatchCueStore(persistence), output)
                assertEquals(WatchCueResult.DUPLICATE, recovered.emit(event, "Go."))
                assertEquals(beforeHaptics + 1, output.hapticCalls)
                assertEquals(beforeSpeech + if (suppressed) 0 else 1, output.speechCalls)
                evidence += "${event.key} preferences=$preferences result=$result speech=${output.speechCalls} haptics=${output.hapticCalls} availability=${VoiceCueAvailabilityRegistry.availability.value}"
            }
            val enabled = WatchCuePreferences(voiceEnabled = true, voicePromptResolved = true)
            for (kind in WatchCueKind.entries) {
                exercise(kind, enabled, suppressed = expectedTalkBack)
                val disabled = when (kind) {
                    WatchCueKind.BRIEFING -> enabled.copy(startBriefing = false)
                    WatchCueKind.REST -> enabled.copy(restAnnouncements = false)
                    WatchCueKind.FIVE_SECONDS, WatchCueKind.GO -> enabled.copy(countdown = false)
                    WatchCueKind.EXERCISE_SUCCESS, WatchCueKind.WORKOUT_SUCCESS -> enabled.copy(completion = false)
                }
                exercise(kind, disabled, suppressed = true)
                exercise(kind, enabled.copy(voiceEnabled = false), suppressed = true)
            }
            assertEquals(18, output.hapticCalls)
            assertEquals(if (expectedTalkBack) 0 else 6, output.speechCalls)
            assertEquals(18, store.state().ledger.deliveredKeys.size)
            evidence += "PASS: 18 reservations, 18 native haptic calls; duplicate recreation produces no output"
        } finally {
            controller?.close()
            scenario.close()
            val folder = File(context.getExternalFilesDir(null), "voice-acceptance").apply { mkdirs() }
            val name = when { expectedTalkBack -> "talkback"; unavailable -> "unavailable"; else -> "foreground" }
            File(folder, "$name.txt").writeText(evidence.joinToString("\n"))
            evidence.forEach(::println)
        }
    }

    private class RecordingOutput(private val native: AndroidTtsCueOutput) : WatchCueOutput {
        var speechCalls = 0
        var hapticCalls = 0
        override val talkBackEnabled get() = native.talkBackEnabled
        override suspend fun speak(utteranceId: String, text: String): Boolean {
            speechCalls++
            return native.speak(utteranceId, text)
        }
        override fun haptic(kind: WatchCueKind) { hapticCalls++; native.haptic(kind) }
        override fun cancel(reason: WatchCueCancellation) = native.cancel(reason)
        override fun close() = native.close()
    }
}

