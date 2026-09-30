package app.personal.workouttracker.weardata

import kotlinx.coroutines.TimeoutCancellationException
import java.util.Locale

private const val WATCH_NOT_CONNECTED =
    "Watch not connected. Reconnect your watch and try again."

internal fun watchTransferErrorMessage(error: Throwable): String {
    if (error is TimeoutCancellationException) {
        return "Watch did not respond. Check the connection and try again."
    }

    val messages = generateSequence(error as Throwable?) { it.cause }
        .take(8)
        .mapNotNull { it.message }
        .toList()
    if (messages.any { it == WATCH_NOT_CONNECTED }) return WATCH_NOT_CONNECTED

    val diagnostic = messages.joinToString(" ").lowercase(Locale.ROOT)
    if (
        "wearable.api is not available" in diagnostic ||
        "api_unavailable" in diagnostic ||
        "api_not_connected" in diagnostic
    ) {
        return "Wear OS watch services are unavailable. Install or open your watch companion app, pair the watch, then try again."
    }

    return "Could not send workout. Check your watch connection and try again."
}
