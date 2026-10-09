package app.personal.workouttracker.wear.cues

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Foreground-only system TTS. It never owns a service or wake lock. */
class AndroidTtsCueOutput(context: Context) : WatchCueOutput, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val accessibilityManager = appContext.getSystemService(AccessibilityManager::class.java)
    private val vibrator = appContext.getSystemService(Vibrator::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(audioAttributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                cancel(WatchCueCancellation.AUDIO_ROUTE)
            }
        }
        .build()
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = audioRouteChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = audioRouteChanged()
    }

    @Volatile private var initialized = false
    @Volatile private var closed = false
    @Volatile private var languageSupported = false
    private var audioCallbackRegistered = false
    private val initializationHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val speechLock = Any()
    private var speechLease: Any? = null
    private var finishSpeech: (() -> Unit)? = null

    init {
        VoiceCueAvailabilityRegistry.report(VoiceCueAvailability.CHECKING)
        tts = TextToSpeech(appContext, this)
    }

    override val talkBackEnabled: Boolean
        get() = accessibilityManager?.isEnabled == true && accessibilityManager.isTouchExplorationEnabled

    override fun onInit(status: Int) {
        // Missing engines can call onInit synchronously inside the constructor.
        // Defer until tts is assigned, and ignore callbacks after owner teardown.
        initializationHandler.post {
            if (closed) return@post
            val engine = tts ?: return@post
            initialized = status == TextToSpeech.SUCCESS
            languageSupported = initialized && engine.setLanguage(Locale.getDefault()) !in
                setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
            if (initialized && languageSupported) {
                engine.setAudioAttributes(audioAttributes)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
                audioCallbackRegistered = true
            }
            reportAvailability()
        }
    }

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        val engine = tts ?: return false
        if (closed || !initialized || !languageSupported || !hasAudioOutput()) {
            reportAvailability()
            return false
        }
        return suspendCancellableCoroutine { continuation ->
            val lease = Any()
            val completion = TtsUtteranceCompletion(utteranceId)
            fun finish(callbackId: String?, success: Boolean) = synchronized(speechLock) {
                // QUEUE_FLUSH can deliver the previous utterance's callback to
                // this new listener. It must not finish or unfocus the replacement.
                if (!completion.accept(callbackId)) return@synchronized
                if (speechLease === lease) {
                    speechLease = null
                    finishSpeech = null
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
                if (continuation.isActive) continuation.resume(success)
            }
            val listener = object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = finish(id, true)
                override fun onStop(id: String?, interrupted: Boolean) = finish(id, false)
                @Deprecated("Deprecated in Android")
                override fun onError(id: String?) = finish(id, false)
                override fun onError(id: String?, errorCode: Int) = finish(id, false)
            }
            continuation.invokeOnCancellation {
                synchronized(speechLock) {
                    if (!completion.accept(utteranceId)) return@synchronized
                    // A cancelled old coroutine must not stop/unfocus a newer cue.
                    if (speechLease === lease) {
                        speechLease = null
                        finishSpeech = null
                        engine.stop()
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                }
            }
            synchronized(speechLock) {
                if (!continuation.isActive) return@synchronized
                if (closed || tts !== engine) {
                    finish(utteranceId, false)
                    return@synchronized
                }
                speechLease = lease
                finishSpeech = { finish(utteranceId, false) }
                try {
                    if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        VoiceCueAvailabilityRegistry.report(VoiceCueAvailability.AUDIO_FOCUS_UNAVAILABLE)
                        finish(utteranceId, false)
                        return@synchronized
                    }
                    VoiceCueAvailabilityRegistry.report(VoiceCueAvailability.AVAILABLE)
                    engine.setOnUtteranceProgressListener(listener)
                    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId) == TextToSpeech.ERROR)
                        finish(utteranceId, false)
                } catch (_: Exception) {
                    finish(utteranceId, false)
                }
            }
        }
    }

    override fun haptic(kind: WatchCueKind) {
        if (kind == WatchCueKind.WORKOUT_SUCCESS) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 90L, 70L, 140L), -1))
            return
        }
        val duration = when (kind) {
            WatchCueKind.GO -> 120L
            WatchCueKind.EXERCISE_SUCCESS -> 80L
            else -> 45L
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun cancel(reason: WatchCueCancellation) {
        synchronized(speechLock) {
            val finishCurrent = finishSpeech
            speechLease = null
            finishSpeech = null
            tts?.stop()
            audioManager.abandonAudioFocusRequest(focusRequest)
            // Do not depend on onStop reaching the old listener after replacement.
            finishCurrent?.invoke()
        }
    }

    override fun close() {
        closed = true
        cancel(WatchCueCancellation.NAVIGATION)
        if (audioCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioCallbackRegistered = false
        }
        synchronized(speechLock) {
            tts?.shutdown()
            tts = null
        }
        initialized = false
        languageSupported = false
    }

    private fun audioRouteChanged() {
        if (closed || !initialized || !languageSupported) return
        cancel(WatchCueCancellation.AUDIO_ROUTE)
        reportAvailability()
    }

    private fun reportAvailability() {
        VoiceCueAvailabilityRegistry.report(initializedVoiceCueAvailability(
            initialized = initialized,
            languageSupported = languageSupported,
            hasAudioOutput = hasAudioOutput(),
        ))
    }

    private fun hasAudioOutput(): Boolean = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any(AudioDeviceInfo::isSink)
}
