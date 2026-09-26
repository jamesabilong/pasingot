package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.SessionStatus
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface LegacySessionSnapshotSource {
    suspend fun entries(): List<DownloadedWorkoutEntry>
}

data class BlockingLegacySession(
    val entryId: String,
    val status: String,
)

sealed interface QuickStartGateResult {
    data class Started(val sessionPackage: WatchSessionPackage) : QuickStartGateResult
    data class AlreadyStarting(val sessionPackage: WatchSessionPackage) : QuickStartGateResult
    data class BlockedByLegacy(val sessions: List<BlockingLegacySession>) : QuickStartGateResult
    data object Missing : QuickStartGateResult
    data object Expired : QuickStartGateResult
}

sealed interface LegacySessionGateResult {
    data object Started : LegacySessionGateResult
    data class BlockedByQuickStart(val sessionPackage: WatchSessionPackage) : LegacySessionGateResult
    data class BlockedByLegacy(val sessions: List<BlockingLegacySession>) : LegacySessionGateResult
}

/**
 * Process-wide serialization point for every session start. Existing session
 * entry points must call [startLegacy] before persisting an active legacy
 * session; Quick Start calls [startQuickStart].
 */
class GlobalSessionStartGate(
    private val legacySessions: LegacySessionSnapshotSource,
    private val quickStartPackages: QuickStartPackageStore,
) {
    private companion object {
        val processStartMutex = Mutex()
    }

    suspend fun startQuickStart(
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): QuickStartGateResult = processStartMutex.withLock {
        val blocking = legacySessions.entries().blockingSessions()
        if (blocking.isNotEmpty()) return@withLock QuickStartGateResult.BlockedByLegacy(blocking)

        when (
            val result = quickStartPackages.markStarting(
                requestId = requestId,
                revision = revision,
                nowEpochMillis = nowEpochMillis,
            )
        ) {
            is MarkQuickStartStartingResult.MarkedStarting -> QuickStartGateResult.Started(result.sessionPackage)
            is MarkQuickStartStartingResult.AlreadyStarting ->
                QuickStartGateResult.AlreadyStarting(result.sessionPackage)
            MarkQuickStartStartingResult.Missing -> QuickStartGateResult.Missing
            MarkQuickStartStartingResult.Expired -> QuickStartGateResult.Expired
        }
    }

    suspend fun startLegacy(
        entryId: String,
        nowEpochMillis: Long,
        persistStart: suspend () -> Unit,
    ): LegacySessionGateResult = processStartMutex.withLock {
        val quickStart = quickStartPackages.current(nowEpochMillis)
        if (quickStart != null) {
            return@withLock LegacySessionGateResult.BlockedByQuickStart(quickStart)
        }

        val blocking = legacySessions.entries().blockingSessions(excludingEntryId = entryId)
        if (blocking.isNotEmpty()) return@withLock LegacySessionGateResult.BlockedByLegacy(blocking)

        persistStart()
        LegacySessionGateResult.Started
    }
}

fun List<DownloadedWorkoutEntry>.blockingSessions(
    excludingEntryId: String? = null,
): List<BlockingLegacySession> = mapNotNull { entry ->
    if (entry.id == excludingEntryId) return@mapNotNull null
    val status = entry.sessionState?.status ?: return@mapNotNull null
    if (status in NON_BLOCKING_LEGACY_STATUSES) return@mapNotNull null
    BlockingLegacySession(entryId = entry.id, status = status)
}

private val NON_BLOCKING_LEGACY_STATUSES = setOf(
    SessionStatus.COMPLETED,
    SessionStatus.ENDED,
)
