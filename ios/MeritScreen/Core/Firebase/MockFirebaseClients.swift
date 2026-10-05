import Foundation

/// Mock implementation of AuthClientProtocol for tests and initial bootstrap.
public final class MockAuthClient: @unchecked Sendable, AuthClientProtocol {
    public var currentUser: AuthUser?

    public init(currentUser: AuthUser? = nil) {
        self.currentUser = currentUser
    }

    public func sendEmailOtp(email: String) async throws {
        // Simulated network latency
        try await Task.sleep(nanoseconds: 200_000_000)
    }

    public func verifyEmailOtp(email: String, code: String) async throws -> AuthUser {
        try await Task.sleep(nanoseconds: 200_000_000)
        let user = AuthUser(uid: "mock_user_\(UUID().uuidString.prefix(6))", email: email)
        self.currentUser = user
        return user
    }

    public func signInWithCustomToken(_ customToken: String) async throws -> AuthUser {
        try await Task.sleep(nanoseconds: 50_000_000)
        let user = AuthUser(uid: "mock_custom_\(UUID().uuidString.prefix(6))", email: nil)
        self.currentUser = user
        return user
    }

    public func signOut() throws {
        self.currentUser = nil
    }
}

/// Mock implementation of PairingClientProtocol for tests and initial bootstrap.
public final class MockPairingClient: @unchecked Sendable, PairingClientProtocol {
    public init() {}

    public func createPairingToken(childId: String) async throws -> PairingTokenInfo {
        let code = "123456"
        let secret = UUID().uuidString
        let expires = Int64(Date().addingTimeInterval(600).timeIntervalSince1970 * 1000)
        return PairingTokenInfo(
            code: code,
            secret: secret,
            expiresAtEpochMs: expires,
            qrPayload: "meritscreen://pair?c=\(code)&s=\(secret)",
            childId: childId,
            familyId: "mock_family_1"
        )
    }

    public func consumePairingToken(
        code: String,
        deviceId: String,
        secret: String?
    ) async throws -> ChildPairingResult {
        return ChildPairingResult(
            customToken: "mock_token_\(UUID().uuidString)",
            familyId: "mock_family_1",
            childId: "mock_child_1",
            deviceId: deviceId,
            parentPinHash: Pbkdf2PinHasher.shared.hash(pin: "1234"),
            displayName: "Leo",
            ageBand: "7_9"
        )
    }
}

/// Mock implementation of FamilyStoreProtocol for tests.
public final class MockFamilyStore: @unchecked Sendable, FamilyStoreProtocol {
    public var userFamilyId: String?
    public var children: [FamilyChildProfile] = []
    public var updatedPinHash: String?
    public var createdFamily: CreatedFamily?
    public var devices: [FamilyDeviceSummary] = []

    public init(userFamilyId: String? = nil, children: [FamilyChildProfile] = []) {
        self.userFamilyId = userFamilyId
        self.children = children
    }

    public func getUserFamilyId(uid: String) async throws -> String? {
        return userFamilyId
    }

    public func listChildren(familyId: String) async throws -> [FamilyChildProfile] {
        return children
    }

    public func createFamilyFromDraft(
        uid: String,
        email: String?,
        draft: FamilyDraft
    ) async throws -> CreatedFamily {
        let familyId = "fam_\(UUID().uuidString.prefix(8))"
        let kids = draft.children.map {
            FamilyChildProfile(
                childId: $0.localId.isEmpty ? "child_\(UUID().uuidString.prefix(6))" : $0.localId,
                displayName: $0.name,
                ageBand: $0.ageBand,
                avatar: $0.avatar,
                language: $0.language
            )
        }
        let result = CreatedFamily(familyId: familyId, children: kids)
        self.createdFamily = result
        self.userFamilyId = familyId
        self.children = kids
        return result
    }

    public func updateParentPinHash(familyId: String, pinHash: String) async throws {
        self.updatedPinHash = pinHash
    }

    public func getParentPinHash(familyId: String) async throws -> String? {
        return updatedPinHash ?? "mock_parent_pin_hash"
    }

    public func listDevices(familyId: String, childId: String) async throws -> [FamilyDeviceSummary] {
        return devices
    }
}

