import Foundation
import UIKit

/// Registers the parent's device token under `families/{familyId}/parentDevices/{installationId}`.
/// Allows Cloud Functions to multicast awareness pushes (quiz fails, time up, daily summary) to parent phones.
public final class ParentPushTokenRegistrar: @unchecked Sendable {
    public static let shared = ParentPushTokenRegistrar()

    private let sessionRepo: ParentSessionRepository
    private let tokenProvider: PushTokenProviderProtocol
    private let registryClient: ParentDeviceRegistryClientProtocol
    private let defaults: UserDefaults

    private let installationIdKey = "meritscreen.parent_installation_id"
    private let lastTokenKey = "meritscreen.last_registered_parent_token"

    public init(
        sessionRepo: ParentSessionRepository = .shared,
        tokenProvider: PushTokenProviderProtocol = FirebasePushTokenProvider.shared,
        registryClient: ParentDeviceRegistryClientProtocol = FirestoreParentDeviceRegistryClient.shared,
        defaults: UserDefaults = .standard
    ) {
        self.sessionRepo = sessionRepo
        self.tokenProvider = tokenProvider
        self.registryClient = registryClient
        self.defaults = defaults
    }

    public func getOrCreateInstallationId() -> String {
        if let existing = defaults.string(forKey: installationIdKey), !existing.isEmpty {
            return existing
        }
        let fresh = UUID().uuidString
        defaults.set(fresh, forKey: installationIdKey)
        return fresh
    }

    @discardableResult
    public func registerParentTokenIfNeeded(
        notificationPrefs: ParentNotificationPrefs = ParentNotificationPrefs(),
        force: Bool = false
    ) async -> Bool {
        guard let session = sessionRepo.current() else {
            return false
        }

        do {
            guard let token = try await tokenProvider.currentToken(), !token.isEmpty else {
                return false
            }

            let lastToken = defaults.string(forKey: lastTokenKey)
            if !force && lastToken == token {
                return true
            }

            let installationId = getOrCreateInstallationId()
            let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
            let model = await MainActor.run { UIDevice.current.model }

            let info = ParentDeviceInfo(
                uid: session.uid,
                fcmToken: token,
                platform: "ios",
                appVersion: appVersion,
                model: model,
                notificationPrefs: notificationPrefs
            )

            try await registryClient.registerParentDevice(
                familyId: session.familyId,
                installationId: installationId,
                info: info
            )

            defaults.set(token, forKey: lastTokenKey)
            print("[ParentPushTokenRegistrar] Successfully registered parent device \(installationId)")
            return true
        } catch {
            print("[ParentPushTokenRegistrar] Failed to register parent push token: \(error.localizedDescription)")
            return false
        }
    }
}
