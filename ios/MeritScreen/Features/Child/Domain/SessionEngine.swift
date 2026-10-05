import Foundation

/// Pure Swift Session Engine for child app block monitoring, quiz gating,
/// and device-wide fail lock enforcement.
///
/// Invariants (Product locked - docs 07 & 13):
/// 1. App block expires -> transitions to `.quizDue`.
/// 2. Quiz passed -> transitions to `.inBlock` with granted minutes.
/// 3. Quiz failed -> device-wide fail lock (`.shielded`) for all non-emergency apps.
/// 4. Cooldown ends -> unlocks all approved apps.
/// 5. Retry quiz passed during cooldown -> unlocks immediately and grants next block.
/// 6. Retry quiz failed during cooldown -> keeps shield and RESTARTS cooldown timer from zero.
/// 7. Emergency apps (Phone, contacts) are ALWAYS unlocked and reachable.
public enum SessionEngine {

    public static func onNewDay(
        snapshot: SessionSnapshot,
        dayKey: String = SessionSnapshot.todayKey()
    ) -> SessionSnapshot {
        if snapshot.dayKey == dayKey { return snapshot }
        var updated = snapshot
        updated.dayKey = dayKey
        updated.minutesUsedToday = 0
        updated.minutesAccruedInBlock = 0.0
        if snapshot.phase == .inBlock {
            updated.phase = .idle
            updated.activePackageOrBundleId = nil
            updated.activeAppId = nil
        }
        updated.blockStartedEpochMs = nil
        updated.quizGraceUntilEpochMs = nil
        return updated
    }

    /// Ticks the clock by `nowEpochMs`.
    /// When `isAppActive` is true and in `.inBlock`, screen time is accumulated.
    public static func tick(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64,
        policy: ChildPolicy,
        isAppActive: Bool = true
    ) -> SessionSnapshot {
        var state = onNewDay(snapshot: snapshot)
        state = expireShieldIfNeeded(snapshot: state, nowEpochMs: nowEpochMs)

        if state.phase == .inBlock {
            let lastTick = state.lastTickEpochMs
            let deltaMs: Int64
            if let lastTick, isAppActive {
                deltaMs = max(0, nowEpochMs - lastTick)
            } else if let started = state.blockStartedEpochMs, state.minutesAccruedInBlock == 0.0, isAppActive {
                deltaMs = max(0, nowEpochMs - started)
            } else {
                deltaMs = 0
            }

            if isAppActive && deltaMs > 0 {
                let previousAccruedMs = Int64(round(state.minutesAccruedInBlock * 60_000.0))
                let newAccruedMs = previousAccruedMs + deltaMs
                let newAccruedMinutes = Double(newAccruedMs) / 60_000.0

                let previousCompletedMins = Int(previousAccruedMs / 60_000)
                let newCompletedMins = Int(newAccruedMs / 60_000)
                let newlyCompletedMinutes = max(0, newCompletedMins - previousCompletedMins)
                let today = state.minutesUsedToday + newlyCompletedMinutes

                state.minutesAccruedInBlock = newAccruedMinutes
                state.minutesUsedToday = today
                state.lastTickEpochMs = nowEpochMs

                let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
                let isUnderGrace = state.quizGraceUntilEpochMs != nil && nowEpochMs < (state.quizGraceUntilEpochMs ?? 0)
                let ceilingHit = today >= ceiling && !isUnderGrace
                let blockHit = newAccruedMinutes >= Double(state.blockDurationMinutes) && !isUnderGrace

                if ceilingHit {
                    state.phase = .dailyLimitLock
                    state.blockStartedEpochMs = nil
                } else if blockHit && policy.quizMode == .appBlock {
                    state.phase = .quizDue
                    state.blockStartedEpochMs = nil
                }
            } else {
                state.lastTickEpochMs = nowEpochMs
            }
        }
        return state
    }

