package app.personal.workouttracker.wear.cues

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
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
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                tts?.stop()
            }
        }
        .build()

    @Volatile private var initialized = false
    @Volatile private var closed = false
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)

    override val talkBackEnabled: Boolean
        get() = accessibilityManager?.isEnabled == true && accessibilityManager.isTouchExplorationEnabled

    override fun onInit(status: Int) {
        val engine = tts ?: return
        initialized = status == TextToSpeech.SUCCESS &&
            engine.setLanguage(Locale.getDefault()) !in setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
        if (initialized) engine.setAudioAttributes(audioAttributes)
    }

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        val engine = tts ?: return false
        if (closed || !initialized || audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return false
        }
        return suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = finish(id == utteranceId)
                override fun onStop(id: String?, interrupted: Boolean) = finish(false)
                @Deprecated("Deprecated in Android")
                override fun onError(id: String?) = finish(false)
                override fun onError(id: String?, errorCode: Int) = finish(false)

                private fun finish(success: Boolean) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    if (continuation.isActive) continuation.resume(success)
                }
            })
            continuation.invokeOnCancellation {
                engine.stop()
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                continuation.resume(false)
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
        tts?.stop()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun close() {
        closed = true
        cancel(WatchCueCancellation.NAVIGATION)
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
