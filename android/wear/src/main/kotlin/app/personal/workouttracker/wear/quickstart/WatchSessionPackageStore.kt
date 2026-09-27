package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QUICK_START_SCHEMA_VERSION
import app.personal.workouttracker.shared.quickstart.QUICK_START_CLOCK_SKEW_MILLIS
import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartCancellation
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartRejectionReason
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
import java.security.MessageDigest

interface QuickStartPackagePersistence {
    suspend fun read(): String?
    suspend fun write(raw: String?)
}

interface QuickStartPackageStore {
    suspend fun current(nowEpochMillis: Long): WatchSessionPackage?
    /** Resolve durable decisions before applying an offer's arrival-time expiry. */
    suspend fun replay(request: QuickStartRequest, nowEpochMillis: Long, observedPhoneNodeId: String?): AcceptQuickStartResult?
    /** Persist a refused native offer before its terminal receipt becomes visible. */
    suspend fun recordRefusal(request: QuickStartRequest, acknowledgement: QuickStartAcknowledgement,
                              observedPhoneNodeId: String): TerminalQuickStartRecord?
    suspend fun accept(request: QuickStartRequest, receivedAtMillis: Long, observedPhoneNodeId: String? = null): AcceptQuickStartResult
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
    suspend fun cancelRemote(
        cancellation: QuickStartCancellation,
        observedPhoneNodeId: String,
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
    data object IdentityMismatch : AcceptQuickStartResult
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
    val reason: QuickStartRejectionReason? = null,
    val sourcePhoneNodeId: String? = null,
    val requestFingerprint: String? = null,
    val replayUntilMillis: Long? = null,
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
    val terminalHistory: List<TerminalQuickStartRecord> = emptyList(),
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

    override suspend fun replay(
        request: QuickStartRequest,
        nowEpochMillis: Long,
        observedPhoneNodeId: String?,
    ): AcceptQuickStartResult? = processMutex.withLock {
        val state = loadState()
        terminalReplay(state, request, observedPhoneNodeId)?.let { return@withLock it }
        val shape = validateQuickStartRequest(request, request.createdAtMillis)
        if (shape is QuickStartValidationResult.Invalid) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(shape.issue)
        }
        replay(state, request, nowEpochMillis, observedPhoneNodeId)
    }

    override suspend fun recordRefusal(
        request: QuickStartRequest,
        acknowledgement: QuickStartAcknowledgement,
        observedPhoneNodeId: String,
    ): TerminalQuickStartRecord? = processMutex.withLock {
        require(acknowledgement.status in setOf(QuickStartStatus.REJECTED, QuickStartStatus.EXPIRED))
        require(validateQuickStartAcknowledgement(acknowledgement, request.requestId, request.targetNodeId) == null)
        require(observedPhoneNodeId.isNodeId() && request.targetNodeId.isNodeId() &&
            observedPhoneNodeId != request.targetNodeId)
        val state = loadState() ?: PersistedQuickStartPackage()
        when (val replay = terminalReplay(state, request, observedPhoneNodeId)) {
            is AcceptQuickStartResult.PreviouslyTerminated -> return@withLock replay.terminal
            null -> Unit
            else -> return@withLock null
        }
        // Never replace a durable Ready/Starting decision or a completed result
        // with a rejection for another sender or a changed same-ID payload.
        if (state.sessionPackage?.request?.requestId == request.requestId ||
            (listOfNotNull(state.acknowledged) + state.acknowledgedHistory)
                .any { it.requestId == request.requestId }) return@withLock null
        val now = acknowledgement.watchUpdatedAtMillis
        val history = state.terminalHistory.filter { now <= it.replayDeadline() }
        val currentTerminal = state.terminal?.takeIf { now <= it.replayDeadline() }
        // Reserve a future terminal slot for the current package as well.
        if (history.size + (if (currentTerminal == null) 0 else 1) +
            (if (state.sessionPackage == null) 0 else 1) >= MAX_ACKNOWLEDGED_HISTORY) return@withLock null
        // A future-dated invalid offer may become time-valid later. Its refused
        // identity must survive until the last possible arrival, not just a TTL
        // measured from the rejection.
        val offerReplayDeadline = if (request.expiresAtMillis > Long.MAX_VALUE - QUICK_START_CLOCK_SKEW_MILLIS)
            Long.MAX_VALUE else request.expiresAtMillis + QUICK_START_CLOCK_SKEW_MILLIS
        val terminal = TerminalQuickStartRecord(request.requestId, acknowledgement.revision,
            request.targetNodeId, acknowledgement.status, now, acknowledgement.reason,
            observedPhoneNodeId, request.fingerprint(), maxOf(now, offerReplayDeadline))
        persistence.write(json.encodeToString(state.copy(terminal = currentTerminal,
            terminalHistory = history + terminal)))
        terminal
    }

