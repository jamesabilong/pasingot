package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSessionPackageStoreTest {
    @Test
    fun `observed phone owner survives restart and another sender cannot claim duplicate`() = runTest {
        val persistence = InMemoryPersistence()
        val request = request()
        val first = WatchSessionPackageStore(persistence)
        assertTrue(first.accept(request, now, "phone-1") is AcceptQuickStartResult.Accepted)
        val restored = WatchSessionPackageStore(persistence)
        assertEquals("phone-1", restored.current(now)?.sourcePhoneNodeId)
        assertTrue(restored.accept(request, now + 1, "phone-2") is AcceptQuickStartResult.RejectedPending)
        assertTrue(restored.accept(request, now + 1, "phone-1") is AcceptQuickStartResult.Duplicate)
        assertTrue(restored.accept(request.copy(requestId = "123e4567-e89b-12d3-a456-426614174099"), now + 1, request.targetNodeId)
            is AcceptQuickStartResult.RejectedInvalid)
    }

    @Test
    fun `dismissed request cannot replay after a newer offer is accepted`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        val old = request()
        val next = request("123e4567-e89b-12d3-a456-426614174099")
        assertTrue(store.accept(old, now) is AcceptQuickStartResult.Accepted)
        assertTrue(store.dismiss(old.requestId, 2, now + 1) is TerminateQuickStartResult.Terminated)
        assertTrue(store.accept(next, now + 2) is AcceptQuickStartResult.Accepted)
        assertTrue(WatchSessionPackageStore(persistence).accept(old, now + 3) is
            AcceptQuickStartResult.PreviouslyTerminated)
    }

    @Test
    fun `terminal replay history refuses a new offer rather than evicting live protection`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        repeat(64) { index ->
            val item = request("123e4567-e89b-12d3-a456-${(index + 1).toString().padStart(12, '0')}")
            assertTrue(store.accept(item, now) is AcceptQuickStartResult.Accepted)
            assertTrue(store.dismiss(item.requestId, 2, now + 1) is TerminateQuickStartResult.Terminated)
        }
        val raw = persistence.raw
        assertEquals(AcceptQuickStartResult.ReplayHistoryFull,
            store.accept(request("123e4567-e89b-12d3-a456-426614174099"), now + 2))
        assertEquals(raw, persistence.raw)
        val oldest = request("123e4567-e89b-12d3-a456-000000000001")
        assertTrue(store.accept(oldest, now + 3) is AcceptQuickStartResult.PreviouslyTerminated)
    }
    private val now = 1_800_000_000_000L

    @Test
    fun `accept persists one ready package and survives store recreation`() = runTest {
        val persistence = InMemoryPersistence()
        val firstStore = WatchSessionPackageStore(persistence)

        val accepted = firstStore.accept(request(), now)

        assertTrue(accepted is AcceptQuickStartResult.Accepted)
        val recovered = WatchSessionPackageStore(persistence).current(now + 1_000)
        assertEquals(REQUEST_ID, recovered?.request?.requestId)
        assertEquals(QuickStartPackageState.READY, recovered?.state)
    }

    @Test
    fun `duplicate delivery is idempotent and does not rewrite package`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        val request = request()
        store.accept(request, now)
        val writesAfterAccept = persistence.writeCount

        val duplicate = store.accept(request, now + 1_000)

        assertTrue(duplicate is AcceptQuickStartResult.Duplicate)
        assertEquals(writesAfterAccept, persistence.writeCount)
    }

    @Test
    fun `different request is rejected while package is pending`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)

        val result = store.accept(
            request(requestId = "123e4567-e89b-12d3-a456-426614174001"),
            now + 1_000,
        )

        assertTrue(result is AcceptQuickStartResult.RejectedPending)
        assertEquals(REQUEST_ID, store.current(now + 1_000)?.request?.requestId)
    }

    @Test
    fun `expired ready package is removed and a new request can be accepted`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        store.accept(request(), now)

        assertNull(store.current(now + QUICK_START_TTL_MILLIS + 1))
        val replacement = store.accept(
            request(requestId = "123e4567-e89b-12d3-a456-426614174001").copy(
                createdAtMillis = now + QUICK_START_TTL_MILLIS + 1,
                expiresAtMillis = now + (2 * QUICK_START_TTL_MILLIS) + 1,
            ),
            now + QUICK_START_TTL_MILLIS + 1,
        )
        assertTrue(replacement is AcceptQuickStartResult.Accepted)
    }

    @Test
    fun `invalid request never replaces pending package`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)

        val invalid = store.accept(request().copy(exercises = emptyList()), now + 1_000)

        assertTrue(invalid is AcceptQuickStartResult.RejectedInvalid)
        assertEquals(
            QuickStartValidationCode.INVALID_EXERCISE_COUNT,
            (invalid as AcceptQuickStartResult.RejectedInvalid).issue.code,
        )
        assertEquals(REQUEST_ID, store.current(now + 1_000)?.request?.requestId)
    }

    @Test
    fun `mark starting persists before success and is idempotent after recreation`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        store.accept(request(), now)

        val marked = store.markStarting(REQUEST_ID, revision = 1, nowEpochMillis = now + 1_000)

        assertTrue(marked is MarkQuickStartStartingResult.MarkedStarting)
        assertEquals(
            QuickStartPackageState.STARTING,
            WatchSessionPackageStore(persistence).current(now + QUICK_START_TTL_MILLIS + 10_000)?.state,
        )
        val repeated = WatchSessionPackageStore(persistence).markStarting(
            REQUEST_ID,
            revision = 1,
            nowEpochMillis = now + QUICK_START_TTL_MILLIS + 10_000,
        )
        assertTrue(repeated is MarkQuickStartStartingResult.AlreadyStarting)
    }

    @Test
    fun `simultaneous start attempts produce one persisted transition`() = runTest {
        val persistence = InMemoryPersistence()
        val firstStore = WatchSessionPackageStore(persistence)
        val secondStore = WatchSessionPackageStore(persistence)
        firstStore.accept(request(), now)
        val writesAfterAccept = persistence.writeCount

        val results = listOf(
            async { firstStore.markStarting(REQUEST_ID, 1, now + 1_000) },
            async { secondStore.markStarting(REQUEST_ID, 1, now + 1_000) },
        ).awaitAll()

        assertEquals(1, results.count { it is MarkQuickStartStartingResult.MarkedStarting })
        assertEquals(1, results.count { it is MarkQuickStartStartingResult.AlreadyStarting })
        assertEquals(writesAfterAccept + 1, persistence.writeCount)
    }

    @Test
    fun `wrong identity cannot start pending package`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)

        assertSame(
            MarkQuickStartStartingResult.Missing,
            store.markStarting("123e4567-e89b-12d3-a456-426614174001", 1, now + 1_000),
        )
        assertSame(
            MarkQuickStartStartingResult.Missing,
            store.markStarting(REQUEST_ID, 2, now + 1_000),
        )
        assertEquals(QuickStartPackageState.READY, store.current(now + 1_000)?.state)
    }

    @Test
    fun `malformed persisted state is cleared safely`() = runTest {
        val persistence = InMemoryPersistence(raw = "not-json")

        assertNull(WatchSessionPackageStore(persistence).current(now))
        assertNull(persistence.raw)
    }

    @Test
    fun `dismiss removes ready package and retains idempotent terminal record`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        store.accept(request(), now)

        val dismissed = store.dismiss(REQUEST_ID, terminalRevision = 2, nowEpochMillis = now + 1_000)

        assertTrue(dismissed is TerminateQuickStartResult.Terminated)
        assertEquals(
            QuickStartStatus.DISMISSED,
            (dismissed as TerminateQuickStartResult.Terminated).terminal.status,
        )
        assertNull(store.current(now + 1_000))
        val duplicate = WatchSessionPackageStore(persistence).dismiss(REQUEST_ID, 2, now + 2_000)
        assertTrue(duplicate is TerminateQuickStartResult.AlreadyTerminal)
    }

    @Test
    fun `cancel terminal prevents replayed request from being accepted again`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        val request = request()
        store.accept(request, now)
        val cancelled = store.cancel(REQUEST_ID, terminalRevision = 2, nowEpochMillis = now + 1_000)

        assertEquals(
            QuickStartStatus.CANCELLED,
            (cancelled as TerminateQuickStartResult.Terminated).terminal.status,
        )
        val replay = WatchSessionPackageStore(persistence).accept(request, now + 2_000)
        assertTrue(replay is AcceptQuickStartResult.PreviouslyTerminated)
        assertEquals(
            QuickStartStatus.CANCELLED,
            (replay as AcceptQuickStartResult.PreviouslyTerminated).terminal.status,
        )
    }

    @Test
    fun `starting package refuses dismiss and cancel`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)
        store.markStarting(REQUEST_ID, 1, now + 1_000)

        assertTrue(store.dismiss(REQUEST_ID, 2, now + 2_000) is TerminateQuickStartResult.RefusedStarting)
        assertTrue(store.cancel(REQUEST_ID, 2, now + 2_000) is TerminateQuickStartResult.RefusedStarting)
        assertEquals(QuickStartPackageState.STARTING, store.current(now + 2_000)?.state)
    }

    @Test
    fun `wrong identity or revision cannot terminate ready package`() = runTest {
        val store = WatchSessionPackageStore(InMemoryPersistence())
        store.accept(request(), now)

        assertSame(
            TerminateQuickStartResult.Missing,
            store.cancel("123e4567-e89b-12d3-a456-426614174001", 2, now + 1_000),
        )
        val wrongRevision = store.cancel(REQUEST_ID, 1, now + 1_000)
        assertTrue(wrongRevision is TerminateQuickStartResult.RevisionMismatch)
        assertEquals(2, (wrongRevision as TerminateQuickStartResult.RevisionMismatch).expectedRevision)
        assertEquals(QuickStartPackageState.READY, store.current(now + 1_000)?.state)
    }

    @Test
    fun `simultaneous start and cancel have exactly one terminal outcome`() = runTest {
        val persistence = InMemoryPersistence()
        val firstStore = WatchSessionPackageStore(persistence)
        val secondStore = WatchSessionPackageStore(persistence)
        firstStore.accept(request(), now)

        val results = listOf(
            async { firstStore.markStarting(REQUEST_ID, 1, now + 1_000) },
            async { secondStore.cancel(REQUEST_ID, 2, now + 1_000) },
        ).awaitAll()

        val startWon = results[0] is MarkQuickStartStartingResult.MarkedStarting
        val cancelWon = results[1] is TerminateQuickStartResult.Terminated
        assertTrue(startWon.xor(cancelWon))
        if (startWon) assertTrue(results[1] is TerminateQuickStartResult.RefusedStarting)
        if (cancelWon) assertSame(MarkQuickStartStartingResult.Missing, results[0])
    }

    @Test
    fun `invalid persisted terminal status is cleared safely`() = runTest {
        val persistence = InMemoryPersistence()
        val store = WatchSessionPackageStore(persistence)
        store.accept(request(), now)
        store.cancel(REQUEST_ID, 2, now + 1_000)
        persistence.raw = persistence.raw?.replace("\"cancelled\"", "\"started\"")

        assertNull(WatchSessionPackageStore(persistence).current(now + 2_000))
        assertNull(persistence.raw)
    }

    private fun request(requestId: String = REQUEST_ID) = QuickStartRequest(
        requestId = requestId,
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
                prescription = "8-12 reps",
                restSeconds = 60,
            ),
        ),
    )

    private class InMemoryPersistence(
        var raw: String? = null,
    ) : QuickStartPackagePersistence {
        var writeCount = 0

        override suspend fun read(): String? = raw

        override suspend fun write(raw: String?) {
            this.raw = raw
            writeCount += 1
        }
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
