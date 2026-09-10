package app.personal.workouttracker.wear.data

import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.LogStatus
import app.personal.workouttracker.shared.SessionEventType
import app.personal.workouttracker.shared.SessionStopReason
import app.personal.workouttracker.shared.WorkoutSessionEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedHistoryTest {
    private val event = WorkoutSessionEvent(
        workoutEntryId = "2026-09-10", workoutDate = "2026-09-10",
        eventType = SessionEventType.ENDED, stopReason = SessionStopReason.ENDED_BY_USER,
        timestamp = "2026-09-10T01:00:00Z", elapsedSeconds = 10,
        exerciseIndex = 0, currentSet = 1, totalExercises = 2,
    )

    @Test fun `session ended before any completed set still drains`() = runTest {
        val sent = mutableListOf<WorkoutSessionEvent>()
        var removedEvents = 0
        val drained = flushQueuedHistory(
            emptyList(), listOf(event),
            sendLog = { error("No exercise logs are queued") },
            sendSessionEvent = { sent.add(it); true },
            removeLogs = { assertEquals(0, it) },
            removeSessionEvents = { removedEvents = it },
        )
        assertTrue(drained)
        assertEquals(listOf(event), sent)
        assertEquals(1, removedEvents)
    }

    @Test fun `partial log failure preserves unsent suffix while session queue drains`() = runTest {
        val logs = (1..3).map { LogEntry(exercise = "Exercise $it", status = LogStatus.DONE, timestamp = "time-$it") }
        val attempted = mutableListOf<LogEntry>()
        var removedLogs = 0
        var removedEvents = 0
        val drained = flushQueuedHistory(
            logs, listOf(event),
            sendLog = { attempted.add(it); it == logs.first() },
            sendSessionEvent = { true },
            removeLogs = { removedLogs = it },
            removeSessionEvents = { removedEvents = it },
        )
        assertFalse(drained)
        assertEquals(logs.take(2), attempted)
        assertEquals(1, removedLogs)
        assertEquals(1, removedEvents)
    }

    @Test fun `failed session remains queued and requests another worker retry`() = runTest {
        var removedEvents = -1
        val drained = flushQueuedHistory(
            emptyList(), listOf(event),
            sendLog = { error("No exercise logs are queued") },
            sendSessionEvent = { false },
            removeLogs = {},
            removeSessionEvents = { removedEvents = it },
        )
        assertFalse(drained)
        assertEquals(0, removedEvents)
    }
}
