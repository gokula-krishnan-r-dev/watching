package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.config.AppConfig

/**
 * Decides when an in-memory [SessionSnapshot] must hit Room.
 * Always persist on phase / app / day / daily-minute changes; otherwise at most once per
 * [AppConfig.SESSION_PERSIST_INTERVAL_SECONDS] so the 1 Hz tick loop does not write every second.
 */
object SessionPersistPolicy {
    fun shouldPersist(
        previousPersisted: SessionSnapshot?,
        next: SessionSnapshot,
        nowElapsedMs: Long,
        lastPersistElapsedMs: Long,
        intervalSeconds: Int = AppConfig.SESSION_PERSIST_INTERVAL_SECONDS,
    ): Boolean {
        if (previousPersisted == null) return true
        if (previousPersisted.phase != next.phase) return true
        if (previousPersisted.activePackage != next.activePackage) return true
        if (previousPersisted.activeAppId != next.activeAppId) return true
        if (previousPersisted.dayKey != next.dayKey) return true
        if (previousPersisted.minutesUsedToday != next.minutesUsedToday) return true
        if (previousPersisted.deviceShieldedUntilElapsedMs != next.deviceShieldedUntilElapsedMs) return true
        if (previousPersisted.blockDurationMinutes != next.blockDurationMinutes) return true
        if (previousPersisted.cooldownMinutes != next.cooldownMinutes) return true
        val intervalMs = intervalSeconds.coerceAtLeast(1) * 1_000L
        return nowElapsedMs - lastPersistElapsedMs >= intervalMs
    }
}
