import Foundation
import FirebaseFirestore

/// Production Firestore implementation of `ParentDeviceRegistryClientProtocol`.
/// Writes to `families/{familyId}/parentDevices/{installationId}`.
public final class FirestoreParentDeviceRegistryClient: @unchecked Sendable, ParentDeviceRegistryClientProtocol {
    public static let shared = FirestoreParentDeviceRegistryClient()

    private let firestore: Firestore

    public init(firestore: Firestore = Firestore.firestore()) {
        self.firestore = firestore
    }

    public func registerParentDevice(
        familyId: String,
        installationId: String,
        info: ParentDeviceInfo
    ) async throws {
        let prefsMap: [String: Any] = [
            "dailySummary": info.notificationPrefs.dailySummary,
            "timeUp": info.notificationPrefs.timeUp,
            "quizFailedRepeatedly": info.notificationPrefs.quizFailedRepeatedly,
            "quizPassedOptional": info.notificationPrefs.quizPassedOptional,
            "deviceOffline": info.notificationPrefs.deviceOffline,
            "pairingEvents": info.notificationPrefs.pairingEvents,
            "launcherLost": info.notificationPrefs.launcherLost
        ]

        let payload: [String: Any] = [
            "uid": info.uid,
            "fcmToken": String(info.fcmToken.prefix(512)),
            "fcmTokenUpdatedAt": FieldValue.serverTimestamp(),
            "platform": info.platform,
            "appVersion": String(info.appVersion.prefix(32)),
            "model": String(info.model.prefix(64)),
            "notificationPrefs": prefsMap,
            "updatedAt": FieldValue.serverTimestamp()
        ]

        try await firestore.collection("families")
            .document(familyId)
            .collection("parentDevices")
            .document(installationId)
            .setData(payload, merge: true)
    }
}
