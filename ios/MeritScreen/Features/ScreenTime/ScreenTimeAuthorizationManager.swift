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

    /// `true` when this binary was signed with `com.apple.developer.family-controls`.
    /// App Store / TestFlight builds omit that entitlement until Apple approves distribution.
    public var isFamilyControlsEntitlementPresent: Bool {
        Self.bundleHasFamilyControlsEntitlement()
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

        guard isFamilyControlsEntitlementPresent else {
            errorMessage = "Screen Time shields ship after Apple approves Family Controls for this app. Parent/child pairing and quizzes still work."
            return false
        }

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

    private static func bundleHasFamilyControlsEntitlement() -> Bool {
        // Codesigned entitlements are not always in Info.plist; probe via task-for-pid style
        // is unavailable on iOS. Instead, attempt a zero-cost status read — missing entitlement
        // yields `.notDetermined` and requestAuthorization fails. We also check the embedded
        // mobileprovision / entitlements blob when present (Debug & enterprise).
        if let path = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision"),
           let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
           let xml = String(data: data, encoding: .ascii),
           xml.contains("com.apple.developer.family-controls") {
            return true
        }
        #if DEBUG
        return true
        #else
        // Release/TestFlight ships without the entitlement until distribution is approved.
        return false
        #endif
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
