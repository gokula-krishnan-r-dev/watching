import Foundation
import Observation

/// Observes iOS system Low Power Mode state.
/// Allows coordinators and views to scale down non-essential background tasks and animations
/// while keeping the core child hub, quiz, and fail-lock snappy.
@Observable
@MainActor
public final class LowPowerModeMonitor {
    public static let shared = LowPowerModeMonitor()

    public private(set) var isLowPowerModeEnabled: Bool

    public init() {
        self.isLowPowerModeEnabled = ProcessInfo.processInfo.isLowPowerModeEnabled

        NotificationCenter.default.addObserver(
            forName: NSNotification.Name.NSProcessInfoPowerStateDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.isLowPowerModeEnabled = ProcessInfo.processInfo.isLowPowerModeEnabled
            SecureLogger.info("[LowPowerModeMonitor] Low Power Mode changed: \(self.isLowPowerModeEnabled)")
        }
    }

    /// Synchronous non-isolated check for background coordinators.
    public nonisolated static var currentIsLowPower: Bool {
        ProcessInfo.processInfo.isLowPowerModeEnabled
    }
}
