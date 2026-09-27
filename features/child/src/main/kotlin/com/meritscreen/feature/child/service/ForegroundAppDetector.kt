package com.meritscreen.feature.child.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

object ForegroundAppDetector {

    /** Home and Android control surfaces are not child app usage sessions. */
    fun isHomeOrSystemUi(context: Context, packageName: String): Boolean {
        val homePackages = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
        return isHomeOrSystemUiPackage(packageName, homePackages)
    }

    internal fun isHomeOrSystemUiPackage(packageName: String, homePackages: Set<String>): Boolean =
        packageName in homePackages || packageName in SYSTEM_UI_PACKAGES

    private val SYSTEM_UI_PACKAGES = setOf(
        "com.android.launcher3",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

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
        private var foregroundActivity: String? = null

        fun currentPackage(context: Context, nowWallTimeMs: Long = System.currentTimeMillis()): String? {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            if (lastQueryTimeMs > nowWallTimeMs) {
                lastQueryTimeMs = 0L
                foregroundPackage = null
                foregroundActivity = null
            }
            val beginTime = if (lastQueryTimeMs == 0L) nowWallTimeMs - 24 * 60 * 60 * 1_000L
                else (lastQueryTimeMs - 1_000L).coerceAtLeast(0L)
            val events = runCatching { usm.queryEvents(beginTime, nowWallTimeMs) }.getOrNull() ?: return null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            foregroundPackage = event.packageName
                            foregroundActivity = event.className
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            if (foregroundPackage == event.packageName &&
                                (event.className == null || foregroundActivity == event.className)
                            ) {
                                foregroundPackage = null
                                foregroundActivity = null
                            }
                        }
                    }
                } else {
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            foregroundPackage = event.packageName
                            foregroundActivity = event.className
                        }
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (foregroundPackage == event.packageName) {
                                foregroundPackage = null
                                foregroundActivity = null
                            }
                        }
                    }
                }
            }
            lastQueryTimeMs = nowWallTimeMs
            return foregroundPackage
        }
    }
}
