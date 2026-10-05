import Foundation

/// Decides when an in-memory `SessionSnapshot` must be flushed to disk/UserDefaults.
/// Mirrors `com.meritscreen.feature.child.domain.SessionPersistPolicy` in Android.
///
/// Invariants:
/// - Always persist on phase change, app change, day transition, daily minute increment, or shield expiration change.
/// - Otherwise, throttles in-block ticking flushes to `AppConfig.sessionPersistIntervalSeconds` (45s)
///   so the 1 Hz timer loop does NOT write to disk every second.
public enum SessionPersistPolicy: Sendable {

    public static func shouldPersist(
        previousPersisted: SessionSnapshot?,
        next: SessionSnapshot,
        nowElapsedMs: Int64,
        lastPersistElapsedMs: Int64,
        intervalSeconds: Int = AppConfig.sessionPersistIntervalSeconds
    ) -> Bool {
        guard let previous = previousPersisted else {
            return true
        }

        // Discrete state changes require immediate persistence
        if previous.phase != next.phase { return true }
        if previous.activePackageOrBundleId != next.activePackageOrBundleId { return true }
        if previous.activeAppId != next.activeAppId { return true }
        if previous.dayKey != next.dayKey { return true }
        if previous.minutesUsedToday != next.minutesUsedToday { return true }
        if previous.deviceShieldedUntilEpochMs != next.deviceShieldedUntilEpochMs { return true }
        if previous.blockDurationMinutes != next.blockDurationMinutes { return true }
        if previous.cooldownMinutes != next.cooldownMinutes { return true }

        // Interval-based throttling for in-block ticking updates
        let intervalMs = Int64(max(1, intervalSeconds) * 1000)
        return (nowElapsedMs - lastPersistElapsedMs) >= intervalMs
    }
}
