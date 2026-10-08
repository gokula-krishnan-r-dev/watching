package com.meritscreen.feature.child.ui

/**
 * Pure policy for where the quiz interrupt UI is hosted. Kept free of Android framework
 * types so unit tests can lock the PiP-covering contract without Robolectric.
 */
object InterruptSurfacePolicy {

    enum class Host {
        /** TYPE_APPLICATION_OVERLAY above activity + PiP layers. */
        OverlayAbovePip,

        /**
         * Regular Activity only. Documented limitation: another app's PiP may remain
         * visible on top until the child closes it or overlay permission is granted.
         */
        ActivityFallback,
    }

    fun shouldHostQuizInOverlay(canDrawOverlays: Boolean): Boolean = canDrawOverlays

    fun hostMode(canDrawOverlays: Boolean): Host =
        if (canDrawOverlays) Host.OverlayAbovePip else Host.ActivityFallback
}
