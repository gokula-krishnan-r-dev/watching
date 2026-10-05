import Foundation

/// Safeguard preventing revoked child devices from performing sync operations or network writes.
/// Mirrors `SECURITY.md` and Firestore security rules `ownDeviceNotRevoked(familyId, childId)`.
public final class DeviceRevocationGuard: @unchecked Sendable {
    public static let shared = DeviceRevocationGuard()

    private let sharedStore: ScreenTimeSharedStore

    public init(sharedStore: ScreenTimeSharedStore = .shared) {
        self.sharedStore = sharedStore
    }

    public var isRevoked: Bool {
        sharedStore.isDeviceRevoked()
    }

    /// Verifies device is not revoked. If revoked, triggers immediate wipe and throws.
    @MainActor
    public func assertNotRevoked() throws {
        if sharedStore.isDeviceRevoked() {
            SecureLogger.warning("[DeviceRevocationGuard] Revoked device attempted unauthorized action. Triggering local wipe.")
            ChildSessionWiper.shared.wipeAllChildData(sharedStore: sharedStore)
            throw AppError.auth("Device access has been revoked by parent.")
        }
    }
}
