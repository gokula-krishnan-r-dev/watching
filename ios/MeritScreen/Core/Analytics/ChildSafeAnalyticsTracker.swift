import Foundation
import os

/// Child-safe analytics tracker enforcing Apple Kids Category and COPPA privacy compliance.
/// Drops any non-child-allowed events when the active role is child.
/// Never collects PII, names, tokens, or quiz details.
public final class ChildSafeAnalyticsTracker: AnalyticsTrackerProtocol, @unchecked Sendable {
    public static let shared = ChildSafeAnalyticsTracker()

    private let logger = Logger(subsystem: "com.watching.app", category: "Analytics")
    private let lock = NSLock()
    private var currentRole: String = "unassigned"
    private var isChildDevice: Bool = false

    public var onEventTracked: (@Sendable (AnalyticsEvent) -> Void)?

    public init() {}

    public func setDeviceRole(_ role: String) {
        lock.lock()
        defer { lock.unlock() }
        currentRole = role
        isChildDevice = (role.lowercased() == "child")
    }

    public func track(_ event: AnalyticsEvent) {
        lock.lock()
        let isChild = isChildDevice
        let role = currentRole
        lock.unlock()

        // COPPA & Kids Category invariant: Drop unauthorized events on child devices
        if isChild && !event.allowedOnChild {
            logger.debug("[Analytics] Suppressing parent-only event '\(event.key)' on child device")
            return
        }

        logger.info("[Analytics] Event: \(event.key) (role: \(role))")
        onEventTracked?(event)
    }
}

/// Lightweight diagnostic crash and error reporter.
public final class CrashReporter: CrashReporterProtocol, @unchecked Sendable {
    public static let shared = CrashReporter()

    private let logger = Logger(subsystem: "com.watching.app", category: "Diagnostics")
    private let lock = NSLock()
    private var currentRole: String = "unassigned"

    public init() {}

    public func record(error: Error) {
        lock.lock()
        let role = currentRole
        lock.unlock()
        logger.error("[CrashReporter] Error (role: \(role)): \(error.localizedDescription)")
    }

    public func log(_ message: String) {
        logger.info("[CrashReporter] \(message)")
    }

    public func setDeviceRole(_ role: String) {
        lock.lock()
        defer { lock.unlock() }
        currentRole = role
    }
}
