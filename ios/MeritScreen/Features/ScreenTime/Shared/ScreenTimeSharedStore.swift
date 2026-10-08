import Foundation
import FamilyControls
import ManagedSettings

public enum ScreenTimeShieldState: Codable, Sendable, Equatable {
    case none
    case quizDue(appName: String, blockMinutes: Int)
    case failLock(deadlineEpochMs: Int64, cooldownMinutes: Int)
    case bedtimeLock(endLabel: String)
    case dailyLimitLock(minutesUsed: Int, ceilingMinutes: Int)

    public var isFailLock: Bool {
        if case .failLock = self { return true }
        return false
    }

    public var isQuizDue: Bool {
        if case .quizDue = self { return true }
        return false
    }

    public var isDailyLimitLock: Bool {
        if case .dailyLimitLock = self { return true }
        return false
    }
}

public struct PendingQuizRequest: Codable, Sendable, Equatable {
    public let appName: String?
    public let isRetry: Bool
    public let requestedAtEpochMs: Int64

    public init(
        appName: String? = nil,
        isRetry: Bool = false,
        requestedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.appName = appName
        self.isRetry = isRetry
        self.requestedAtEpochMs = requestedAtEpochMs
    }
}

/// Shared store synchronizing Screen Time selection, shield state, and quiz requests
/// between the main MeritScreen application and background extensions.
public final class ScreenTimeSharedStore: @unchecked Sendable {
    public static let shared = ScreenTimeSharedStore()
    public static let appGroupId = "group.com.watching.app"

    private let lock = NSLock()
    private let defaults: UserDefaults

    private let selectionKey = "meritscreen.activity_selection"
    private let emergencyTokensKey = "meritscreen.emergency_tokens"
    private let shieldStateKey = "meritscreen.shield_state"
    private let pendingQuizKey = "meritscreen.pending_quiz_request"
    private let failLockDeadlineKey = "meritscreen.fail_lock_deadline_epoch_ms"
    private let deviceRevokedKey = "meritscreen.device_revoked"
    private let blockMinutesKey = "meritscreen.block_minutes"
    private let cooldownMinutesKey = "meritscreen.cooldown_minutes"
    private let childNameKey = "meritscreen.child_name"
    private let bedtimeStartHourKey = "meritscreen.bedtime_start_hour"
    private let bedtimeStartMinKey = "meritscreen.bedtime_start_min"
    private let bedtimeEndHourKey = "meritscreen.bedtime_end_hour"
    private let bedtimeEndMinKey = "meritscreen.bedtime_end_min"
    private let bedtimeEnabledKey = "meritscreen.bedtime_enabled"

    public init(suiteName: String = appGroupId) {
        self.defaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    // MARK: - Activity Selection

    public func saveActivitySelection(_ selection: FamilyActivitySelection) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? PropertyListEncoder().encode(selection) {
            defaults.set(data, forKey: selectionKey)
        }
    }

