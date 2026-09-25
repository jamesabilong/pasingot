package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartExercise
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartSource
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
