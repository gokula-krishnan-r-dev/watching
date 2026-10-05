import Foundation

public struct PersistedPinGate: Sendable, Equatable {
    public let failedAttempts: Int
    public let lockedUntilEpochMs: Int64?

    public init(failedAttempts: Int = 0, lockedUntilEpochMs: Int64? = nil) {
        self.failedAttempts = failedAttempts
        self.lockedUntilEpochMs = lockedUntilEpochMs
    }

    public var isLocked: Bool {
        guard let lockedUntil = lockedUntilEpochMs else { return false }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        return nowMs < lockedUntil
    }

    public var remainingMinutes: Int {
        guard let lockedUntil = lockedUntilEpochMs else { return 0 }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let diffMs = max(0, lockedUntil - nowMs)
        return Int(ceil(Double(diffMs) / 60_000.0))
    }
}

/// Keychain-backed PIN lockout store so process death or device restart cannot reset the attempt counter.
/// Mirrors `com.meritscreen.core.security.pin.PinGateStore`.
public final class PinGateStore: Sendable {
    private let storage: SecureStorageProtocol
    private let attemptsKey = "parent_pin_failed_attempts"
    private let lockedUntilKey = "parent_pin_locked_until_ms"

    public init(storage: SecureStorageProtocol = KeychainStorage.shared) {
        self.storage = storage
    }

    public func get() -> PersistedPinGate {
        let attempts = Int(storage.get(attemptsKey) ?? "") ?? 0
        let lockedUntil = Int64(storage.get(lockedUntilKey) ?? "")
        return PersistedPinGate(failedAttempts: max(0, attempts), lockedUntilEpochMs: lockedUntil)
    }

    public func recordFailedAttempt() -> PersistedPinGate {
        let current = get()
        let newAttempts = current.failedAttempts + 1

        if newAttempts >= AppConfig.parentPinMaxAttempts {
            let lockDurationMs = Int64(AppConfig.parentPinLockoutMinutes * 60 * 1000)
            let lockedUntil = Int64(Date().timeIntervalSince1970 * 1000) + lockDurationMs
            storage.put(attemptsKey, value: "\(newAttempts)")
            storage.put(lockedUntilKey, value: "\(lockedUntil)")
            return PersistedPinGate(failedAttempts: newAttempts, lockedUntilEpochMs: lockedUntil)
        } else {
            storage.put(attemptsKey, value: "\(newAttempts)")
            return PersistedPinGate(failedAttempts: newAttempts, lockedUntilEpochMs: nil)
        }
    }

    public func clear() {
        storage.remove(attemptsKey)
        storage.remove(lockedUntilKey)
    }
}