    public func getActivitySelection() -> FamilyActivitySelection {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: selectionKey),
              let selection = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data) else {
            return FamilyActivitySelection()
        }
        return selection
    }

    // MARK: - Emergency Tokens (Never Shielded)

    public func saveEmergencyTokens(_ tokens: Set<ApplicationToken>) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? PropertyListEncoder().encode(tokens) {
            defaults.set(data, forKey: emergencyTokensKey)
        }
    }

    public func getEmergencyTokens() -> Set<ApplicationToken> {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: emergencyTokensKey),
              let tokens = try? PropertyListDecoder().decode(Set<ApplicationToken>.self, from: data) else {
            return []
        }
        return tokens
    }

    // MARK: - Shield State

    public func saveShieldState(_ state: ScreenTimeShieldState) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(state) {
            defaults.set(data, forKey: shieldStateKey)
        }
        if case .failLock(let deadline, _) = state {
            defaults.set(deadline, forKey: failLockDeadlineKey)
        } else if case .none = state {
            defaults.removeObject(forKey: failLockDeadlineKey)
        }
    }

    public func getShieldState() -> ScreenTimeShieldState {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: shieldStateKey),
              let state = try? JSONDecoder().decode(ScreenTimeShieldState.self, from: data) else {
            return .none
        }
        return state
    }

    public func getFailLockDeadline() -> Int64? {
        lock.lock()
        defer { lock.unlock() }
        let deadline = defaults.integer(forKey: failLockDeadlineKey)
        return deadline > 0 ? Int64(deadline) : nil
    }

    // MARK: - Pending Quiz Request (Bridge from ShieldAction)

    public func setPendingQuizRequest(_ request: PendingQuizRequest) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(request) {
            defaults.set(data, forKey: pendingQuizKey)
        }
    }

    public func hasPendingQuizRequest() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return defaults.data(forKey: pendingQuizKey) != nil
    }

    public func consumePendingQuizRequest() -> PendingQuizRequest? {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: pendingQuizKey),
              let request = try? JSONDecoder().decode(PendingQuizRequest.self, from: data) else {
            return nil
        }
        defaults.removeObject(forKey: pendingQuizKey)
        return request
    }

    // MARK: - Device Revocation Killswitch

    public func setDeviceRevoked(_ revoked: Bool) {
        lock.lock()
        defer { lock.unlock() }
        defaults.set(revoked, forKey: deviceRevokedKey)
    }

    public func isDeviceRevoked() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return defaults.bool(forKey: deviceRevokedKey)
    }

    // MARK: - Block Policy Values (readable by DeviceActivity extension)

    public func saveBlockMinutes(_ minutes: Int) {
        lock.lock()
        defer { lock.unlock() }
        defaults.set(minutes, forKey: blockMinutesKey)
    }

    public func getBlockMinutes(default defaultValue: Int = 30) -> Int {
        lock.lock()
        defer { lock.unlock() }
        let v = defaults.integer(forKey: blockMinutesKey)
        return v > 0 ? v : defaultValue
    }

    public func saveCooldownMinutes(_ minutes: Int) {
        lock.lock()
        defer { lock.unlock() }
        defaults.set(minutes, forKey: cooldownMinutesKey)
    }

    public func getCooldownMinutes(default defaultValue: Int = 15) -> Int {
        lock.lock()
        defer { lock.unlock() }
        let v = defaults.integer(forKey: cooldownMinutesKey)
        return v > 0 ? v : defaultValue
    }

    public func saveChildName(_ name: String) {
        lock.lock()
        defer { lock.unlock() }
        defaults.set(name, forKey: childNameKey)
    }

    public func getChildName() -> String {
        lock.lock()
        defer { lock.unlock() }
        return defaults.string(forKey: childNameKey) ?? "Child"
    }

    // MARK: - Bedtime Policy

    public func saveBedtimePolicy(enabled: Bool, startHour: Int, startMin: Int, endHour: Int, endMin: Int) {
        lock.lock()
        defer { lock.unlock() }
        defaults.set(enabled, forKey: bedtimeEnabledKey)
        defaults.set(startHour, forKey: bedtimeStartHourKey)
        defaults.set(startMin, forKey: bedtimeStartMinKey)
        defaults.set(endHour, forKey: bedtimeEndHourKey)
        defaults.set(endMin, forKey: bedtimeEndMinKey)
    }

    public func isBedtimeEnabled() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return defaults.bool(forKey: bedtimeEnabledKey)
    }

    /// Returns true if the current local time falls within the bedtime window.
    public func isCurrentlyBedtime() -> Bool {
        lock.lock()
        let enabled = defaults.bool(forKey: bedtimeEnabledKey)
        let startH = defaults.integer(forKey: bedtimeStartHourKey)
        let startM = defaults.integer(forKey: bedtimeStartMinKey)
        let endH = defaults.integer(forKey: bedtimeEndHourKey)
        let endM = defaults.integer(forKey: bedtimeEndMinKey)
        lock.unlock()

        guard enabled else { return false }

        let cal = Calendar.current
        let now = Date()
        let comps = cal.dateComponents([.hour, .minute], from: now)
        let nowMin = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        let startMin = startH * 60 + startM
        let endMin = endH * 60 + endM

        // Handle overnight windows (e.g., 22:00 – 06:00)
        if startMin <= endMin {
            return nowMin >= startMin && nowMin < endMin
        } else {
            return nowMin >= startMin || nowMin < endMin
        }
    }

    public func bedtimeEndLabel() -> String {
        lock.lock()
        let endH = defaults.integer(forKey: bedtimeEndHourKey)
        let endM = defaults.integer(forKey: bedtimeEndMinKey)
        lock.unlock()
        let ampm = endH < 12 ? "AM" : "PM"
        let h = endH == 0 ? 12 : (endH > 12 ? endH - 12 : endH)
        return String(format: "%d:%02d %@", h, endM, ampm)
    }

    // MARK: - Clear


    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        defaults.removeObject(forKey: selectionKey)
        defaults.removeObject(forKey: emergencyTokensKey)
        defaults.removeObject(forKey: shieldStateKey)
        defaults.removeObject(forKey: pendingQuizKey)
        defaults.removeObject(forKey: failLockDeadlineKey)
        defaults.removeObject(forKey: deviceRevokedKey)
    }
}
