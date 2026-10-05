import Foundation


/// An immutable point-in-time snapshot of the child session state.
/// Pure Swift value type. Zero platform/UI dependencies.
public struct SessionSnapshot: Codable, Sendable, Equatable {
    public var phase: SessionPhase
    public var activePackageOrBundleId: String?
    public var activeAppId: String?
    public var blockStartedEpochMs: Int64?
    public var blockDurationMinutes: Int
    public var minutesAccruedInBlock: Double
    public var deviceShieldedUntilEpochMs: Int64?
    public var cooldownMinutes: Int
    public var dayKey: String
    public var minutesUsedToday: Int
    public var lastTickEpochMs: Int64?
    public var quizGraceUntilEpochMs: Int64?

    public init(
        phase: SessionPhase = .idle,
        activePackageOrBundleId: String? = nil,
        activeAppId: String? = nil,
        blockStartedEpochMs: Int64? = nil,
        blockDurationMinutes: Int = AppConfig.defaultBlockMinutes,
        minutesAccruedInBlock: Double = 0.0,
        deviceShieldedUntilEpochMs: Int64? = nil,
        cooldownMinutes: Int = AppConfig.defaultCooldownMinutes,
        dayKey: String = SessionSnapshot.todayKey(),
        minutesUsedToday: Int = 0,
        lastTickEpochMs: Int64? = nil,
        quizGraceUntilEpochMs: Int64? = nil
    ) {
        self.phase = phase
        self.activePackageOrBundleId = activePackageOrBundleId
        self.activeAppId = activeAppId
        self.blockStartedEpochMs = blockStartedEpochMs
        self.blockDurationMinutes = blockDurationMinutes
        self.minutesAccruedInBlock = minutesAccruedInBlock
        self.deviceShieldedUntilEpochMs = deviceShieldedUntilEpochMs
        self.cooldownMinutes = cooldownMinutes
        self.dayKey = dayKey
        self.minutesUsedToday = minutesUsedToday
        self.lastTickEpochMs = lastTickEpochMs
        self.quizGraceUntilEpochMs = quizGraceUntilEpochMs
    }

    public func remainingBlockMinutes() -> Int {
        guard phase == .inBlock else { return max(0, Int(Double(blockDurationMinutes) - minutesAccruedInBlock)) }
        return max(0, Int(Double(blockDurationMinutes) - minutesAccruedInBlock))
    }

    public func remainingBlockSeconds() -> Int {
        let remSec = max(0, Int((Double(blockDurationMinutes) - minutesAccruedInBlock) * 60.0))
        return remSec
    }

    public func remainingCooldownSeconds(nowEpochMs: Int64) -> Int {
        guard let until = deviceShieldedUntilEpochMs else { return 0 }
        return max(0, Int((until - nowEpochMs) / 1000))
    }

    public func dailyRemainingMinutes(policy: ChildPolicy) -> Int {
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        return max(0, ceiling - minutesUsedToday)
    }

    public static func todayKey() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter.string(from: Date())
    }
}
