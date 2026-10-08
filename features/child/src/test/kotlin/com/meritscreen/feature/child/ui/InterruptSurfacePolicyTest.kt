package com.meritscreen.feature.child.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents when the quiz interrupt must use a TYPE_APPLICATION_OVERLAY surface
 * so Picture-in-Picture cannot cover the quiz (consumer devices, no Accessibility).
 */
class InterruptSurfacePolicyTest {

    @Test
    fun overlaySurfaceRequiredWhenDrawOverlaysGranted() {
        assertTrue(InterruptSurfacePolicy.shouldHostQuizInOverlay(canDrawOverlays = true))
        assertFalse(InterruptSurfacePolicy.shouldHostQuizInOverlay(canDrawOverlays = false))
    }

    @Test
    fun activityFallbackIsHonestWhenOverlayUnavailable() {
        assertEquals(
            InterruptSurfacePolicy.Host.ActivityFallback,
            InterruptSurfacePolicy.hostMode(canDrawOverlays = false),
        )
        assertEquals(
            InterruptSurfacePolicy.Host.OverlayAbovePip,
            InterruptSurfacePolicy.hostMode(canDrawOverlays = true),
        )
    }
}
