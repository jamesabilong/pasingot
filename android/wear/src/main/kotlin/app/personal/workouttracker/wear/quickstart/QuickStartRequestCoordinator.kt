package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartAcknowledgement
import app.personal.workouttracker.shared.quickstart.QuickStartDataLayerPaths
import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.QuickStartRejectionReason
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.shared.quickstart.QuickStartStatus
import app.personal.workouttracker.shared.quickstart.QuickStartValidationCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

fun interface QuickStartReceiptClient {
    suspend fun send(acknowledgement: QuickStartAcknowledgement)
}

/** Keeps Data Layer identity checks, durable acceptance, and the receipt in one order. */
class QuickStartRequestCoordinator(
    private val packages: QuickStartPackageStore,
    private val legacySessions: LegacySessionSnapshotSource,
    private val receipts: QuickStartReceiptClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun receive(
        payload: String,
        path: String,
        observedPhoneNodeId: String,
        localWatchNodeId: String,
        nowEpochMillis: Long,
    ): QuickStartAcknowledgement? {
        val pathId = path.removePrefix(QuickStartDataLayerPaths.REQUEST_PREFIX)
        if (!path.startsWith(QuickStartDataLayerPaths.REQUEST_PREFIX) ||
            pathId != runCatching { UUID.fromString(pathId).toString() }.getOrNull() ||
            observedPhoneNodeId.isBlank() || observedPhoneNodeId.length > 256 ||
            observedPhoneNodeId.any(Char::isISOControl) ||
            localWatchNodeId.isBlank() || localWatchNodeId.length > 256 ||
            localWatchNodeId.any(Char::isISOControl) ||
            observedPhoneNodeId == localWatchNodeId
        ) return null

        // The path is the only safe request identity when JSON cannot be decoded.
        val request = try { json.decodeFromString<QuickStartRequest>(payload) }
        catch (_: Exception) { return reject(pathId, 1, localWatchNodeId,
            QuickStartRejectionReason.INVALID_PAYLOAD, nowEpochMillis) }
        if (request.requestId != pathId || request.targetNodeId != localWatchNodeId) return null

        val existing = packages.current(nowEpochMillis)
        if (existing == null || existing.request != request ||
            existing.sourcePhoneNodeId != observedPhoneNodeId) {
            if (legacySessions.entries().blockingSessions().isNotEmpty()) {
                return reject(pathId, request.revision.coerceAtLeast(1), localWatchNodeId,
                    QuickStartRejectionReason.ACTIVE_SESSION, nowEpochMillis)
            }
        }

        val acknowledgement = when (val result = packages.accept(request, nowEpochMillis, observedPhoneNodeId)) {
            is AcceptQuickStartResult.Accepted -> ready(result.sessionPackage.request, localWatchNodeId, nowEpochMillis)
            is AcceptQuickStartResult.Duplicate -> when (result.sessionPackage.state) {
                QuickStartPackageState.READY -> ready(result.sessionPackage.request, localWatchNodeId, nowEpochMillis)
                // STARTED follows durable runtime creation, not merely the STARTING marker.
                QuickStartPackageState.STARTING -> return null
            }
            is AcceptQuickStartResult.RejectedPending -> rejectValue(pathId, request.revision,
                localWatchNodeId, QuickStartRejectionReason.PENDING_REQUEST, nowEpochMillis)
            is AcceptQuickStartResult.RejectedInvalid -> {
                val reason = if (result.issue.code == QuickStartValidationCode.UNSUPPORTED_SCHEMA)
                    QuickStartRejectionReason.UNSUPPORTED_SCHEMA else QuickStartRejectionReason.INVALID_PAYLOAD
                val status = if (result.issue.code == QuickStartValidationCode.EXPIRED)
                    QuickStartStatus.EXPIRED else QuickStartStatus.REJECTED
                // Expiry is a terminal decision after an offer may have been Ready.
                val revision = if (status == QuickStartStatus.EXPIRED && request.revision < Long.MAX_VALUE)
                    request.revision + 1 else request.revision.coerceAtLeast(1)
                QuickStartAcknowledgement(pathId, revision, localWatchNodeId,
                    status, if (status == QuickStartStatus.REJECTED) reason else null, nowEpochMillis)
            }
            is AcceptQuickStartResult.PreviouslyTerminated -> QuickStartAcknowledgement(
                pathId, result.terminal.revision, localWatchNodeId, result.terminal.status,
                watchUpdatedAtMillis = result.terminal.recordedAtMillis)
            is AcceptQuickStartResult.PreviouslyAcknowledged -> return null
            AcceptQuickStartResult.ReplayHistoryFull -> rejectValue(pathId, request.revision,
                localWatchNodeId, QuickStartRejectionReason.STORAGE_ERROR, nowEpochMillis)
        }
        receipts.send(acknowledgement)
        return acknowledgement
    }

    private fun ready(request: QuickStartRequest, node: String, now: Long) =
        QuickStartAcknowledgement(request.requestId, request.revision, node, QuickStartStatus.READY,
            watchUpdatedAtMillis = now)

    private fun rejectValue(id: String, revision: Long, node: String,
                            reason: QuickStartRejectionReason, now: Long) =
        QuickStartAcknowledgement(id, revision.coerceAtLeast(1), node, QuickStartStatus.REJECTED,
            reason, now)

    private suspend fun reject(id: String, revision: Long, node: String,
                               reason: QuickStartRejectionReason, now: Long): QuickStartAcknowledgement =
        rejectValue(id, revision, node, reason, now).also { receipts.send(it) }
}
