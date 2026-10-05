import FirebaseFirestore
import Foundation

/// Production FamilyStore backed by Firestore (same schema as Android).
public actor FirestoreFamilyStore: FamilyStoreProtocol {
    public static let shared = FirestoreFamilyStore()

    private let db: Firestore

    public init(db: Firestore = Firestore.firestore()) {
        self.db = db
    }

    public func getUserFamilyId(uid: String) async throws -> String? {
        if let snap = try? await db.collection("users").document(uid).getDocument(), snap.exists {
            if let familyId = snap.get("familyId") as? String,
               let clean = familyId.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
               clean != "sample_family" {
                return clean
            }
        }

        // Fallback: Check if user owns an existing family in Firestore
        let ownedSnap = try await db.collection("families")
            .whereField("ownerUid", isEqualTo: uid)
            .limit(to: 1)
            .getDocuments()

        if let doc = ownedSnap.documents.first {
            let famId = doc.documentID
            try? await db.collection("users").document(uid).setData(["familyId": famId], merge: true)
            return famId
        }

        return nil
    }

    public func listChildren(familyId: String) async throws -> [FamilyChildProfile] {
        let snap = try await db.collection("families").document(familyId)
            .collection("children")
            .getDocuments()
        return snap.documents.compactMap { doc in
            guard let name = doc.get("displayName") as? String else { return nil }
            return FamilyChildProfile(
                childId: doc.documentID,
                displayName: name,
                ageBand: AgeBand.fromStorage(doc.get("ageBand") as? String),
                avatar: AvatarPreset(rawValue: (doc.get("avatarId") as? String) ?? "") ?? .default,
                language: (doc.get("language") as? String) ?? "en"
            )
        }
    }

    public func createFamilyFromDraft(
        uid: String,
        email: String?,
        draft: FamilyDraft
    ) async throws -> CreatedFamily {
        let familyId = UUID().uuidString.lowercased()
        let children = draft.children.map { child in
            FamilyChildProfile(
                childId: child.localId.isEmpty ? UUID().uuidString.lowercased() : child.localId,
                displayName: child.name,
                ageBand: child.ageBand,
                avatar: child.avatar,
                language: child.language
            )
        }

        let batch = db.batch()
        let familyRef = db.collection("families").document(familyId)
        batch.setData([
            "ownerUid": uid,
            "name": draft.familyName,
            "parentPinHash": draft.parentPinHash,
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp(),
        ], forDocument: familyRef)

        batch.setData([
            "role": "owner",
            "joinedAt": FieldValue.serverTimestamp(),
        ], forDocument: familyRef.collection("members").document(uid))

        batch.setData([
            "familyId": familyId,
            "email": email ?? "",
            "role": "parent",
            "updatedAt": FieldValue.serverTimestamp(),
            "createdAt": FieldValue.serverTimestamp(),
        ], forDocument: db.collection("users").document(uid), merge: true)

        for child in children {
            let childRef = familyRef.collection("children").document(child.childId)
            batch.setData([
                "displayName": child.displayName,
                "ageBand": child.ageBand.firestoreName,
                "avatarId": child.avatar.rawValue,
                "language": child.language,
                "createdAt": FieldValue.serverTimestamp(),
            ], forDocument: childRef)

            var policyMap = ChildPolicyMapper.toMap(ChildPolicy())
            policyMap["updatedAt"] = FieldValue.serverTimestamp()
            batch.setData(policyMap, forDocument: childRef.collection("policy").document("current"))
        }

        try await batch.commit()
        return CreatedFamily(familyId: familyId, children: children)
    }

    public func updateParentPinHash(familyId: String, pinHash: String) async throws {
        try await db.collection("families").document(familyId).updateData([
            "parentPinHash": pinHash,
            "updatedAt": FieldValue.serverTimestamp(),
        ])
    }

    public func getParentPinHash(familyId: String) async throws -> String? {
        let snap = try await db.collection("families").document(familyId).getDocument()
        return snap.data()?["parentPinHash"] as? String
    }

    public func listDevices(familyId: String, childId: String) async throws -> [FamilyDeviceSummary] {
        let snap = try await db.collection("families").document(familyId)
            .collection("children").document(childId)
            .collection("devices")
            .getDocuments()
        return snap.documents.map { doc in
            FamilyDeviceSummary(
                deviceId: doc.documentID,
                platform: (doc.get("platform") as? String) ?? "ios",
                revoked: (doc.get("revoked") as? Bool) ?? false,
                displayName: (doc.get("model") as? String) ?? doc.documentID
            )
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
