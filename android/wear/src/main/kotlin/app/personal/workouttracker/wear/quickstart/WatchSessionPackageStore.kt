package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QUICK_START_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QUICK_START_CLOCK_SKEW_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationIssue
import app.personal.workouttracker.shared.quickstart.QuickStartValidationResult
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.shared.quickstart.validateQuickStartRequest
import app.personal.workouttracker.shared.quickstart.validateQuickStartAcknowledgement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface QuickStartPackagePersistence {
    suspend fun read(): String?
    suspend fun write(raw: String?)
}

interface QuickStartPackageStore {
    suspend fun current(nowEpochMillis: Long): WatchSessionPackage?
    suspend fun accept(request: QuickStartRequest, receivedAtMillis: Long): AcceptQuickStartResult
    suspend fun markStarting(
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): MarkQuickStartStartingResult
    suspend fun dismiss(
        requestId: String,
        terminalRevision: Long,
        nowEpochMillis: Long,
    ): TerminateQuickStartResult
    suspend fun cancel(
        requestId: String,
        terminalRevision: Long,
        nowEpochMillis: Long,
    ): TerminateQuickStartResult
    /** Called only after the result store validates/persists the receipt and finishes cleanup. */
    suspend fun releaseAcknowledged(receipt: QuickStartResultReceipt): ReleaseAcknowledgedQuickStartResult
}

sealed interface AcceptQuickStartResult {
    data class Accepted(val sessionPackage: WatchSessionPackage) : AcceptQuickStartResult
    data class Duplicate(val sessionPackage: WatchSessionPackage) : AcceptQuickStartResult
    data class RejectedPending(val existing: WatchSessionPackage) : AcceptQuickStartResult
    data class RejectedInvalid(val issue: QuickStartValidationIssue) : AcceptQuickStartResult
    data class PreviouslyTerminated(val terminal: TerminalQuickStartRecord) : AcceptQuickStartResult
    data class PreviouslyAcknowledged(val record: AcknowledgedQuickStartRecord) : AcceptQuickStartResult
    data object ReplayHistoryFull : AcceptQuickStartResult
}

sealed interface MarkQuickStartStartingResult {
    data class MarkedStarting(val sessionPackage: WatchSessionPackage) : MarkQuickStartStartingResult
    data class AlreadyStarting(val sessionPackage: WatchSessionPackage) : MarkQuickStartStartingResult
    data object Missing : MarkQuickStartStartingResult
    data object Expired : MarkQuickStartStartingResult
}

@Serializable
data class TerminalQuickStartRecord(
    val requestId: String,
    val revision: Long,
    val targetNodeId: String,
    val status: QuickStartStatus,
    val recordedAtMillis: Long,
)

@Serializable
data class AcknowledgedQuickStartRecord(
    val requestId: String,
    val resultId: String,
    val outcomeRevision: Long,
    val recordedAtMillis: Long,
    val replayUntilMillis: Long,
)

sealed interface ReleaseAcknowledgedQuickStartResult {
    data class Released(val record: AcknowledgedQuickStartRecord) : ReleaseAcknowledgedQuickStartResult
    data class AlreadyReleased(val record: AcknowledgedQuickStartRecord) : ReleaseAcknowledgedQuickStartResult
    data object Missing : ReleaseAcknowledgedQuickStartResult
    data object Mismatch : ReleaseAcknowledgedQuickStartResult
    data object NotStarting : ReleaseAcknowledgedQuickStartResult
}

sealed interface TerminateQuickStartResult {
    data class Terminated(val terminal: TerminalQuickStartRecord) : TerminateQuickStartResult
    data class AlreadyTerminal(val terminal: TerminalQuickStartRecord) : TerminateQuickStartResult
    data class RefusedStarting(val sessionPackage: WatchSessionPackage) : TerminateQuickStartResult
    data class RevisionMismatch(val expectedRevision: Long) : TerminateQuickStartResult
    data object Missing : TerminateQuickStartResult
}

