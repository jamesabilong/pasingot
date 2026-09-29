package app.personal.workouttracker.wear.cues

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceCueAvailability {
    CHECKING,
    ENGINE_AVAILABLE,
    AVAILABLE,
    SERVICE_UNAVAILABLE,
    INITIALIZATION_FAILED,
    LANGUAGE_UNAVAILABLE,
    AUDIO_OUTPUT_UNAVAILABLE,
    AUDIO_FOCUS_UNAVAILABLE,
}

fun interface VoiceCueAvailabilityProbe {
    fun check(): VoiceCueAvailability
}

/** Lightweight service discovery; session output still validates init, locale, and audio focus. */
class AndroidVoiceCueAvailabilityProbe(context: Context) : VoiceCueAvailabilityProbe {
    private val packageManager = context.applicationContext.packageManager

    override fun check(): VoiceCueAvailability {
        val services = packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        )
        return if (services.isEmpty()) {
            VoiceCueAvailability.SERVICE_UNAVAILABLE
        } else {
            VoiceCueAvailability.ENGINE_AVAILABLE
        }
    }
}

/** Process-visible status from discovery and the latest foreground TTS owner. */
object VoiceCueAvailabilityRegistry {
    private val mutableAvailability = MutableStateFlow(VoiceCueAvailability.CHECKING)
    val availability: StateFlow<VoiceCueAvailability> = mutableAvailability.asStateFlow()

    fun report(availability: VoiceCueAvailability) {
        mutableAvailability.value = availability
    }

    fun reportDiscovery(availability: VoiceCueAvailability) {
        val current = mutableAvailability.value
        if (availability == VoiceCueAvailability.SERVICE_UNAVAILABLE ||
            current == VoiceCueAvailability.CHECKING ||
            current == VoiceCueAvailability.SERVICE_UNAVAILABLE
        ) {
            mutableAvailability.value = availability
        }
    }
}

internal fun initializedVoiceCueAvailability(
    initialized: Boolean,
    languageSupported: Boolean,
    hasAudioOutput: Boolean,
): VoiceCueAvailability = when {
    !initialized -> VoiceCueAvailability.INITIALIZATION_FAILED
    !languageSupported -> VoiceCueAvailability.LANGUAGE_UNAVAILABLE
    !hasAudioOutput -> VoiceCueAvailability.AUDIO_OUTPUT_UNAVAILABLE
    else -> VoiceCueAvailability.AVAILABLE
}
