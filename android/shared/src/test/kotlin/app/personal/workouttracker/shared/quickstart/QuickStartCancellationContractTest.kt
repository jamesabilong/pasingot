package app.personal.workouttracker.shared.quickstart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStartCancellationContractTest {
    private val request = QuickStartRequest(
        requestId = REQUEST_ID,
        createdAtMillis = 1_000,
        expiresAtMillis = 301_000,
        targetNodeId = WATCH,
        source = QuickStartSource.SINGLE,
        exercises = listOf(QuickStartExercise("item", "squat", "Squat", 3, "10", 60)),
    )
    private val cancellation = QuickStartCancellation(REQUEST_ID, 2, WATCH, PHONE, 2_000)

    @Test fun `cancellation round trip binds request path and both nodes`() {
        assertEquals(
            QuickStartCancellationDecodeResult.Accepted(cancellation),
            decodeQuickStartCancellation(
                encodeQuickStartCancellation(cancellation),
                quickStartCancellationPath(REQUEST_ID),
                PHONE,
                WATCH,
                request,
            ),
        )
    }

    @Test fun `wrong path node revision and malformed payload fail closed`() {
        val payload = encodeQuickStartCancellation(cancellation)
        val attempts = listOf(
            decodeQuickStartCancellation(payload, quickStartCancellationPath(OTHER_ID), PHONE, WATCH, request),
            decodeQuickStartCancellation(payload, quickStartCancellationPath(REQUEST_ID), "other-phone", WATCH, request),
            decodeQuickStartCancellation(payload, quickStartCancellationPath(REQUEST_ID), PHONE, "other-watch", request),
            decodeQuickStartCancellation(
                encodeQuickStartCancellation(cancellation.copy(revision = 3)),
                quickStartCancellationPath(REQUEST_ID), PHONE, WATCH, request,
            ),
            decodeQuickStartCancellation("broken", quickStartCancellationPath(REQUEST_ID), PHONE, WATCH, request),
        )
        assertTrue(attempts.all { it is QuickStartCancellationDecodeResult.Rejected })
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val OTHER_ID = "123e4567-e89b-12d3-a456-426614174001"
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
