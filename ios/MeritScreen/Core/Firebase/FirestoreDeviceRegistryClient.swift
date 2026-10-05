import Foundation
import FirebaseFirestore

/// Production Firestore implementation of `DeviceRegistryClientProtocol`.
/// Writes strictly to this child device's own `devices/{deviceId}` document.
public final class FirestoreDeviceRegistryClient: @unchecked Sendable, DeviceRegistryClientProtocol {
    public static let shared = FirestoreDeviceRegistryClient()

    private let firestore: Firestore

    public init(firestore: Firestore = Firestore.firestore()) {
        self.firestore = firestore
    }

    private func deviceRef(familyId: String, childId: String, deviceId: String) -> DocumentReference {
        firestore.collection("families")
            .document(familyId)
            .collection("children")
            .document(childId)
            .collection("devices")
            .document(deviceId)
    }

    public func registerPushToken(
        familyId: String,
        childId: String,
        deviceId: String,
        token: String
    ) async throws {
        let cleanToken = String(token.prefix(512))
        let payload: [String: Any] = [
            "fcmToken": cleanToken,
            "fcmTokenUpdatedAt": FieldValue.serverTimestamp()
        ]
        try await deviceRef(familyId: familyId, childId: childId, deviceId: deviceId)
            .setData(payload, merge: true)
    }

    public func heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        info: DeviceHeartbeatInfo
    ) async throws {
        var payload: [String: Any] = [
            "lastSeenAt": FieldValue.serverTimestamp(),
            "launcherDefault": info.launcherDefault,
            "model": String(info.model.prefix(64))
        ]
        if let batt = info.batteryPercent, (0...100).contains(batt) {
            payload["batteryPercent"] = batt
        }
        if let os = info.osVersion, !os.isEmpty {
            payload["osVersion"] = String(os.prefix(32))
        }
        if let app = info.appVersion, !app.isEmpty {
            payload["appVersion"] = String(app.prefix(32))
        }

        try await deviceRef(familyId: familyId, childId: childId, deviceId: deviceId)
            .setData(payload, merge: true)
    }

    public func fetchStatus(
        familyId: String,
        childId: String,
        deviceId: String
    ) async throws -> RemoteDeviceStatus {
        let snapshot = try await deviceRef(familyId: familyId, childId: childId, deviceId: deviceId).getDocument()
        guard snapshot.exists else {
            return RemoteDeviceStatus(exists: false, revoked: false, launcherDefault: false)
        }
        let data = snapshot.data() ?? [:]
        let revoked = data["revoked"] as? Bool ?? false
        let launcherDefault = data["launcherDefault"] as? Bool ?? false
        return RemoteDeviceStatus(exists: true, revoked: revoked, launcherDefault: launcherDefault)
    }
}
