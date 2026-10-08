import Foundation
import UserNotifications
import UIKit

public extension Notification.Name {
    static let childDeviceRevoked = Notification.Name("com.watching.notification.device_revoked")
    static let childFamilyDeleted = Notification.Name("com.watching.notification.family_deleted")
    static let parentDeepLinkRequested = Notification.Name("com.watching.notification.parent_deep_link")
}

/// Central router for dispatching incoming FCM / APNs remote notifications.
/// Deliberately keeps child hot-path local: only pulls into local stores in the background.
public final class PushNotificationRouter: @unchecked Sendable {
    public static let shared = PushNotificationRouter()

    private let policyPullCoordinator: ChildPolicyPullCoordinator
    private let pairingStore: ChildPairingStore
    private let sharedStore: ScreenTimeSharedStore
    private let familyStore: FamilyStoreProtocol

    public init(
        policyPullCoordinator: ChildPolicyPullCoordinator = .shared,
        pairingStore: ChildPairingStore = .shared,
        sharedStore: ScreenTimeSharedStore = .shared,
        familyStore: FamilyStoreProtocol = FirestoreFamilyStore.shared
    ) {
        self.policyPullCoordinator = policyPullCoordinator
        self.pairingStore = pairingStore
        self.sharedStore = sharedStore
        self.familyStore = familyStore
    }

    /// Handles an incoming parsed push message directly.
    @discardableResult
    public func routePushMessage(_ message: PushMessage) async -> UIBackgroundFetchResult {
        switch message.type {
        case .policySync:
            let success = await policyPullCoordinator.pullLatestPolicy()
            return success ? .newData : .noData

        case .deviceRevoked:
            await handleDeviceRevoked()
            return .newData

        case .pinSync:
            await handlePinSync(familyId: message.familyId)
            return .newData

        case .familyDeleted:
            await handleFamilyDeleted()
            return .newData

        case .parentChildPaired,
             .parentTimeUp,
             .parentFailLock,
             .parentQuizFailStreak,
             .parentQuizPassed,
             .parentDailySummary,
             .parentDeviceOffline,
             .parentLauncherLost:
            handleParentAwareness(message: message)
            return .newData

        case .unknown:
            return .noData
        }
    }

    /// Handles an incoming remote notification payload dictionary.
    @discardableResult
    public func handleRemoteNotification(
        userInfo: [AnyHashable: Any]
    ) async -> UIBackgroundFetchResult {
        let message = PushMessage.parse(from: userInfo)
        return await routePushMessage(message)
    }

    // MARK: - Control Plane Handlers

    private func handleDeviceRevoked() async {
        SecureLogger.info("[PushNotificationRouter] Device revocation push received. Executing immediate unpair killswitch.")
        await MainActor.run {
            ChildSessionWiper.shared.wipeAllChildData(
                pairingStore: pairingStore,
                sharedStore: sharedStore
            )
        }
    }

    private func handlePinSync(familyId: String?) async {
        guard let fid = familyId ?? pairingStore.current()?.familyId else { return }
        do {
            if let pinHash = try await familyStore.getParentPinHash(familyId: fid) {
                if let currentCred = pairingStore.current() {
                    let updated = ChildPairingCredential(
                        familyId: currentCred.familyId,
                        childId: currentCred.childId,
                        deviceId: currentCred.deviceId,
                        parentPinHash: pinHash,
                        displayName: currentCred.displayName,
                        ageBand: currentCred.ageBand
                    )
                    pairingStore.set(updated)
                    SecureLogger.info("[PushNotificationRouter] Successfully refreshed parentPinHash via pin_sync push")
                }
            }
        } catch {
            SecureLogger.error("[PushNotificationRouter] Failed to sync PIN hash: \(error.localizedDescription)")
        }
    }

    private func handleFamilyDeleted() async {
        SecureLogger.info("[PushNotificationRouter] Family deleted push received. Wiping child session.")
        await MainActor.run {
            ChildSessionWiper.shared.wipeAllChildData(
                pairingStore: pairingStore,
                sharedStore: sharedStore
            )
            NotificationCenter.default.post(name: .childFamilyDeleted, object: nil)
        }
    }

    // MARK: - Awareness Plane Handlers

    private func handleParentAwareness(message: PushMessage) {
        if let route = message.route {
            NotificationCenter.default.post(
                name: .parentDeepLinkRequested,
                object: nil,
                userInfo: ["route": route, "childId": message.childId ?? ""]
            )
        }
    }
}
