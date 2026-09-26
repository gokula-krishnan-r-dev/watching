package com.meritscreen.feature.child.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object ForegroundAppDetector {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Tracks activity lifecycle events incrementally so stale RESUMED events cannot
     * keep an app active after its PAUSED/STOPPED event. One tracker belongs to the
     * foreground monitoring service, not to a Compose screen. */
    class Tracker {
        private var lastQueryTimeMs = 0L
        private var foregroundPackage: String? = null

        fun currentPackage(context: Context, nowWallTimeMs: Long = System.currentTimeMillis()): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val beginTime = if (lastQueryTimeMs == 0L) nowWallTimeMs - 24 * 60 * 60 * 1_000L
            else (lastQueryTimeMs - 1_000L).coerceAtLeast(0L)
        val events = runCatching { usm.queryEvents(beginTime, nowWallTimeMs) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foregroundPackage = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (foregroundPackage == event.packageName) foregroundPackage = null
                }
            }
        }
        lastQueryTimeMs = nowWallTimeMs
        return foregroundPackage
        }
    }
}
