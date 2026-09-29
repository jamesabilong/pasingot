package app.personal.workouttracker.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPresentationPolicyTest {
    @Test fun `interactive presentation keeps controls and progress visible`() {
        val policy = WatchPresentationPolicy()

        assertTrue(policy.allowInteraction)
        assertTrue(policy.showDecorativeProgress)
    }

    @Test fun `reduced motion removes progress motion without disabling controls`() {
        val policy = WatchPresentationPolicy(reducedMotion = true)

        assertTrue(policy.allowInteraction)
        assertFalse(policy.showDecorativeProgress)
    }

    @Test fun `ambient presentation removes controls and decorative progress`() {
        val policy = WatchPresentationPolicy(ambient = true)

        assertFalse(policy.allowInteraction)
        assertFalse(policy.showDecorativeProgress)
    }

    @Test fun `burn in offsets cycle around the origin`() {
        assertEquals(listOf(-2 to -2, 2 to -2, 2 to 2, -2 to 2),
            (0..3).map(::ambientBurnInOffset))
        assertEquals(ambientBurnInOffset(0), ambientBurnInOffset(4))
    }
}
