package com.meritscreen.feature.child.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

enum class ForegroundLifecycleState {
    FOREGROUND_ACTIVE,
    BACKGROUND_PAUSED,
    BACKGROUND_STOPPED,
    SCREEN_OFF,
    LAUNCHER,
}

data class ForegroundAppInfo(
    val packageName: String?,
    val lifecycleState: ForegroundLifecycleState,
    val isLauncher: Boolean,
    val timestampMs: Long,
)

object ForegroundAppDetector {

    @Volatile
    private var lastKnownPackage: String? = null

    @Volatile
    private var lastKnownActivity: String? = null

    @Volatile
    private var lastState: ForegroundLifecycleState = ForegroundLifecycleState.LAUNCHER

    @Volatile
    private var lastQueryTimeMs: Long = 0L

    @Volatile
    private var cachedLauncherPackages: Set<String>? = null

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

    /**
     * Resolves complete foreground info including package, lifecycle state (Active, Paused, Screen-off, Launcher),
     * and whether the active window is the device launcher.
     * Uses a robust state machine over a continuous sliding window so foreground detection never drops to null
     * after 10 seconds of user activity within the same screen.
     */
    fun getForegroundInfo(context: Context): ForegroundAppInfo {
        val now = System.currentTimeMillis()

        // 1. Hardware Screen Power / Keyguard check
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) {
            lastState = ForegroundLifecycleState.SCREEN_OFF
            return ForegroundAppInfo(
                packageName = lastKnownPackage,
                lifecycleState = ForegroundLifecycleState.SCREEN_OFF,
                isLauncher = false,
                timestampMs = now,
            )
        }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            lastState = ForegroundLifecycleState.SCREEN_OFF
            return ForegroundAppInfo(
                packageName = lastKnownPackage,
                lifecycleState = ForegroundLifecycleState.SCREEN_OFF,
                isLauncher = false,
                timestampMs = now,
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            val isLauncher = isLauncherPackage(context, lastKnownPackage)
            return ForegroundAppInfo(
                packageName = lastKnownPackage,
                lifecycleState = if (isLauncher) ForegroundLifecycleState.LAUNCHER else lastState,
                isLauncher = isLauncher,
                timestampMs = now,
            )
        }

        // 2. Sliding window query: if cold-start, look back 15 minutes to find current foreground app;
        // otherwise query since last check with a small 2-second overlap so no event boundary is missed.
        val beginTime = if (lastQueryTimeMs == 0L || now - lastQueryTimeMs > 15 * 60 * 1000L) {
            now - (15 * 60 * 1000L)
        } else {
            (lastQueryTimeMs - 2_000L).coerceAtLeast(now - (15 * 60 * 1000L))
        }
        lastQueryTimeMs = now

        val events = runCatching { usm.queryEvents(beginTime, now) }.getOrNull()
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                android.util.Log.d("MeritDetector", "event: type=${event.eventType} pkg=$pkg cls=${event.className}")
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastKnownPackage = pkg
                        lastKnownActivity = event.className
                        lastState = ForegroundLifecycleState.FOREGROUND_ACTIVE
                    }
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        // Only transition to STOPPED if the stopped activity is the current top resumed activity.
                        // When an app transitions between activities (e.g. SplashActivity -> MainActivity),
                        // SplashActivity stops AFTER MainActivity resumes. Do not overwrite MainActivity's ACTIVE state.
                        if (pkg == lastKnownPackage && (lastKnownActivity == null || event.className == lastKnownActivity)) {
                            lastState = ForegroundLifecycleState.BACKGROUND_STOPPED
                        }
                    }
                    UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        lastState = ForegroundLifecycleState.SCREEN_OFF
                    }
                    UsageEvents.Event.KEYGUARD_HIDDEN, UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (lastKnownPackage != null && lastState == ForegroundLifecycleState.SCREEN_OFF) {
                            lastState = ForegroundLifecycleState.FOREGROUND_ACTIVE
                        }
                    }
                }
            }
        }

        val isLauncher = isLauncherPackage(context, lastKnownPackage)
        val effectiveState = if (isLauncher) ForegroundLifecycleState.LAUNCHER else lastState
        android.util.Log.d("MeritDetector", "result: pkg=$lastKnownPackage act=$lastKnownActivity state=$effectiveState isLauncher=$isLauncher")

        return ForegroundAppInfo(
            packageName = lastKnownPackage,
            lifecycleState = effectiveState,
            isLauncher = isLauncher,
            timestampMs = now,
        )
    }

    /**
     * Resolves the package name currently in the foreground using the robust state machine.
     * Returns null if screen is off, paused, or stopped.
     */
    fun getForegroundPackage(context: Context): String? {
        val info = getForegroundInfo(context)
        return when (info.lifecycleState) {
            ForegroundLifecycleState.FOREGROUND_ACTIVE,
            ForegroundLifecycleState.LAUNCHER -> info.packageName
            ForegroundLifecycleState.BACKGROUND_PAUSED,
            ForegroundLifecycleState.BACKGROUND_STOPPED,
            ForegroundLifecycleState.SCREEN_OFF -> null
        }
    }

    fun isLauncherPackage(context: Context, packageName: String?): Boolean {
        if (packageName == null) return false
        val launchers = cachedLauncherPackages ?: getLauncherPackages(context).also {
            cachedLauncherPackages = it
        }
        return launchers.contains(packageName)
    }

    private fun getLauncherPackages(context: Context): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val set = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toMutableSet()
        set.add("com.google.android.apps.nexuslauncher")
        set.add("com.android.launcher3")
        set.add("com.android.launcher")
        set.add("com.sec.android.app.launcher")
        return set
    }

    fun resetState() {
        lastKnownPackage = null
        lastKnownActivity = null
        lastState = ForegroundLifecycleState.LAUNCHER
        lastQueryTimeMs = 0L
    }
}
