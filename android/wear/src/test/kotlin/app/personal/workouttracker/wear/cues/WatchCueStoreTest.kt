package app.personal.workouttracker.wear.cues

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCueStoreTest {
    @Test fun `receipt without prior cues still permits only one late terminal success`() = runTest {
        val store = WatchCueStore(YieldingCuePersistence())
        val event = WatchCueEvent("session", 2, 0, 1, WatchCueKind.WORKOUT_SUCCESS)
        store.clearAcknowledgedSession("session")
        assertTrue(store.reserve(event) is ReserveWatchCue.Reserved)
        store.clearAcknowledgedSession("session")
        assertEquals(ReserveWatchCue.Duplicate, store.reserve(event))
        assertTrue(store.state().ledger.deliveredKeys.isEmpty())
    }

    @Test fun `receipt before success prunes keys and admits terminal cue once after recreation`() = runTest {
        val persistence = YieldingCuePersistence()
        val store = WatchCueStore(persistence)
        val enabled = WatchCuePreferences(voiceEnabled = true, countdown = false)
        store.setPreferences(enabled)
        val go = WatchCueEvent("session", 1, 0, 1, WatchCueKind.GO)
        val success = go.copy(revision = 2, kind = WatchCueKind.WORKOUT_SUCCESS)
        store.reserve(go)
        store.clearAcknowledgedSession("session")
        val restored = WatchCueStore(persistence)
        assertEquals(ReserveWatchCue.Duplicate, restored.reserve(go))
        assertEquals(ReserveWatchCue.Reserved(enabled), restored.reserve(success))
        assertEquals(ReserveWatchCue.Duplicate, WatchCueStore(persistence).reserve(success))
        assertEquals(null, restored.state().ledgerSessionId)
        assertTrue(restored.state().ledger.deliveredKeys.isEmpty())
        assertEquals(enabled, restored.state().preferences)
    }

    @Test fun `receipt after success keeps duplicate protection and a newer ledger intact`() = runTest {
        val store = WatchCueStore(YieldingCuePersistence())
        val success = WatchCueEvent("session", 2, 0, 1, WatchCueKind.WORKOUT_SUCCESS)
        store.reserve(success)
        store.clearAcknowledgedSession("session")
        store.clearAcknowledgedSession("session")
        assertEquals(ReserveWatchCue.Duplicate, store.reserve(success))
        val next = success.copy(sessionId = "next-session", kind = WatchCueKind.GO)
        store.reserve(next)
        store.clearAcknowledgedSession("session")
        assertEquals("next-session", store.state().ledgerSessionId)
        assertEquals(ReserveWatchCue.Duplicate, store.reserve(next))
        assertEquals(ReserveWatchCue.Duplicate, store.reserve(success))
    }

    @Test fun `racing receipt and success retain one terminal reservation and no transient keys`() = runTest {
        val store = WatchCueStore(YieldingCuePersistence())
        val event = WatchCueEvent("session", 2, 0, 1, WatchCueKind.WORKOUT_SUCCESS)
        store.reserve(event.copy(revision = 1, kind = WatchCueKind.GO))
        awaitAll(
            async { assertTrue(store.reserve(event) is ReserveWatchCue.Reserved) },
            async { store.clearAcknowledgedSession("session") },
        )
        assertEquals(ReserveWatchCue.Duplicate, store.reserve(event))
        assertTrue(store.state().ledger.deliveredKeys.isEmpty())
    }

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
