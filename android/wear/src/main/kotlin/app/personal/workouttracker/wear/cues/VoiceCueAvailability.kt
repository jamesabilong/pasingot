package app.personal.workouttracker.wear.cues

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech

enum class VoiceCueAvailability { AVAILABLE, UNAVAILABLE }

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
        return if (services.isEmpty()) VoiceCueAvailability.UNAVAILABLE else VoiceCueAvailability.AVAILABLE
    }
}
