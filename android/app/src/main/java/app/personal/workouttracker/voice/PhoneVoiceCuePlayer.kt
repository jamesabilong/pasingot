package app.personal.workouttracker.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The foreground service owns this engine. Focus is held only for a short utterance. */
class PhoneVoiceCuePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) cancel()
        }.build()
    private val routes = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) { cancel() }
    }
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    private var complete: ((Boolean) -> Unit)? = null
    private var currentId: String? = null
    private var focusHeld = false

    init {
        engine = TextToSpeech(context.applicationContext) { status -> handler.post {
            if (!closed) {
                val tts = engine
                initialized = status == TextToSpeech.SUCCESS && tts != null &&
                    tts.setLanguage(Locale.getDefault()) !in setOf(TextToSpeech.LANG_MISSING_DATA,
                        TextToSpeech.LANG_NOT_SUPPORTED)
                if (initialized) tts?.setAudioAttributes(attributes)
            }
        } }
        audio.registerAudioDeviceCallback(routes, handler)
    }

    fun available(): Boolean = !closed && initialized && hasHeadphones() &&
        !(accessibility?.isEnabled == true && accessibility.isTouchExplorationEnabled)

    fun hasHeadphones(): Boolean = hasPhoneHeadphones(audio)

    companion object {
        fun hasPhoneHeadphones(audio: AudioManager): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET)
        }
    }

    suspend fun speak(id: String, text: String): Boolean {
        if (!available()) return false
        cancel()
        return suspendCancellableCoroutine { continuation ->
            val tts = engine ?: run { continuation.resume(false); return@suspendCancellableCoroutine }
            currentId = id
            var finished = false
            val finish: (Boolean) -> Unit = { success ->
                if (!finished) {
                    finished = true
                    if (currentId == id) {
                        currentId = null
                        complete = null
                        releaseFocus()
                    }
                    if (continuation.isActive) continuation.resume(success)
                }
            }
            complete = finish
            continuation.invokeOnCancellation { handler.post { if (currentId == id) cancel() } }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { handler.post { if (utteranceId == id) finish(true) } }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    handler.post { if (utteranceId == id) finish(false) }
                }
                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    handler.post { if (utteranceId == id) finish(false) }
                }
                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
            })
            try {
                focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!focusHeld || tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.ERROR)
                    finish(false)
            } catch (_: Exception) { finish(false) }
        }
    }

    fun cancel() {
        val finish = complete
        engine?.stop()
        currentId = null
        complete = null
        releaseFocus()
        finish?.invoke(false)
    }

    private fun releaseFocus() {
        if (!focusHeld) return
        focusHeld = false
        audio.abandonAudioFocusRequest(focus)
    }

    fun close() {
        closed = true
        cancel()
        audio.unregisterAudioDeviceCallback(routes)
        engine?.shutdown()
        engine = null
    }
}
