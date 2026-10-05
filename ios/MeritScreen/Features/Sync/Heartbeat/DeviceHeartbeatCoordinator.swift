import Foundation
import UIKit

/// Coordinates periodic device heartbeat and remote status (revocation killswitch) checks.
/// Mirrors `com.meritscreen.feature.devices.DeviceHeartbeatWorker` in Android.
public final class DeviceHeartbeatCoordinator: @unchecked Sendable {
    public static let shared = DeviceHeartbeatCoordinator()

    private let pairingStore: ChildPairingStore
    private let registryClient: DeviceRegistryClientProtocol
    private let sharedStore: ScreenTimeSharedStore
    private let tokenRegistrar: DevicePushTokenRegistrar

    public init(
        pairingStore: ChildPairingStore = .shared,
        registryClient: DeviceRegistryClientProtocol = FirestoreDeviceRegistryClient.shared,
        sharedStore: ScreenTimeSharedStore = .shared,
        tokenRegistrar: DevicePushTokenRegistrar = .shared
    ) {
        self.pairingStore = pairingStore
        self.registryClient = registryClient
        self.sharedStore = sharedStore
        self.tokenRegistrar = tokenRegistrar
    }

    /// Performs a single heartbeat check-in.
    /// Returns true if heartbeat succeeded and device is active, or false if offline or revoked.
    @discardableResult
    public func performHeartbeat() async -> Bool {
        guard let credential = pairingStore.current() else {
            return false
        }

        // Also ensure push token is registered
        await tokenRegistrar.registerPushTokenIfNeeded()

        let (model, batteryPercent, osVersion) = await MainActor.run { () -> (String, Int?, String) in
            let isBatteryEnabled = UIDevice.current.isBatteryMonitoringEnabled
            if !isBatteryEnabled {
                UIDevice.current.isBatteryMonitoringEnabled = true
            }
            let batteryLevel = UIDevice.current.batteryLevel
            let batteryPercent = batteryLevel >= 0 ? Int(batteryLevel * 100) : nil
            let osVersion = UIDevice.current.systemVersion
            let model = UIDevice.current.model
            return (model, batteryPercent, osVersion)
        }

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"

        let info = DeviceHeartbeatInfo(
            launcherDefault: false,
            model: model,
            batteryPercent: batteryPercent,
            osVersion: osVersion,
            appVersion: appVersion
        )

        do {
            try await registryClient.heartbeat(
                familyId: credential.familyId,
                childId: credential.childId,
                deviceId: credential.deviceId,
                info: info
            )

            // Inspect remote revocation status
            let remoteStatus = try await registryClient.fetchStatus(
                familyId: credential.familyId,
                childId: credential.childId,
                deviceId: credential.deviceId
            )

            if remoteStatus.revoked {
                SecureLogger.info("[DeviceHeartbeatCoordinator] Remote status indicates device is revoked. Triggering local killswitch.")
                await MainActor.run {
                    ChildSessionWiper.shared.wipeAllChildData(
                        pairingStore: pairingStore,
                        sharedStore: sharedStore
                    )
                }
                return false
            }

            return true
        } catch {
            SecureLogger.error("[DeviceHeartbeatCoordinator] Heartbeat failed or offline: \(error.localizedDescription)")
            return false
        }
    }
}