@Serializable
private data class PersistedQuickStartPackage(
    val schemaVersion: Int = QUICK_START_SCHEMA_VERSION,
    val sessionPackage: WatchSessionPackage? = null,
    val terminal: TerminalQuickStartRecord? = null,
    val acknowledged: AcknowledgedQuickStartRecord? = null,
    val acknowledgedHistory: List<AcknowledgedQuickStartRecord> = emptyList(),
)

/**
 * Owns the watch's single transient Quick Start package. All read-modify-write
 * operations are serialized and persisted before success is returned.
 */
class WatchSessionPackageStore(
    private val persistence: QuickStartPackagePersistence,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : QuickStartPackageStore {
    private companion object {
        /** Services and activities may construct separate store instances. */
        val processMutex = Mutex()
        const val MAX_ACKNOWLEDGED_HISTORY = 64
    }

    override suspend fun current(nowEpochMillis: Long): WatchSessionPackage? = processMutex.withLock {
        loadCurrent(nowEpochMillis)
    }

    override suspend fun accept(
        request: QuickStartRequest,
        receivedAtMillis: Long,
    ): AcceptQuickStartResult = processMutex.withLock {
        val validated = validateQuickStartRequest(request, receivedAtMillis)
        if (validated is QuickStartValidationResult.Invalid) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(validated.issue)
        }
        val incoming = (validated as QuickStartValidationResult.Valid).sessionPackage
        val state = loadState()
        val recentHistory = state?.acknowledgedHistory.orEmpty().filter {
            receivedAtMillis <= it.replayUntilMillis
        }
        (recentHistory + listOfNotNull(state?.acknowledged).filter {
            receivedAtMillis <= it.replayUntilMillis
        })
            .firstOrNull { it.requestId == incoming.request.requestId }
            ?.let { return@withLock AcceptQuickStartResult.PreviouslyAcknowledged(it) }
        val existing = state?.sessionPackage?.takeUnless { sessionPackage ->
            sessionPackage.state == QuickStartPackageState.READY &&
                receivedAtMillis > sessionPackage.expiresLocallyAtMillis
        }
        if (state?.sessionPackage != null && existing == null) {
            val cleared = state.copy(sessionPackage = null, acknowledgedHistory = recentHistory)
            persistOrClear(cleared)
        }
        if (existing != null) {
            return@withLock if (existing.request == incoming.request) {
                AcceptQuickStartResult.Duplicate(existing)
            } else {
                AcceptQuickStartResult.RejectedPending(existing)
            }
        }
        val terminal = state?.terminal
        if (terminal != null && terminal.requestId == incoming.request.requestId) {
            return@withLock AcceptQuickStartResult.PreviouslyTerminated(terminal)
        }
        val keptHistory = recentHistory + listOfNotNull(state?.acknowledged).filter {
            receivedAtMillis <= it.replayUntilMillis
        }
        if (keptHistory.size >= MAX_ACKNOWLEDGED_HISTORY) {
            return@withLock AcceptQuickStartResult.ReplayHistoryFull
        }

        persistPackage(incoming, keptHistory)
        AcceptQuickStartResult.Accepted(incoming)
    }

    override suspend fun markStarting(
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): MarkQuickStartStartingResult = processMutex.withLock {
        val state = loadState() ?: return@withLock MarkQuickStartStartingResult.Missing
        val current = state.sessionPackage ?: return@withLock MarkQuickStartStartingResult.Missing
        if (current.request.requestId != requestId || current.request.revision != revision) {
            return@withLock MarkQuickStartStartingResult.Missing
        }
        if (
            current.state == QuickStartPackageState.READY &&
            nowEpochMillis > current.expiresLocallyAtMillis
        ) {
            persistOrClear(state.copy(sessionPackage = null))
            return@withLock MarkQuickStartStartingResult.Expired
        }
        if (current.state == QuickStartPackageState.STARTING) {
            return@withLock MarkQuickStartStartingResult.AlreadyStarting(current)
        }

        val starting = current.copy(state = QuickStartPackageState.STARTING)
        persistPackage(starting, state.acknowledgedHistory)
        MarkQuickStartStartingResult.MarkedStarting(starting)
    }

    override suspend fun dismiss(
        requestId: String,
        terminalRevision: Long,
        nowEpochMillis: Long,
    ): TerminateQuickStartResult = terminate(
        requestId = requestId,
        terminalRevision = terminalRevision,
        nowEpochMillis = nowEpochMillis,
        status = QuickStartStatus.DISMISSED,
    )

    override suspend fun cancel(
        requestId: String,
        terminalRevision: Long,
        nowEpochMillis: Long,
    ): TerminateQuickStartResult = terminate(
        requestId = requestId,
        terminalRevision = terminalRevision,
        nowEpochMillis = nowEpochMillis,
        status = QuickStartStatus.CANCELLED,
    )

    override suspend fun releaseAcknowledged(
        receipt: QuickStartResultReceipt,
    ): ReleaseAcknowledgedQuickStartResult = processMutex.withLock {
        val state = loadState() ?: return@withLock ReleaseAcknowledgedQuickStartResult.Missing
        (listOfNotNull(state.acknowledged) + state.acknowledgedHistory)
            .firstOrNull { it.requestId == receipt.requestId }?.let { existing ->
            return@withLock if (
                existing.resultId == receipt.resultId && existing.outcomeRevision == receipt.outcomeRevision
            ) ReleaseAcknowledgedQuickStartResult.AlreadyReleased(existing)
            else ReleaseAcknowledgedQuickStartResult.Mismatch
        }
        val current = state.sessionPackage ?: return@withLock ReleaseAcknowledgedQuickStartResult.Missing
        if (current.request.requestId != receipt.requestId) {
            return@withLock ReleaseAcknowledgedQuickStartResult.Mismatch
        }
        if (current.state != QuickStartPackageState.STARTING) {
            return@withLock ReleaseAcknowledgedQuickStartResult.NotStarting
        }
        val acknowledged = AcknowledgedQuickStartRecord(
            requestId = receipt.requestId,
            resultId = receipt.resultId,
            outcomeRevision = receipt.outcomeRevision,
            recordedAtMillis = receipt.receivedAtMillis,
            // The request may have been created up to one skew allowance in
            // the watch's future, then accepted through another at expiry.
            replayUntilMillis = current.expiresLocallyAtMillis + 2 * QUICK_START_CLOCK_SKEW_MILLIS,
        )
        persistence.write(json.encodeToString(PersistedQuickStartPackage(
            acknowledged = acknowledged,
            acknowledgedHistory = state.acknowledgedHistory,
        )))
        ReleaseAcknowledgedQuickStartResult.Released(acknowledged)
    }

    private suspend fun terminate(
        requestId: String,
        terminalRevision: Long,
        nowEpochMillis: Long,
        status: QuickStartStatus,
    ): TerminateQuickStartResult = processMutex.withLock {
        val state = loadState() ?: return@withLock TerminateQuickStartResult.Missing
        val previousTerminal = state.terminal
        if (previousTerminal != null && previousTerminal.requestId == requestId) {
            return@withLock TerminateQuickStartResult.AlreadyTerminal(previousTerminal)
        }

        val current = state.sessionPackage ?: return@withLock TerminateQuickStartResult.Missing
        if (current.request.requestId != requestId) return@withLock TerminateQuickStartResult.Missing
        val expectedRevision = current.request.revision + 1
        if (terminalRevision != expectedRevision) {
            return@withLock TerminateQuickStartResult.RevisionMismatch(expectedRevision)
        }
        if (current.state == QuickStartPackageState.STARTING) {
            return@withLock TerminateQuickStartResult.RefusedStarting(current)
        }

        val terminal = TerminalQuickStartRecord(
            requestId = requestId,
            revision = terminalRevision,
            targetNodeId = current.request.targetNodeId,
            status = status,
            recordedAtMillis = nowEpochMillis,
        )
        persistTerminal(terminal, state.acknowledgedHistory)
        TerminateQuickStartResult.Terminated(terminal)
    }

    private suspend fun loadCurrent(nowEpochMillis: Long): WatchSessionPackage? {
        val state = loadState() ?: return null
        val current = state.sessionPackage ?: return null
        if (
            current.state == QuickStartPackageState.READY &&
            nowEpochMillis > current.expiresLocallyAtMillis
        ) {
            persistOrClear(state.copy(sessionPackage = null))
            return null
        }
        return current
    }

    private suspend fun loadState(): PersistedQuickStartPackage? {
        val raw = persistence.read() ?: return null
        val state = try {
            json.decodeFromString<PersistedQuickStartPackage>(raw)
        } catch (_: Exception) {
            persistence.write(null)
            return null
        }
        val terminalIsValid = state.terminal?.let { terminal ->
            terminal.status in setOf(QuickStartStatus.DISMISSED, QuickStartStatus.CANCELLED) &&
                validateQuickStartAcknowledgement(
                    acknowledgement = QuickStartAcknowledgement(
                        requestId = terminal.requestId,
                        revision = terminal.revision,
                        targetNodeId = terminal.targetNodeId,
                        status = terminal.status,
                        watchUpdatedAtMillis = terminal.recordedAtMillis,
                    ),
                    expectedRequestId = terminal.requestId,
                    expectedTargetNodeId = terminal.targetNodeId,
                ) == null
        } ?: true
        fun validAcknowledged(record: AcknowledgedQuickStartRecord): Boolean =
            record.requestId.isNotBlank() && record.resultId.isNotBlank() &&
                record.requestId.length <= 128 && record.resultId.length <= 128 &&
                record.outcomeRevision >= 0 && record.recordedAtMillis >= 0 &&
                record.replayUntilMillis >= 0 &&
                record.requestId.none(Char::isISOControl) && record.resultId.none(Char::isISOControl)
        val acknowledgedIsValid = state.acknowledged?.let(::validAcknowledged) ?: true
        val historyIsValid = state.acknowledgedHistory.size <= MAX_ACKNOWLEDGED_HISTORY &&
            state.acknowledgedHistory.all(::validAcknowledged) &&
            (state.acknowledgedHistory.map { it.requestId } + listOfNotNull(state.acknowledged?.requestId))
                .distinct().size == state.acknowledgedHistory.size +
                (if (state.acknowledged == null) 0 else 1)
        val recordCount = listOf(state.sessionPackage, state.terminal, state.acknowledged).count { it != null }
        if (
            state.schemaVersion != QUICK_START_SCHEMA_VERSION ||
            (state.sessionPackage != null &&
                state.sessionPackage.request.schemaVersion != QUICK_START_SCHEMA_VERSION) ||
            (recordCount != 1 && (recordCount != 0 || state.acknowledgedHistory.isEmpty())) ||
            !terminalIsValid || !acknowledgedIsValid || !historyIsValid
        ) {
            persistence.write(null)
            return null
        }
        return state
    }

    private suspend fun persistPackage(
        sessionPackage: WatchSessionPackage,
        history: List<AcknowledgedQuickStartRecord>,
    ) {
        persistence.write(json.encodeToString(PersistedQuickStartPackage(
            sessionPackage = sessionPackage, acknowledgedHistory = history,
        )))
    }

    private suspend fun persistTerminal(
        terminal: TerminalQuickStartRecord,
        history: List<AcknowledgedQuickStartRecord>,
    ) {
        persistence.write(json.encodeToString(PersistedQuickStartPackage(
            terminal = terminal, acknowledgedHistory = history,
        )))
    }

    private suspend fun persistOrClear(state: PersistedQuickStartPackage) {
        if (state.acknowledgedHistory.isEmpty() && state.sessionPackage == null &&
            state.terminal == null && state.acknowledged == null
        ) persistence.write(null) else persistence.write(json.encodeToString(state))
    }
}
