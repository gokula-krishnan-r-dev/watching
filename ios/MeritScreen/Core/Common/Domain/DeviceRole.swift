import Foundation

/// Active role of this device.
/// Controls the root navigation graph in RoleGateView.
/// Mirrors `com.meritscreen.core.common.session.DeviceRole`.
public enum DeviceRole: String, Codable, Sendable, CaseIterable {
    case unassigned
    case parent
    case child
}
