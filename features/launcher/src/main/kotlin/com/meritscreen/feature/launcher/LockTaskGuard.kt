package com.meritscreen.feature.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context

/**
 * Best-effort screen pinning during the device-wide fail-lock (Shielded) state, using only
 * the public `Activity.startLockTask()` / `stopLockTask()` APIs — no Device Owner required.
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

    fun engage(activity: Activity) {
        runCatching { activity.startLockTask() }
    }

    fun release(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    fun isEngaged(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
