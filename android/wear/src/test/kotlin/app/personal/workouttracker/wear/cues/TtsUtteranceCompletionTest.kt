package app.personal.workouttracker.wear.cues

import org.junit.Assert.*
import org.junit.Test

class TtsUtteranceCompletionTest {
    @Test fun `old success stop cannot complete replacement warning`() {
        val warning = TtsUtteranceCompletion("five-seconds")
        assertFalse(warning.accept("exercise-success"))
        assertTrue(warning.accept("five-seconds"))
        assertFalse(warning.accept("five-seconds"))
    }

    @Test fun `late done error and missing IDs leave Go pending`() {
        val go = TtsUtteranceCompletion("go")
        for (id in listOf("rest", "five-seconds", null)) assertFalse(go.accept(id))
        assertTrue(go.accept("go"))
    }

    @Test fun `cancellation consumes only its own completion`() {
        val old = TtsUtteranceCompletion("rest")
        val replacement = TtsUtteranceCompletion("go")
        assertTrue(old.accept("rest"))
        assertFalse(old.accept("rest"))
        assertFalse(replacement.accept("rest"))
        assertTrue(replacement.accept("go"))
    }
}
