import Foundation

/// Protocol for telemetry tracking with strict COPPA and Apple Kids Category enforcement.
public protocol AnalyticsTrackerProtocol: Sendable {
    func track(_ event: AnalyticsEvent)
    func setDeviceRole(_ role: String)
}

/// Protocol for application diagnostics and crash reporting.
public protocol CrashReporterProtocol: Sendable {
    func record(error: Error)
    func log(_ message: String)
    func setDeviceRole(_ role: String)
}
