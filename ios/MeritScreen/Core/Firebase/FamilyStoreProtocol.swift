import Foundation

public struct FamilyChildProfile: Sendable, Equatable, Identifiable {
    public var id: String { childId }
    public let childId: String
    public let displayName: String
    public let ageBand: AgeBand
    public let avatar: AvatarPreset
    public let language: String

    public init(
        childId: String,
        displayName: String,
        ageBand: AgeBand,
        avatar: AvatarPreset,
        language: String = "en"
    ) {
        self.childId = childId
        self.displayName = displayName
        self.ageBand = ageBand
        self.avatar = avatar
        self.language = language
    }
}

public struct CreatedFamily: Sendable, Equatable {
    public let familyId: String
    public let children: [FamilyChildProfile]

    public init(familyId: String, children: [FamilyChildProfile]) {
        self.familyId = familyId
        self.children = children
    }
}

public struct FamilyDraftChild: Sendable, Equatable {
    public let localId: String
    public let name: String
    public let ageBand: AgeBand
    public let avatar: AvatarPreset
    public let language: String

    public init(
        localId: String,
        name: String,
        ageBand: AgeBand,
        avatar: AvatarPreset,
        language: String = "en"
    ) {
        self.localId = localId
        self.name = name
        self.ageBand = ageBand
        self.avatar = avatar
        self.language = language
    }
}

public struct FamilyDraft: Sendable, Equatable {
    public let familyName: String
    public let parentPinHash: String
    public let children: [FamilyDraftChild]

    public init(familyName: String, parentPinHash: String, children: [FamilyDraftChild]) {
        self.familyName = familyName
        self.parentPinHash = parentPinHash
        self.children = children
    }
}

public struct FamilyDeviceSummary: Sendable, Equatable, Identifiable {
    public var id: String { deviceId }
    public let deviceId: String
    public let platform: String
    public let revoked: Bool
    public let displayName: String

    public init(deviceId: String, platform: String, revoked: Bool, displayName: String) {
        self.deviceId = deviceId
        self.platform = platform
        self.revoked = revoked
        self.displayName = displayName
    }
}

/// Protocol abstracting family and children cloud persistence in Firestore.
public protocol FamilyStoreProtocol: Sendable {
    func getUserFamilyId(uid: String) async throws -> String?
    func listChildren(familyId: String) async throws -> [FamilyChildProfile]
    func createFamilyFromDraft(uid: String, email: String?, draft: FamilyDraft) async throws -> CreatedFamily
    func updateParentPinHash(familyId: String, pinHash: String) async throws
    func getParentPinHash(familyId: String) async throws -> String?
    func listDevices(familyId: String, childId: String) async throws -> [FamilyDeviceSummary]
}
