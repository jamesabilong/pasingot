package app.personal.workouttracker.wear.cues

import android.Manifest
import android.app.UiAutomation
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartRuntimePersistence
import app.personal.workouttracker.wear.quickstart.QuickStartRuntimeStore
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual native output/focus; synthetic cue storage never alters workout history. */
@RunWith(AndroidJUnit4::class)
class NativeSpeechInterruptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation by lazy { instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES) }
    private val evidence = mutableListOf<String>()
    private val folder by lazy { File(context.getExternalFilesDir(null), "speech-interruption").apply { mkdirs() } }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private suspend fun requireCopy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeSpeechInterruptionValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Pasingot_Matrix_Wear", shell("getprop ro.boot.qemu.avd_name"))
        assertNull("Preserve existing active workouts", QuickStartRuntimeStore(DataStoreQuickStartRuntimePersistence(context)).current())
        assertFalse("Preserve existing downloaded workouts", WorkoutRepository(context).entries.first().any {
            it.sessionState?.status in listOf(SessionStatus.ACTIVE, SessionStatus.RESTING, SessionStatus.PAUSED)
        })
        assertFalse("Preserve existing native focus owners", focusStack(shell("dumpsys audio")).contains("client:"))
    }

    private suspend fun output(): AndroidTtsCueOutput {
        val output = withContext(Dispatchers.Main) { AndroidTtsCueOutput(context) }
        try {
            withTimeout(20_000) {
                while (VoiceCueAvailabilityRegistry.availability.value in listOf(
                        VoiceCueAvailability.CHECKING, VoiceCueAvailability.ENGINE_AVAILABLE)) delay(50)
            }
            assertFalse("Restore accessibility baseline before interruption testing", output.talkBackEnabled)
            return output
        } catch (failure: Throwable) { output.close(); throw failure }
    }

    private fun engine(output: AndroidTtsCueOutput): TextToSpeech =
        AndroidTtsCueOutput::class.java.getDeclaredField("tts").apply { isAccessible = true }.get(output) as TextToSpeech

    private suspend fun awaitSpeaking(output: AndroidTtsCueOutput, speaking: Boolean) = withTimeout(2_000) {
        while (engine(output).isSpeaking != speaking) delay(20)
    }

    private fun store() = WatchCueStore(object : WatchCuePersistence {
        var raw: String? = null
        override suspend fun read() = raw
        override suspend fun write(value: String) { raw = value }
    })

    private suspend fun witness(name: String, stage: String): String = buildJsonObject {
        put("cues", JsonPrimitive(DataStoreWatchCuePersistence(context).read()))
        put("runtime", JsonPrimitive(DataStoreQuickStartRuntimePersistence(context).read()))
        put("entries", Json.encodeToJsonElement(WorkoutRepository(context).entries.first()))
    }.toString().also { File(folder, "$name-$stage.json").writeText(it) }

    private fun focusStack(dump: String) = dump.substringAfter("Audio Focus stack entries (last is top of stack):")
        .substringBefore("No external focus policy").substringBefore("External focus policy")

    @Test fun cancellationReplacementAndNativeFocusLoss() = runBlocking {
        requireCopy()
        val before = witness("interruption", "before")
        val scenario = ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java))
        var controller: WatchCueController? = null
        val audio = context.getSystemService(AudioManager::class.java)
        val competitor = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            val native = output()
            assertEquals(VoiceCueAvailability.AVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            val store = store().also { it.setPreferences(WatchCuePreferences(voiceEnabled = true)) }
            val cues = WatchCueController(store, native)
            controller = cues
            var revision = 0L
            for (reason in listOf(WatchCueCancellation.PAUSE, WatchCueCancellation.START_NOW,
                    WatchCueCancellation.NAVIGATION, WatchCueCancellation.END)) {
                val event = WatchCueEvent("native-interruption", revision++, 0, 0, WatchCueKind.REST)
                val pending = async { cues.emit(event, LONG_SCRIPT) }
                awaitSpeaking(native, true)
                assertFalse("Cancel must target an in-flight utterance", pending.isCompleted)
                val start = SystemClock.elapsedRealtime()
                cues.cancel(reason)
                assertEquals(WatchCueResult.HAPTIC_ONLY, withTimeout(1_500) { pending.await() })
                awaitSpeaking(native, false)
                assertEquals(WatchCueResult.DUPLICATE, cues.emit(event, LONG_SCRIPT))
                val recovery = event.copy(revision = revision++, kind = WatchCueKind.GO)
                assertEquals(WatchCueResult.SPOKEN, cues.emit(recovery, "Go."))
                evidence += "$reason interrupted before controller timeout; recovery SPOKEN; elapsed=${SystemClock.elapsedRealtime() - start}ms"
            }
            val rest = WatchCueEvent("native-interruption", revision++, 0, 0, WatchCueKind.REST)
            val pending = async { cues.emit(rest, LONG_SCRIPT) }
            awaitSpeaking(native, true)
            File(folder, "focus-during-speech.txt").writeText(shell("dumpsys audio"))
            val go = rest.copy(revision = revision++, kind = WatchCueKind.GO)
            assertEquals("A stale stop callback must not finish the replacement", WatchCueResult.SPOKEN, cues.emit(go, "Go."))
            assertEquals(WatchCueResult.HAPTIC_ONLY, withTimeout(1_500) { pending.await() })
            assertEquals(WatchCueResult.DUPLICATE, cues.emit(go, "Go."))
            evidence += "priority replacement: old HAPTIC_ONLY, new SPOKEN, duplicate suppressed"

            val focusEvent = go.copy(revision = revision++, kind = WatchCueKind.REST)
            val interrupted = async { cues.emit(focusEvent, LONG_SCRIPT) }
            awaitSpeaking(native, true)
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competitor))
            assertEquals(WatchCueResult.HAPTIC_ONLY, withTimeout(1_500) { interrupted.await() })
            awaitSpeaking(native, false)
            audio.abandonAudioFocusRequest(competitor)
            assertEquals(WatchCueResult.SPOKEN, cues.emit(go.copy(revision = revision++), "Go."))
            assertEquals(WatchCueResult.DUPLICATE, cues.emit(focusEvent, LONG_SCRIPT))
            evidence += "native transient focus loss stops speech; focus release allows a new SPOKEN cue"
            assertEquals(revision.toInt(), store.state().ledger.deliveredKeys.size)
        } finally {
            audio.abandonAudioFocusRequest(competitor)
            controller?.close()
            val finalFocus = shell("dumpsys audio")
            File(folder, "focus-after-close.txt").writeText(finalFocus)
            assertFalse("Close must release native cue focus", focusStack(finalFocus).contains("pack: app.personal.workouttracker"))
            scenario.close()
            assertEquals(before, witness("interruption", "after"))
            File(folder, "interruption.txt").writeText(evidence.joinToString("\n"))
        }
    }

    @Test fun unsupportedLanguageFallsBackAndRestores() = runBlocking {
        requireCopy()
        val before = witness("language", "before")
        val locale = Locale.getDefault()
        val scenario = ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java))
        var controller: WatchCueController? = null
        try {
            Locale.setDefault(Locale.forLanguageTag("zz-ZZ"))
            val native = output()
            assertEquals(VoiceCueAvailability.LANGUAGE_UNAVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            val store = store().also { it.setPreferences(WatchCuePreferences(voiceEnabled = true)) }
            val cues = WatchCueController(store, native)
            controller = cues
            val event = WatchCueEvent("native-language", 0, 0, 0, WatchCueKind.GO)
            assertEquals(WatchCueResult.HAPTIC_ONLY, cues.emit(event, "Go."))
            assertEquals(WatchCueResult.DUPLICATE, cues.emit(event, "Go."))
            assertFalse(engine(native).isSpeaking)
            cues.close()
            controller = null
            Locale.setDefault(locale)
            val restored = output()
            controller = WatchCueController(store, restored)
            assertEquals(VoiceCueAvailability.AVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            assertEquals(WatchCueResult.SPOKEN, controller.emit(event.copy(revision = 1), "Go."))
            evidence += "zz-ZZ: LANGUAGE_UNAVAILABLE/HAPTIC_ONLY, duplicate suppressed; restored $locale: AVAILABLE/SPOKEN"
        } finally {
            controller?.close()
            Locale.setDefault(locale)
            scenario.close()
            assertEquals(locale, Locale.getDefault())
            assertEquals(before, witness("language", "after"))
            File(folder, "language.txt").writeText(evidence.joinToString("\n"))
        }
    }

    @Test fun lockedNativeFocusFallsBackAndRecovers() = runBlocking {
        requireCopy()
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeLockedFocusValidation") == "true")
        val before = witness("focus-denial", "before")
        // A copied-emulator-only system fixture; no telephone call is made.
        // The caller explicitly disables hidden-API checks for this instrumentation.
        assertFalse("Do not disturb an existing call focus owner", focusStack(shell("dumpsys audio")).contains("client: AudioFocus_For_Phone_Ring_And_Calls"))
        val scenario = ActivityScenario.launch<WearMainActivity>(Intent(context, WearMainActivity::class.java))
        val audio = context.getSystemService(AudioManager::class.java)
        var controller: WatchCueController? = null
        var locked = false
        var releaseFocus: java.lang.reflect.Method? = null
        try {
            val native = output()
            assertEquals(VoiceCueAvailability.AVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            val store = store().also { it.setPreferences(WatchCuePreferences(voiceEnabled = true)) }
            val cues = WatchCueController(store, native)
            controller = cues
            automation.adoptShellPermissionIdentity(Manifest.permission.MODIFY_PHONE_STATE, "android.permission.MODIFY_AUDIO_ROUTING")
            val requestFocus = AudioManager::class.java.getMethod("requestAudioFocusForCall", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            releaseFocus = AudioManager::class.java.getMethod("abandonAudioFocusForCall")
            locked = true
            requestFocus.invoke(audio, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            val focus = shell("dumpsys audio")
            assertTrue("A real native focus lock is required", focusStack(focus).contains("client: AudioFocus_For_Phone_Ring_And_Calls"))
            File(folder, "focus-locked.txt").writeText(focus)
            val event = WatchCueEvent("native-focus-denial", 0, 0, 0, WatchCueKind.GO)
            assertEquals(WatchCueResult.HAPTIC_ONLY, cues.emit(event, "Go."))
            assertEquals(VoiceCueAvailability.AUDIO_FOCUS_UNAVAILABLE, VoiceCueAvailabilityRegistry.availability.value)
            assertEquals(WatchCueResult.DUPLICATE, cues.emit(event, "Go."))
            releaseFocus.invoke(audio)
            locked = false
            assertEquals(WatchCueResult.SPOKEN, cues.emit(event.copy(revision = 1), "Go."))
            evidence += "locked native focus: AUDIO_FOCUS_UNAVAILABLE/HAPTIC_ONLY; duplicate suppressed; unlock: SPOKEN"
        } finally {
            if (locked) releaseFocus?.invoke(audio)
            automation.dropShellPermissionIdentity()
            controller?.close()
            scenario.close()
            assertFalse("Fixture must release its native focus lock", focusStack(shell("dumpsys audio")).contains("client: AudioFocus_For_Phone_Ring_And_Calls"))
            assertEquals(before, witness("focus-denial", "after"))
            File(folder, "focus-denial.txt").writeText(evidence.joinToString("\n"))
        }
    }

    private companion object {
        val LONG_SCRIPT = "This spoken acceptance cue stays active until the watch cancels it. ".repeat(20)
    }
}
