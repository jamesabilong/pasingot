package app.personal.workouttracker.weardata

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTransferErrorMessageTest {
    @Test
    fun `missing companion services gives an actionable message without diagnostics`() {
        val error = IllegalStateException(
            "17: API: Wearable.API is not available on this device. " +
                "ConnectionResult{statusCode=API_UNAVAILABLE}"
        )

        assertEquals(
            "Wear OS watch services are unavailable. Install or open your watch companion app, pair the watch, then try again.",
            watchTransferErrorMessage(error),
        )
    }

    @Test
    fun `nested API not connected failure is normalized`() {
        val error = IllegalStateException("Transfer failed", IllegalStateException("API_NOT_CONNECTED"))

        assertEquals(
            "Wear OS watch services are unavailable. Install or open your watch companion app, pair the watch, then try again.",
            watchTransferErrorMessage(error),
        )
    }

    @Test
    fun `known disconnected message is preserved`() {
        val error = IllegalStateException("Watch not connected. Reconnect your watch and try again.")

        assertEquals(error.message, watchTransferErrorMessage(error))
    }

    @Test
    fun `timeout gives a retryable response instead of coroutine diagnostics`() {
        val error = try {
            runBlocking { withTimeout(0) { Unit } }
            error("Expected timeout")
        } catch (error: Throwable) {
            error
        }

        assertEquals(
            "Watch did not respond. Check the connection and try again.",
            watchTransferErrorMessage(error),
        )
    }

    @Test
    fun `unexpected failures do not expose implementation details`() {
        assertEquals(
            "Could not send workout. Check your watch connection and try again.",
            watchTransferErrorMessage(IllegalArgumentException("secret implementation detail")),
        )
    }
}