    override suspend fun accept(
        request: QuickStartRequest,
        receivedAtMillis: Long,
        observedPhoneNodeId: String?,
    ): AcceptQuickStartResult = processMutex.withLock {
        if (observedPhoneNodeId != null && (observedPhoneNodeId.isBlank() ||
            observedPhoneNodeId.length > 256 || observedPhoneNodeId.any(Char::isISOControl) ||
            observedPhoneNodeId == request.targetNodeId)) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(
                QuickStartValidationIssue(app.personal.workouttracker.shared.quickstart.QuickStartValidationCode.INVALID_SOURCE_NODE,
                    field = "observedPhoneNodeId"),
            )
        }
        val state = loadState()
        terminalReplay(state, request, observedPhoneNodeId)?.let { return@withLock it }
        val shape = validateQuickStartRequest(request, request.createdAtMillis)
        if (shape is QuickStartValidationResult.Invalid) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(shape.issue)
        }
        replay(state, request, receivedAtMillis, observedPhoneNodeId)?.let { return@withLock it }
        val validated = validateQuickStartRequest(request, receivedAtMillis)
        if (validated is QuickStartValidationResult.Invalid) {
            return@withLock AcceptQuickStartResult.RejectedInvalid(validated.issue)
        }
        val incoming = (validated as QuickStartValidationResult.Valid).sessionPackage
            .copy(sourcePhoneNodeId = observedPhoneNodeId)
        val recentHistory = state?.acknowledgedHistory.orEmpty().filter {
            receivedAtMillis <= it.replayUntilMillis
        }
        val recentTerminals = state?.terminalHistory.orEmpty().filter {
            receivedAtMillis <= it.replayDeadline()
        }
        val existing = state?.sessionPackage?.takeUnless { sessionPackage ->
            sessionPackage.state == QuickStartPackageState.READY &&
                receivedAtMillis > sessionPackage.expiresLocallyAtMillis
        }
        if (state?.sessionPackage != null && existing == null) {
            expire(state, receivedAtMillis)
        }
        if (existing != null) {
            return@withLock AcceptQuickStartResult.RejectedPending(existing)
        }
        val keptHistory = recentHistory + listOfNotNull(state?.acknowledged).filter {
            receivedAtMillis <= it.replayUntilMillis
        }
        val expiredTerminal = state?.sessionPackage?.takeIf { existing == null }?.let {
            expiredRecord(it, receivedAtMillis)
        }
        val keptTerminals = recentTerminals + listOfNotNull(state?.terminal, expiredTerminal).filter {
            receivedAtMillis <= it.replayDeadline()
        }
        if (keptHistory.size >= MAX_ACKNOWLEDGED_HISTORY ||
            keptTerminals.size >= MAX_ACKNOWLEDGED_HISTORY) {
            return@withLock AcceptQuickStartResult.ReplayHistoryFull
        }

        persistPackage(incoming, keptHistory, keptTerminals)
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
            expire(state, nowEpochMillis)
            return@withLock MarkQuickStartStartingResult.Expired
        }
        if (current.state == QuickStartPackageState.STARTING) {
            return@withLock MarkQuickStartStartingResult.AlreadyStarting(current)
        }

        val starting = current.copy(state = QuickStartPackageState.STARTING)
        persistPackage(starting, state.acknowledgedHistory, state.terminalHistory)
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

    override suspend fun cancelRemote(
        cancellation: QuickStartCancellation,
        observedPhoneNodeId: String,
        nowEpochMillis: Long,
    ): TerminateQuickStartResult = processMutex.withLock {
        val state = loadState() ?: return@withLock TerminateQuickStartResult.Missing
        (listOfNotNull(state.terminal) + state.terminalHistory)
            .firstOrNull { it.requestId == cancellation.requestId }
            ?.let { terminal ->
                return@withLock if (terminal.revision == cancellation.revision &&
                    terminal.targetNodeId == cancellation.targetNodeId &&
                    terminal.sourcePhoneNodeId == observedPhoneNodeId
                ) TerminateQuickStartResult.AlreadyTerminal(terminal)
                else TerminateQuickStartResult.Missing
            }
        val current = state.sessionPackage ?: return@withLock TerminateQuickStartResult.Missing
        if (current.request.requestId != cancellation.requestId ||
            current.request.targetNodeId != cancellation.targetNodeId ||
            current.sourcePhoneNodeId != observedPhoneNodeId
        ) return@withLock TerminateQuickStartResult.Missing
        val expectedRevision = current.request.revision + 1
        if (cancellation.revision != expectedRevision) {
            return@withLock TerminateQuickStartResult.RevisionMismatch(expectedRevision)
        }
        if (current.state == QuickStartPackageState.STARTING) {
            return@withLock TerminateQuickStartResult.RefusedStarting(current)
        }
        val terminal = TerminalQuickStartRecord(
            requestId = cancellation.requestId,
            revision = cancellation.revision,
            targetNodeId = cancellation.targetNodeId,
            status = QuickStartStatus.CANCELLED,
            recordedAtMillis = nowEpochMillis,
            sourcePhoneNodeId = observedPhoneNodeId,
            requestFingerprint = current.request.fingerprint(),
        )
        persistTerminal(terminal, state.acknowledgedHistory, state.terminalHistory)
        TerminateQuickStartResult.Terminated(terminal)
    }

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
            terminalHistory = state.terminalHistory,
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
            sourcePhoneNodeId = current.sourcePhoneNodeId,
            requestFingerprint = current.request.fingerprint(),
        )
        persistTerminal(terminal, state.acknowledgedHistory, state.terminalHistory)
        TerminateQuickStartResult.Terminated(terminal)
    }

    private suspend fun loadCurrent(nowEpochMillis: Long): WatchSessionPackage? {
        val state = loadState() ?: return null
        val current = state.sessionPackage ?: return null
        if (
            current.state == QuickStartPackageState.READY &&
            nowEpochMillis > current.expiresLocallyAtMillis
        ) {
            expire(state, nowEpochMillis)
            return null
        }
        return current
    }

    private suspend fun replay(
        state: PersistedQuickStartPackage?,
        request: QuickStartRequest,
        nowEpochMillis: Long,
        observedPhoneNodeId: String?,
    ): AcceptQuickStartResult? {
        terminalReplay(state, request, observedPhoneNodeId)?.let { return it }
        (listOfNotNull(state?.acknowledged) + state?.acknowledgedHistory.orEmpty())
            .firstOrNull { it.requestId == request.requestId }
            ?.let { return AcceptQuickStartResult.PreviouslyAcknowledged(it) }
        val existing = state?.sessionPackage ?: return null
        if (existing.request.requestId != request.requestId) return null
        if (existing.request != request || existing.sourcePhoneNodeId != observedPhoneNodeId) {
            return AcceptQuickStartResult.IdentityMismatch
        }
        if (existing.state == QuickStartPackageState.READY && nowEpochMillis > existing.expiresLocallyAtMillis) {
            return AcceptQuickStartResult.PreviouslyTerminated(expire(state, nowEpochMillis))
        }
        return AcceptQuickStartResult.Duplicate(existing)
    }

    private fun expiredRecord(current: WatchSessionPackage, nowEpochMillis: Long) = TerminalQuickStartRecord(
        requestId = current.request.requestId,
        revision = current.request.revision + 1,
        targetNodeId = current.request.targetNodeId,
        status = QuickStartStatus.EXPIRED,
        recordedAtMillis = nowEpochMillis,
        sourcePhoneNodeId = current.sourcePhoneNodeId,
        requestFingerprint = current.request.fingerprint(),
    )

    private fun terminalReplay(state: PersistedQuickStartPackage?, request: QuickStartRequest,
                               observedPhoneNodeId: String?): AcceptQuickStartResult? {
        val terminal = (listOfNotNull(state?.terminal) + state?.terminalHistory.orEmpty())
            .firstOrNull { it.requestId == request.requestId } ?: return null
        // Older ownerless records remain readable for local recovery. Native
        // delivery cannot prove their owner, so it neither replays nor replaces.
        return if (terminal.targetNodeId == request.targetNodeId &&
            terminal.sourcePhoneNodeId == observedPhoneNodeId &&
            (terminal.requestFingerprint == request.fingerprint() ||
                (terminal.requestFingerprint == null && observedPhoneNodeId == null))) {
            AcceptQuickStartResult.PreviouslyTerminated(terminal)
        } else AcceptQuickStartResult.IdentityMismatch
    }

    private fun QuickStartRequest.fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(this).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun String.isNodeId(): Boolean = isNotBlank() && length <= 256 && none(Char::isISOControl)

    private fun TerminalQuickStartRecord.replayDeadline(): Long {
        val retentionMillis = QUICK_START_TTL_MILLIS + 2 * QUICK_START_CLOCK_SKEW_MILLIS
        val minimumDeadline = if (recordedAtMillis > Long.MAX_VALUE - retentionMillis) Long.MAX_VALUE
            else recordedAtMillis + retentionMillis
        return maxOf(minimumDeadline, replayUntilMillis ?: minimumDeadline)
    }

    private suspend fun expire(state: PersistedQuickStartPackage, nowEpochMillis: Long): TerminalQuickStartRecord {
        val terminal = expiredRecord(requireNotNull(state.sessionPackage), nowEpochMillis)
        persistTerminal(terminal, state.acknowledgedHistory, state.terminalHistory)
        return terminal
    }

    private suspend fun loadState(): PersistedQuickStartPackage? {
        val raw = persistence.read() ?: return null
        val state = try {
            json.decodeFromString<PersistedQuickStartPackage>(raw)
        } catch (_: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Stored Quick Start package is unreadable")
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("Stored Quick Start package is unreadable")
        }
        fun validTerminal(terminal: TerminalQuickStartRecord): Boolean =
            terminal.status in setOf(QuickStartStatus.DISMISSED, QuickStartStatus.CANCELLED,
                QuickStartStatus.EXPIRED, QuickStartStatus.REJECTED) &&
                terminal.targetNodeId.isNodeId() &&
                (terminal.sourcePhoneNodeId == null ||
                    (terminal.sourcePhoneNodeId.isNodeId() && terminal.sourcePhoneNodeId != terminal.targetNodeId)) &&
                (terminal.requestFingerprint == null || terminal.requestFingerprint.matches(Regex("[0-9a-f]{64}"))) &&
                (terminal.replayUntilMillis == null || terminal.replayUntilMillis >= terminal.recordedAtMillis) &&
                validateQuickStartAcknowledgement(
                    acknowledgement = QuickStartAcknowledgement(
                        requestId = terminal.requestId,
                        revision = terminal.revision,
                        targetNodeId = terminal.targetNodeId,
                        status = terminal.status,
                        reason = terminal.reason,
                        watchUpdatedAtMillis = terminal.recordedAtMillis,
                    ),
                    expectedRequestId = terminal.requestId,
                    expectedTargetNodeId = terminal.targetNodeId,
                ) == null
        val terminalIsValid = state.terminal?.let(::validTerminal) ?: true
        fun validAcknowledged(record: AcknowledgedQuickStartRecord): Boolean =
            record.requestId.isNotBlank() && record.resultId.isNotBlank() &&
                record.requestId.length <= 128 && record.resultId.length <= 128 &&
                record.outcomeRevision >= 0 && record.recordedAtMillis >= 0 &&
                record.replayUntilMillis >= 0 &&
                record.requestId.none(Char::isISOControl) && record.resultId.none(Char::isISOControl)
        val acknowledgedIsValid = state.acknowledged?.let(::validAcknowledged) ?: true
        val acknowledgedIds = state.acknowledgedHistory.map { it.requestId } +
            listOfNotNull(state.acknowledged?.requestId)
        val terminalIds = state.terminalHistory.map { it.requestId } + listOfNotNull(state.terminal?.requestId)
        val allIds = acknowledgedIds + terminalIds + listOfNotNull(state.sessionPackage?.request?.requestId)
        val historyIsValid = acknowledgedIds.size <= MAX_ACKNOWLEDGED_HISTORY &&
            state.acknowledgedHistory.all(::validAcknowledged) &&
            (state.acknowledgedHistory.map { it.requestId } + listOfNotNull(state.acknowledged?.requestId))
                .distinct().size == state.acknowledgedHistory.size +
                (if (state.acknowledged == null) 0 else 1)
        val terminalHistoryIsValid = terminalIds.size <= MAX_ACKNOWLEDGED_HISTORY &&
            state.terminalHistory.all(::validTerminal) &&
            (state.terminalHistory.map { it.requestId } + listOfNotNull(state.terminal?.requestId)).distinct().size ==
                state.terminalHistory.size + (if (state.terminal == null) 0 else 1)
        val recordCount = listOf(state.sessionPackage, state.terminal, state.acknowledged).count { it != null }
        if (
            state.schemaVersion != QUICK_START_SCHEMA_VERSION ||
            (state.sessionPackage != null &&
                (validateQuickStartRequest(state.sessionPackage.request, state.sessionPackage.receivedAtMillis)
                    !is QuickStartValidationResult.Valid ||
                    state.sessionPackage.receivedAtMillis < 0 ||
                    state.sessionPackage.expiresLocallyAtMillis != state.sessionPackage.receivedAtMillis +
                        (state.sessionPackage.request.expiresAtMillis - state.sessionPackage.request.createdAtMillis))) ||
            (state.sessionPackage?.sourcePhoneNodeId?.let { source ->
                source.isBlank() || source.length > 256 || source.any(Char::isISOControl) ||
                    source == state.sessionPackage.request.targetNodeId
            } == true) ||
            (recordCount != 1 && (recordCount != 0 ||
                (state.acknowledgedHistory.isEmpty() && state.terminalHistory.isEmpty()))) ||
            !terminalIsValid || !acknowledgedIsValid || !historyIsValid || !terminalHistoryIsValid ||
            allIds.distinct().size != allIds.size
        ) {
            throw IllegalStateException("Stored Quick Start package is invalid")
        }
        return state
    }

    private suspend fun persistPackage(
        sessionPackage: WatchSessionPackage,
        history: List<AcknowledgedQuickStartRecord>,
        terminalHistory: List<TerminalQuickStartRecord>,
    ) {
        persistence.write(json.encodeToString(PersistedQuickStartPackage(
            sessionPackage = sessionPackage, acknowledgedHistory = history,
            terminalHistory = terminalHistory,
        )))
    }

    private suspend fun persistTerminal(
        terminal: TerminalQuickStartRecord,
        history: List<AcknowledgedQuickStartRecord>,
        terminalHistory: List<TerminalQuickStartRecord>,
    ) {
        persistence.write(json.encodeToString(PersistedQuickStartPackage(
            terminal = terminal, acknowledgedHistory = history,
            terminalHistory = terminalHistory,
        )))
    }

}
