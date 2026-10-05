import FirebaseFunctions
import Foundation

/// Production pairing client via Cloud Functions.
/// Mirrors Android `FirebasePairingClient`.
public final class FirebasePairingClient: @unchecked Sendable, PairingClientProtocol {
    public static let shared = FirebasePairingClient()

    private let functions: Functions

    public init(functions: Functions = Functions.functions(region: "us-central1")) {
        self.functions = functions
    }

    public func createPairingToken(childId: String) async throws -> PairingTokenInfo {
        try await createPairingToken(childId: childId, familyId: nil)
    }

    public func createPairingToken(childId: String, familyId: String?) async throws -> PairingTokenInfo {
        var payload: [String: Any] = [
            "childId": childId,
        ]
        if let familyId, !familyId.isEmpty, familyId != "sample_family" {
            payload["familyId"] = familyId
        }
        let result = try await functions.httpsCallable("createPairingToken").call(payload)
        let map = result.data as? [String: Any] ?? [:]
        return PairingTokenInfo(
            code: try requiredString(map, "code"),
            secret: try requiredString(map, "secret"),
            expiresAtEpochMs: requiredInt64(map, "expiresAtEpochMs"),
            qrPayload: try requiredString(map, "qrPayload"),
            childId: try requiredString(map, "childId"),
            familyId: try requiredString(map, "familyId")
        )
    }

    public func consumePairingToken(
        code: String,
        deviceId: String,
        secret: String?
    ) async throws -> ChildPairingResult {
        var payload: [String: Any] = [
            "code": code,
            "deviceId": deviceId,
            "platform": "ios",
        ]
        if let secret, !secret.isEmpty {
            payload["secret"] = secret
        }
        let result = try await functions.httpsCallable("consumePairingToken").call(payload)
        let map = result.data as? [String: Any] ?? [:]
        return ChildPairingResult(
            customToken: try requiredString(map, "customToken"),
            familyId: try requiredString(map, "familyId"),
            childId: try requiredString(map, "childId"),
            deviceId: try requiredString(map, "deviceId"),
            parentPinHash: map["parentPinHash"] as? String ?? "",
            displayName: map["displayName"] as? String ?? "",
            ageBand: map["ageBand"] as? String ?? ""
        )
    }

    private func requiredString(_ map: [String: Any], _ key: String) throws -> String {
        guard let value = map[key] as? String, !value.isEmpty else {
            throw AppError.unknown("Pairing response missing \(key).")
        }
        return value
    }

    private func requiredInt64(_ map: [String: Any], _ key: String) -> Int64 {
        if let number = map[key] as? NSNumber { return number.int64Value }
        if let value = map[key] as? Int64 { return value }
        if let value = map[key] as? Int { return Int64(value) }
        if let value = map[key] as? String, let parsed = Int64(value) { return parsed }
        return 0
    }
}
