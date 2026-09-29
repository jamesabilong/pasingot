package app.personal.workouttracker.wear.ui

import androidx.compose.runtime.staticCompositionLocalOf

data class WatchPresentationPolicy(
    val ambient: Boolean = false,
    val reducedMotion: Boolean = false,
    val burnInProtectionRequired: Boolean = false,
    val lowBitAmbient: Boolean = false,
    val ambientUpdate: Int = 0,
) {
    val showDecorativeProgress: Boolean get() = !ambient && !reducedMotion
    val allowInteraction: Boolean get() = !ambient
}

val LocalWatchPresentationPolicy = staticCompositionLocalOf { WatchPresentationPolicy() }

internal fun ambientBurnInOffset(update: Int): Pair<Int, Int> = when (update.mod(4)) {
    0 -> -2 to -2
    1 -> 2 to -2
    2 -> 2 to 2
    else -> -2 to 2
}
