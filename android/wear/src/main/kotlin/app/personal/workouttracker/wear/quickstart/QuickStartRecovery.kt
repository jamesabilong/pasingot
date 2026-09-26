package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartPackageState
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage

/** Navigation decision after activity recreation, process death, or reboot. */
enum class QuickStartRecoveryDecision {
    NONE,
    READY,
    EXPIRED,
    RESUME_STARTING,
}

fun recoverQuickStartPackage(
    sessionPackage: WatchSessionPackage?,
    nowEpochMillis: Long,
): QuickStartRecoveryDecision = when (sessionPackage?.state) {
    null -> QuickStartRecoveryDecision.NONE
    QuickStartPackageState.READY -> if (nowEpochMillis > sessionPackage.expiresLocallyAtMillis) {
        QuickStartRecoveryDecision.EXPIRED
    } else {
        QuickStartRecoveryDecision.READY
    }
    // A persisted STARTING transition must be resumed explicitly. Never
    // replay the foreground-only pre-start countdown or start unseen.
    QuickStartPackageState.STARTING -> QuickStartRecoveryDecision.RESUME_STARTING
}
