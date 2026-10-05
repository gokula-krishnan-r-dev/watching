import SwiftUI
@preconcurrency import FamilyControls

extension FamilyControlsMember: @unchecked @retroactive Sendable {}
extension AuthorizationCenter: @unchecked @retroactive Sendable {}

/// Manages Family Controls / Screen Time authorization for the child device.
/// Interacts with Apple's `AuthorizationCenter.shared`.
@Observable
@MainActor
public final class ScreenTimeAuthorizationManager {
    public static let shared = ScreenTimeAuthorizationManager()

    public var status: AuthorizationStatus = .notDetermined
    public var isLoading: Bool = false
    public var errorMessage: String?

    public var isAuthorized: Bool {
        status == .approved
    }

    private let authCenter: AuthorizationCenter

    public init(authCenter: AuthorizationCenter = .shared) {
        self.authCenter = authCenter
        refreshStatus()
    }

    public func refreshStatus() {
        self.status = authCenter.authorizationStatus
    }

    /// Requests Screen Time authorization for this device.
    /// Member type `.individual` is used for unmanaged/single device setups,
    /// or `.child` when managed via iCloud Family Sharing.
    public func requestAuthorization(for member: FamilyControlsMember = .individual) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authCenter.requestAuthorization(for: member)
            refreshStatus()
            return isAuthorized
        } catch {
            print("[ScreenTimeAuth] Authorization request failed: \(error.localizedDescription)")
            self.errorMessage = error.localizedDescription
            refreshStatus()
            return false
        }
    }

    public func revokeAuthorization() {
        authCenter.revokeAuthorization { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success:
                    self?.refreshStatus()
                case .failure(let error):
                    self?.errorMessage = error.localizedDescription
                }
            }
        }
    }
}
