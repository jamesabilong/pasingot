package app.personal.workouttracker.wear.cues

/** Called under the native output's speech lock. Old/duplicate callbacks have no effects. */
internal class TtsUtteranceCompletion(private val utteranceId: String) {
    private var completed = false

    fun accept(callbackId: String?): Boolean {
        if (completed || callbackId != utteranceId) return false
        completed = true
        return true
    }
}
