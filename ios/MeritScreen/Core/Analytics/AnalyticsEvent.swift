import Foundation

/// Safe telemetry events catalog matching Android `AnalyticsEvent`.
/// Invariant: Events have `allowedOnChild` flag to strictly comply with Apple Kids Category
/// and COPPA guidelines. Never log child PII, parent PINs, or quiz questions/answers.
public enum AnalyticsEvent: String, Sendable, CaseIterable {
    case appOpen = "app_open"
    case roleSelected = "role_selected"
    case policySyncCompleted = "policy_sync_completed"
    case quizCompleted = "quiz_completed"
    case parentSignedIn = "parent_signed_in"
    case parentSignedOut = "parent_signed_out"
    case childDevicePaired = "child_device_paired"
    case childDeviceUnpaired = "child_device_unpaired"
    case reportsViewed = "reports_viewed"

    public var key: String { rawValue }

    /// Indicates whether the event is permitted to be recorded from a child device.
    public var allowedOnChild: Bool {
        switch self {
        case .appOpen, .policySyncCompleted, .quizCompleted, .childDevicePaired, .childDeviceUnpaired:
            return true
        case .roleSelected, .parentSignedIn, .parentSignedOut, .reportsViewed:
            return false
        }
    }
}
