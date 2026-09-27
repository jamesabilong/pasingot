package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionState
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSessionStartGateTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `active resting and paused legacy sessions block Quick Start`() = runTest {
        for (status in listOf(SessionStatus.ACTIVE, SessionStatus.RESTING, SessionStatus.PAUSED, "unknown")) {
            val fixture = fixture(legacyEntries = listOf(entry("legacy", status)))

            val result = fixture.gate.startQuickStart(REQUEST_ID, 1, now + 1_000)

            assertTrue(result is QuickStartGateResult.BlockedByLegacy)
            assertEquals(status, (result as QuickStartGateResult.BlockedByLegacy).sessions.single().status)
            assertEquals(QuickStartPackageState.READY, fixture.store.current(now + 1_000)?.state)
        }
    }

    @Test
    fun `not started completed and ended legacy entries do not block Quick Start`() = runTest {
        val fixture = fixture(
            legacyEntries = listOf(
                entry("not-started", null),
                entry("completed", SessionStatus.COMPLETED),
                entry("ended", SessionStatus.ENDED),
            ),
        )

        val result = fixture.gate.startQuickStart(REQUEST_ID, 1, now + 1_000)

        assertTrue(result is QuickStartGateResult.Started)
        assertEquals(QuickStartPackageState.STARTING, fixture.store.current(now + 1_000)?.state)
    }

    @Test
    fun `ready Quick Start blocks a legacy start before callback runs`() = runTest {
        val fixture = fixture()
        var persisted = false

        val result = fixture.gate.startLegacy("legacy", now + 1_000) { persisted = true }

        assertTrue(result is LegacySessionGateResult.BlockedByQuickStart)
        assertEquals(false, persisted)
    }

    @Test
    fun `starting Quick Start continues to block a legacy start`() = runTest {
        val fixture = fixture()
        fixture.gate.startQuickStart(REQUEST_ID, 1, now + 1_000)
        var persisted = false

        val result = fixture.gate.startLegacy("legacy", now + 2_000) { persisted = true }

        assertTrue(result is LegacySessionGateResult.BlockedByQuickStart)
        assertEquals(false, persisted)
    }

    @Test
    fun `expired ready Quick Start is pruned before legacy start`() = runTest {
        val fixture = fixture()
        var persisted = false

        val result = fixture.gate.startLegacy(
            entryId = "legacy",
            nowEpochMillis = now + QUICK_START_TTL_MILLIS + 1,
        ) { persisted = true }

        assertSame(LegacySessionGateResult.Started, result)
        assertTrue(persisted)
        assertEquals(null, fixture.store.current(now + QUICK_START_TTL_MILLIS + 1))
    }

    @Test
    fun `legacy start excludes its own resumable entry but not another active entry`() = runTest {
        val source = MutableLegacySessions(listOf(entry("same", SessionStatus.PAUSED)))
        val store = WatchSessionPackageStore(InMemoryPersistence())
        val gate = GlobalSessionStartGate(source, store)
        var persisted = false

        assertSame(
            LegacySessionGateResult.Started,
            gate.startLegacy("same", now) { persisted = true },
        )
        assertTrue(persisted)

        source.value = source.value + entry("other", SessionStatus.ACTIVE)
        val blocked = gate.startLegacy("same", now) { error("Must not persist") }
        assertTrue(blocked is LegacySessionGateResult.BlockedByLegacy)
        assertEquals("other", (blocked as LegacySessionGateResult.BlockedByLegacy).sessions.single().entryId)
    }

    @Test
    fun `concurrent Quick Start and legacy start allow exactly one winner`() = runTest {
        val source = MutableLegacySessions(emptyList())
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)
        val gateA = GlobalSessionStartGate(source, store)
        val gateB = GlobalSessionStartGate(source, store)

        val results = listOf(
            async { gateA.startQuickStart(REQUEST_ID, 1, now + 1_000) },
            async {
                gateB.startLegacy("legacy", now + 1_000) {
                    source.value = listOf(entry("legacy", SessionStatus.ACTIVE))
                }
            },
        ).awaitAll()

        val quickWon = results[0] is QuickStartGateResult.Started
        val legacyWon = results[1] === LegacySessionGateResult.Started
        assertTrue(quickWon.xor(legacyWon))
        if (quickWon) assertTrue(results[1] is LegacySessionGateResult.BlockedByQuickStart)
        if (legacyWon) assertTrue(results[0] is QuickStartGateResult.BlockedByLegacy)
    }

    @Test
    fun `concurrent offer and legacy start admit exactly one owner`() = runTest {
        val source = MutableLegacySessions(emptyList())
        val store = WatchSessionPackageStore(InMemoryPersistence())
        val offerGate = GlobalSessionStartGate(source, store)
        val legacyGate = GlobalSessionStartGate(source, store)

        val results = listOf(
            async { offerGate.acceptQuickStart(request(), "phone-node", now) },
            async {
                legacyGate.startLegacy("legacy", now) {
                    source.value = listOf(entry("legacy", SessionStatus.ACTIVE))
                }
            },
        ).awaitAll()

        val offerWon = (results[0] as? QuickStartOfferGateResult.Processed)?.result is
            AcceptQuickStartResult.Accepted
        val legacyWon = results[1] == LegacySessionGateResult.Started
        assertTrue(offerWon != legacyWon)
        if (offerWon) assertTrue(results[1] is LegacySessionGateResult.BlockedByQuickStart)
        if (legacyWon) assertTrue(results[0] is QuickStartOfferGateResult.BlockedByLegacy)
    }

    @Test
    fun `invalid offer stays invalid even with a blocking legacy session`() = runTest {
        val source = MutableLegacySessions(listOf(entry("legacy", SessionStatus.ACTIVE)))
        val gate = GlobalSessionStartGate(source, WatchSessionPackageStore(InMemoryPersistence()))

        val result = gate.acceptQuickStart(request().copy(schemaVersion = 99), "phone-node", now)

        assertTrue((result as QuickStartOfferGateResult.Processed).result is
            AcceptQuickStartResult.RejectedInvalid)
    }

    @Test
    fun `legacy blocker suppresses ready replay from older inconsistent state`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now, "phone-node")
        val source = MutableLegacySessions(listOf(entry("legacy", SessionStatus.ACTIVE)))

        val result = GlobalSessionStartGate(source, store)
            .acceptQuickStart(request(), "phone-node", now + 1)

        assertTrue(result is QuickStartOfferGateResult.BlockedByLegacy)
    }

    @Test
    fun `terminal replay wins over later legacy blocker and expired offer`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now, "phone-node")
        val dismissed = store.dismiss(REQUEST_ID, 2, now + 1) as TerminateQuickStartResult.Terminated
        val source = MutableLegacySessions(listOf(entry("legacy", SessionStatus.ACTIVE)))

        val result = GlobalSessionStartGate(source, store)
            .acceptQuickStart(request(), "phone-node", now + 900_000) as QuickStartOfferGateResult.Processed

        assertEquals(dismissed.terminal, (result.result as AcceptQuickStartResult.PreviouslyTerminated).terminal)
    }

    @Test
    fun `wrong Quick Start identity does not alter ready package`() = runTest {
        val fixture = fixture()

        val result = fixture.gate.startQuickStart(
            requestId = "123e4567-e89b-12d3-a456-426614174001",
            revision = 1,
            nowEpochMillis = now + 1_000,
        )

        assertSame(QuickStartGateResult.Missing, result)
        assertEquals(QuickStartPackageState.READY, fixture.store.current(now + 1_000)?.state)
    }

    private suspend fun fixture(
        legacyEntries: List<DownloadedWorkoutEntry> = emptyList(),
    ): Fixture {
        val source = MutableLegacySessions(legacyEntries)
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)
        return Fixture(store, GlobalSessionStartGate(source, store))
    }

    private fun entry(id: String, status: String?) = DownloadedWorkoutEntry(
        id = id,
        date = "2026-09-25",
        label = id,
        exercises = listOf(WorkoutExercise("Squat", "10", sets = 3, rest = 60)),
        sessionState = status?.let {
            SessionState(
                workoutEntryId = id,
                exerciseIndex = 0,
                currentSet = 1,
                status = it,
            )
        },
    )

    private fun request() = QuickStartRequest(
        requestId = REQUEST_ID,
        createdAtMillis = now,
        expiresAtMillis = now + QUICK_START_TTL_MILLIS,
        targetNodeId = "watch-node",
        source = QuickStartSource.SINGLE,
        exercises = listOf(
            QuickStartExercise(
                itemId = "item-1",
                exerciseId = "squat",
                exerciseName = "Squat",
                sets = 3,
                prescription = "10 reps",
                restSeconds = 60,
            ),
        ),
    )

    private data class Fixture(
        val store: WatchSessionPackageStore,
        val gate: GlobalSessionStartGate,
    )

    private class MutableLegacySessions(
        var value: List<DownloadedWorkoutEntry>,
    ) : LegacySessionSnapshotSource {
        override suspend fun entries(): List<DownloadedWorkoutEntry> = value
    }

    private class InMemoryPersistence : QuickStartPackagePersistence {
        private var raw: String? = null
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String?) {
            this.raw = raw
        }
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
