package com.meritscreen.feature.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import com.meritscreen.core.common.domain.SessionPhase

/**
 * Best-effort screen pinning during the device-wide fail-lock (Shielded / parent-paused)
 * state, using only the public [Activity.startLockTask] / [Activity.stopLockTask] APIs —
 * no Device Owner required.
 *
 * **Why release before launching apps:** while this Activity is pinned, Android refuses
 * `startActivity` to another package and shows the system toast
 * *"To unpin this app, touch and hold the Recents and Back buttons…"*. Residual pin after
 * cooldown ends (OEM/Samsung races, failed `stopLockTask`, process recreation) is the
 * intermittent "YouTube won't open" bug on the Approved Playground. Callers **must**
 * [ensureReleased] before every external launch and whenever Home is not in fail-lock.
 *
 * **Documented limitation:** without Device Owner + `DevicePolicyManager.setLockTaskPackages`,
 * this is *screen pinning*, not enforced Lock Task Mode. Android still lets the user exit via
 * the system's own long-press Back+Recents "unpin" gesture, and some OEM skins expose an extra
 * affordance to leave it. This is a deliberate choice: we only use officially guaranteed
 * behavior, and we do not attempt to defeat the user's own exit gesture, per the
 * "no anti-bypass workarounds" requirement. Parents should treat this as a nudge that
 * complements the quiz/cooldown enforcement (which is server-verified), not a substitute for it.
 */
object LockTaskGuard {

    /**
     * True while Child Home should pin for fail-lock (parent pause or [SessionPhase.Shielded]).
     * Playground / daily-cap UI must never pin — pinning blocks approved-app launches.
     */
    fun shouldPinForFailLock(paused: Boolean, phase: SessionPhase): Boolean =
        paused || phase == SessionPhase.Shielded

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun engage(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (isEngaged(activity)) return
        runCatching { activity.startLockTask() }
    }

    /**
     * Idempotent unpin. Safe to call when not pinned, when the activity is finishing, or when
     * the OEM already cleared pin — never throws to callers.
     */
    fun ensureReleased(activity: Activity) {
        if (!isEngaged(activity)) return
        if (activity.isFinishing || activity.isDestroyed) {
            // Still attempt stop — some OEMs keep pin after finish until stopLockTask.
            runCatching { activity.stopLockTask() }
            return
        }
        runCatching { activity.stopLockTask() }
        // Samsung / One UI sometimes reports pinned for a beat after stop; second stop is harmless.
        if (isEngaged(activity)) {
            runCatching { activity.stopLockTask() }
        }
    }

    /** @deprecated Prefer [ensureReleased] — name kept for existing call sites. */
    fun release(activity: Activity) = ensureReleased(activity)

    /**
     * Clears pin (if any) so [Context.startActivity] to another package can succeed.
     * Returns the host [Activity] when found so callers can re-engage fail-lock pin on a
     * failed launch or after the child returns from an emergency app.
     */
    fun prepareExternalLaunch(context: Context): Activity? {
        val activity = findActivity(context) ?: return null
        ensureReleased(activity)
        return activity
    }

    /** Re-pin only when fail-lock still applies and pin is not already active. */
    fun restoreFailLockPinIfNeeded(activity: Activity?, shouldPin: Boolean) {
        if (activity == null || !shouldPin) return
        engage(activity)
    }

    fun isEngaged(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
