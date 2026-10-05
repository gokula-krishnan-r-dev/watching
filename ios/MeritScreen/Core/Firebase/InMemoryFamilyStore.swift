import Foundation

/// In-memory FamilyStore for unit tests.
public actor InMemoryFamilyStore: FamilyStoreProtocol {
    public static let shared = InMemoryFamilyStore()

    private var inMemoryUserFamilies: [String: String] = [:] // uid -> familyId
    private var inMemoryFamilies: [String: FamilyDraft] = [:] // familyId -> draft
    private var inMemoryChildren: [String: [FamilyChildProfile]] = [:] // familyId -> children
    private var inMemoryDevices: [String: [FamilyDeviceSummary]] = [:] // childId -> devices

    public init() {}

    public func getUserFamilyId(uid: String) async throws -> String? {
        return inMemoryUserFamilies[uid]
    }

    public func listChildren(familyId: String) async throws -> [FamilyChildProfile] {
        return inMemoryChildren[familyId] ?? []
    }

    public func createFamilyFromDraft(
        uid: String,
        email: String?,
        draft: FamilyDraft
    ) async throws -> CreatedFamily {
        let familyId = UUID().uuidString.lowercased()
        let children = draft.children.map { draftChild in
            FamilyChildProfile(
                childId: draftChild.localId.isEmpty ? UUID().uuidString.lowercased() : draftChild.localId,
                displayName: draftChild.name,
                ageBand: draftChild.ageBand,
                avatar: draftChild.avatar,
                language: draftChild.language
            )
        }

        inMemoryUserFamilies[uid] = familyId
        inMemoryFamilies[familyId] = draft
        inMemoryChildren[familyId] = children

        return CreatedFamily(familyId: familyId, children: children)
    }

    public func updateParentPinHash(familyId: String, pinHash: String) async throws {
        if let draft = inMemoryFamilies[familyId] {
            inMemoryFamilies[familyId] = FamilyDraft(
                familyName: draft.familyName,
                parentPinHash: pinHash,
                children: draft.children
            )
        }
    }

    public func getParentPinHash(familyId: String) async throws -> String? {
        return inMemoryFamilies[familyId]?.parentPinHash
    }

    public func listDevices(familyId: String, childId: String) async throws -> [FamilyDeviceSummary] {
        return inMemoryDevices[childId] ?? []
    }

    // Helper for testing and pairing simulation
    public func registerDevice(childId: String, device: FamilyDeviceSummary) {
        var list = inMemoryDevices[childId] ?? []
        list.append(device)
        inMemoryDevices[childId] = list
    }
}
