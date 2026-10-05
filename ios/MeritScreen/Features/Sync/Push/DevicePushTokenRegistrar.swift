import Foundation

/// Registers the child device's FCM push token to `families/{familyId}/children/{childId}/devices/{deviceId}`.
/// Mirrors `com.meritscreen.feature.devices.PushTokenRegistrationWorker` in Android.
public final class DevicePushTokenRegistrar: @unchecked Sendable {
    public static let shared = DevicePushTokenRegistrar()

    private let pairingStore: ChildPairingStore
    private let tokenProvider: PushTokenProviderProtocol
    private let registryClient: DeviceRegistryClientProtocol
    private let defaults: UserDefaults

    private let lastTokenKey = "meritscreen.last_registered_fcm_token"

    public init(
        pairingStore: ChildPairingStore = .shared,
        tokenProvider: PushTokenProviderProtocol = FirebasePushTokenProvider.shared,
        registryClient: DeviceRegistryClientProtocol = FirestoreDeviceRegistryClient.shared,
        defaults: UserDefaults = .standard
    ) {
        self.pairingStore = pairingStore
        self.tokenProvider = tokenProvider
        self.registryClient = registryClient
        self.defaults = defaults
    }

    /// Registers the current FCM push token if paired and token is available.
    @discardableResult
    public func registerPushTokenIfNeeded(force: Bool = false) async -> Bool {
        guard let credential = pairingStore.current() else {
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

            try await registryClient.registerPushToken(
                familyId: credential.familyId,
                childId: credential.childId,
                deviceId: credential.deviceId,
                token: token
            )

            defaults.set(token, forKey: lastTokenKey)
            print("[DevicePushTokenRegistrar] Successfully registered FCM token for device \(credential.deviceId)")
            return true
        } catch {
            print("[DevicePushTokenRegistrar] Failed to register push token: \(error.localizedDescription)")
            return false
        }
    }
}