    public static func openApp(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64,
        rule: AppRule,
        policy: ChildPolicy
    ) -> SessionSnapshot {
        var state = tick(snapshot: snapshot, nowEpochMs: nowEpochMs, policy: policy, isAppActive: false)
        if rule.isEmergency || isEmergencyPackage(rule.packageOrBundleId, policy: policy) {
            return state
        }
        if !rule.allowed { return state }
        if policy.paused { return state }
        if state.phase == .shielded || state.phase == .dailyLimitLock || state.phase == .bedtimeLock { return state }
        if state.phase == .quizDue { return state }

        let isUnderGrace = state.quizGraceUntilEpochMs != nil && nowEpochMs < (state.quizGraceUntilEpochMs ?? 0)
        let ceiling = policy.dailyCeilingMinutes
        if let ceiling, state.minutesUsedToday >= ceiling, !isUnderGrace {
            state.phase = .dailyLimitLock
            return state
        }

        // Resume existing active app without wiping accrued block time
        if state.activePackageOrBundleId == rule.packageOrBundleId && state.phase == .inBlock {
            state.lastTickEpochMs = nowEpochMs
            return state
        }

        let blockMinutes = (rule.blockMinutes > 0 ? rule.blockMinutes : policy.defaultBlockMinutes)
            .clamped(to: 1...240)

        switch policy.quizMode {
        case .everySession:
            state.phase = .quizDue
            state.activePackageOrBundleId = rule.packageOrBundleId
            state.activeAppId = rule.appId
            state.blockDurationMinutes = blockMinutes
            state.minutesAccruedInBlock = 0.0
            state.blockStartedEpochMs = nil
            state.lastTickEpochMs = nowEpochMs
        default:
            state.phase = .inBlock
            state.activePackageOrBundleId = rule.packageOrBundleId
            state.activeAppId = rule.appId
            state.blockDurationMinutes = blockMinutes
            state.minutesAccruedInBlock = 0.0
            state.blockStartedEpochMs = nowEpochMs
            state.lastTickEpochMs = nowEpochMs
            state.cooldownMinutes = rule.cooldownMinutes.clamped(to: 1...180)
            state.quizGraceUntilEpochMs = nil
        }
        return state
    }

    public static func onQuizPassed(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64,
        rule: AppRule?,
        policy: ChildPolicy
    ) -> SessionSnapshot {
        let grant = (rule?.grantOnPassMinutes ?? policy.defaultBlockMinutes)
            .clamped(to: AppConfig.sessionChunkMinMinutes...AppConfig.sessionChunkMaxMinutes)
        var extra = policy.rewardsEnabled ? policy.extraMinutesOnPass : 0
        if policy.rewardsEnabled && policy.weekendBonusEnabled && isWeekend() {
            extra += AppConfig.weekendBonusExtraMinutes
        }
        let block = (grant + extra).clamped(to: AppConfig.sessionChunkMinMinutes...AppConfig.sessionChunkMaxMinutes)
        let graceUntil = nowEpochMs + Int64(block * 60_000)

        var updated = snapshot
        updated.phase = .inBlock
        updated.blockDurationMinutes = block
        updated.minutesAccruedInBlock = 0.0
        updated.blockStartedEpochMs = nowEpochMs
        updated.lastTickEpochMs = nowEpochMs
        updated.deviceShieldedUntilEpochMs = nil
        updated.quizGraceUntilEpochMs = graceUntil
        if let rule {
            updated.activePackageOrBundleId = rule.packageOrBundleId
            updated.activeAppId = rule.appId
        }
        return updated
    }

    public static func onQuizFailed(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64,
        policy: ChildPolicy,
        rule: AppRule?
    ) -> SessionSnapshot {
        let cooldown = (rule?.cooldownMinutes ?? policy.defaultCooldownMinutes).clamped(to: 1...180)
        var updated = snapshot
        updated.phase = .shielded
        updated.deviceShieldedUntilEpochMs = nowEpochMs + Int64(cooldown * 60_000)
        updated.cooldownMinutes = cooldown
        updated.blockStartedEpochMs = nil
        updated.minutesAccruedInBlock = 0.0
        updated.lastTickEpochMs = nowEpochMs
        updated.quizGraceUntilEpochMs = nil
        return updated
    }

