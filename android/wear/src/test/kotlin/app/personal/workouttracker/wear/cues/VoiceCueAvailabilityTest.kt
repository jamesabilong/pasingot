package app.personal.workouttracker.wear.cues

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCueAvailabilityTest {
    @Test fun `initialization failure takes precedence over downstream checks`() {
        assertEquals(
            VoiceCueAvailability.INITIALIZATION_FAILED,
            initializedVoiceCueAvailability(false, false, false),
        )
    }

    @Test fun `unsupported locale is distinct from a missing audio route`() {
        assertEquals(
            VoiceCueAvailability.LANGUAGE_UNAVAILABLE,
            initializedVoiceCueAvailability(true, false, false),
        )
        assertEquals(
            VoiceCueAvailability.AUDIO_OUTPUT_UNAVAILABLE,
            initializedVoiceCueAvailability(true, true, false),
        )
    }

    @Test fun `initialized language and audio route are available`() {
        assertEquals(
            VoiceCueAvailability.AVAILABLE,
            initializedVoiceCueAvailability(true, true, true),
        )
    }
}
