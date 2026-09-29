package app.personal.workouttracker.wear.cues

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchCueStoreTest {
    @Test fun `settings and session stores serialize preference and ledger writes`() = runTest {
        val persistence = YieldingCuePersistence()
        val settings = WatchCueStore(persistence)
        val session = WatchCueStore(persistence)
        val enabled = WatchCuePreferences(
            voiceEnabled = true,
            voicePromptResolved = true,
            restAnnouncements = false,
        )
        val event = WatchCueEvent("session", 1, 0, 1, WatchCueKind.GO)

        awaitAll(
            async { settings.setPreferences(enabled) },
            async { session.reserve(event) },
        )

        val restored = WatchCueStore(persistence)
        assertEquals(enabled, restored.state().preferences)
        assertEquals(ReserveWatchCue.Duplicate, restored.reserve(event))
    }
}

private class YieldingCuePersistence : WatchCuePersistence {
    private var raw: String? = null
    override suspend fun read(): String? {
        val snapshot = raw
        yield()
        return snapshot
    }
    override suspend fun write(value: String) {
        yield()
        raw = value
    }
}