    /// When a child attempts and fails a Retry Quiz during cooldown:
    /// Invariant: Cooldown RESTARTS from zero length so child cannot hammer retries.
    public static func onRetryQuizFailed(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64,
        policy: ChildPolicy,
        rule: AppRule?
    ) -> SessionSnapshot {
        let cooldown = (rule?.cooldownMinutes ?? policy.defaultCooldownMinutes).clamped(to: 1...180)
        var updated = snapshot
        updated.phase = .shielded
        updated.deviceShieldedUntilEpochMs = nowEpochMs + Int64(cooldown * 60_000)
        updated.cooldownMinutes = cooldown
        updated.blockStartedEpochMs = nil
        updated.minutesAccruedInBlock = 0.0
        updated.lastTickEpochMs = nowEpochMs
        updated.quizGraceUntilEpochMs = nil
        return updated
    }

    public static func expireShieldIfNeeded(
        snapshot: SessionSnapshot,
        nowEpochMs: Int64
    ) -> SessionSnapshot {
        guard snapshot.phase == .shielded else { return snapshot }
        guard let until = snapshot.deviceShieldedUntilEpochMs else {
            var updated = snapshot
            updated.phase = .idle
            return updated
        }
        if nowEpochMs >= until {
            var updated = snapshot
            updated.phase = .idle
            updated.deviceShieldedUntilEpochMs = nil
            updated.activePackageOrBundleId = nil
            updated.activeAppId = nil
            return updated
        }
        return snapshot
    }

    /// Parent PIN override - ends fail lock immediately.
    public static func endFailLock(snapshot: SessionSnapshot) -> SessionSnapshot {
        guard snapshot.phase == .shielded || snapshot.deviceShieldedUntilEpochMs != nil else {
            return snapshot
        }
        var updated = snapshot
        updated.phase = .idle
        updated.deviceShieldedUntilEpochMs = nil
        updated.activePackageOrBundleId = nil
        updated.activeAppId = nil
        updated.blockStartedEpochMs = nil
        updated.minutesAccruedInBlock = 0.0
        updated.quizGraceUntilEpochMs = nil
        return updated
    }

    /// Parent PIN override / Bonus - ends daily limit lock and grants extra time.
    public static func endDailyLimitLock(
        snapshot: SessionSnapshot,
        bonusMinutes: Int = 15
    ) -> SessionSnapshot {
        var updated = snapshot
        updated.phase = .inBlock
        updated.blockDurationMinutes = bonusMinutes
        updated.minutesAccruedInBlock = 0.0
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        updated.blockStartedEpochMs = now
        updated.lastTickEpochMs = now
        updated.quizGraceUntilEpochMs = now + Int64(bonusMinutes * 60_000)
        return updated
    }

    public static func returnHome(snapshot: SessionSnapshot, nowEpochMs: Int64) -> SessionSnapshot {
        var updated = snapshot
        updated.phase = .idle
        updated.activePackageOrBundleId = nil
        updated.activeAppId = nil
        updated.blockStartedEpochMs = nil
        updated.minutesAccruedInBlock = 0.0
        updated.lastTickEpochMs = nowEpochMs
        return updated
    }

    public static func canLaunch(snapshot: SessionSnapshot, rule: AppRule, policy: ChildPolicy) -> Bool {
        if rule.isEmergency || isEmergencyPackage(rule.packageOrBundleId, policy: policy) { return true }
        if !rule.allowed { return false }
        if policy.paused { return false }
        if snapshot.phase == .shielded || snapshot.phase == .dailyLimitLock || snapshot.phase == .bedtimeLock { return false }
        if let ceiling = policy.dailyCeilingMinutes, snapshot.minutesUsedToday >= ceiling {
            let isUnderGrace = snapshot.quizGraceUntilEpochMs != nil &&
                Int64(Date().timeIntervalSince1970 * 1000) < (snapshot.quizGraceUntilEpochMs ?? 0)
            if !isUnderGrace { return false }
        }
        return true
    }

    public static func isEmergencyPackage(_ packageOrBundleId: String, policy: ChildPolicy) -> Bool {
        let lower = packageOrBundleId.lowercased()
        if lower.contains("dialer") || lower.contains("telecom") || lower.contains("phone") {
            return true
        }
        return false
    }

    public static func isWeekend(date: Date = Date()) -> Bool {
        let calendar = Calendar.current
        let weekday = calendar.component(.weekday, from: date)
        // 1 = Sunday, 7 = Saturday
        return weekday == 1 || weekday == 7
    }
}

private extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
