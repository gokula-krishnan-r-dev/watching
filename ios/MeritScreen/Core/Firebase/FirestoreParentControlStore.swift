import FirebaseFirestore
import FirebaseFunctions
import Foundation

/// Production parent control store — same Firestore paths as Android `FirestoreParentControlStore`.
public actor FirestoreParentControlStore: ParentControlStoreProtocol {
    public static let shared = FirestoreParentControlStore()

    private let db: Firestore
    private let functions: Functions

    public init(
        db: Firestore = Firestore.firestore(),
        functions: Functions = Functions.functions(region: "us-central1")
    ) {
        self.db = db
        self.functions = functions
    }

    private func childRef(familyId: String, childId: String) -> DocumentReference {
        db.collection("families").document(familyId)
            .collection("children").document(childId)
    }

    public func getFamilyMeta(familyId: String) async throws -> FamilyMeta? {
        let snap = try await db.collection("families").document(familyId).getDocument()
        guard snap.exists else { return nil }
        return FamilyMeta(
            familyId: familyId,
            name: (snap.get("name") as? String) ?? "",
            ownerUid: (snap.get("ownerUid") as? String) ?? ""
        )
    }

    public func listChildren(familyId: String) async throws -> [FamilyChildProfile] {
        let snap = try await db.collection("families").document(familyId)
            .collection("children").getDocuments()
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

    public func addChild(familyId: String, child: FamilyDraftChild) async throws -> FamilyChildProfile {
        let existing = try await listChildren(familyId: familyId)
        if existing.count >= AppConfig.maxChildrenPerParent {
            throw AppError.validation("You can add up to \(AppConfig.maxChildrenPerParent) children for now.")
        }
        let childId = child.localId.isEmpty ? UUID().uuidString.lowercased() : child.localId
        let profile = FamilyChildProfile(
            childId: childId,
            displayName: child.name.trimmingCharacters(in: .whitespacesAndNewlines),
            ageBand: child.ageBand,
            avatar: child.avatar,
            language: child.language.isEmpty ? "en" : child.language
        )
        let ref = childRef(familyId: familyId, childId: childId)
        let batch = db.batch()
        batch.setData([
            "displayName": profile.displayName,
            "ageBand": profile.ageBand.firestoreName,
            "avatarId": profile.avatar.rawValue,
            "language": profile.language,
            "createdAt": FieldValue.serverTimestamp(),
        ], forDocument: ref)
        var policyMap = ChildPolicyMapper.toMap(ChildPolicy())
        policyMap["updatedAt"] = FieldValue.serverTimestamp()
        batch.setData(policyMap, forDocument: ref.collection("policy").document("current"))
        try await batch.commit()
        return profile
    }

    public func updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild
    ) async throws -> FamilyChildProfile {
        let profile = FamilyChildProfile(
            childId: childId,
            displayName: child.name.trimmingCharacters(in: .whitespacesAndNewlines),
            ageBand: child.ageBand,
            avatar: child.avatar,
            language: child.language.isEmpty ? "en" : child.language
        )
        try await childRef(familyId: familyId, childId: childId).setData([
            "displayName": profile.displayName,
            "ageBand": profile.ageBand.firestoreName,
            "avatarId": profile.avatar.rawValue,
            "language": profile.language,
            "updatedAt": FieldValue.serverTimestamp(),
        ], merge: true)
        return profile
    }

    public func deleteChild(familyId: String, childId: String) async throws {
        _ = try await functions.httpsCallable("deleteChild").call([
            "familyId": familyId,
            "childId": childId,
        ])
    }

    public func getPolicy(familyId: String, childId: String) async throws -> ChildPolicy {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("policy").document("current").getDocument()
        guard snap.exists, let data = snap.data() else { return ChildPolicy() }
        return ChildPolicyMapper.fromMap(data)
    }

    public func updatePolicy(familyId: String, childId: String, policy: ChildPolicy) async throws {
        var map = ChildPolicyMapper.toMap(policy)
        map["updatedAt"] = FieldValue.serverTimestamp()
        try await childRef(familyId: familyId, childId: childId)
            .collection("policy").document("current")
            .setData(map)
    }

    public func setChildPaused(familyId: String, childId: String, paused: Bool) async throws {
        try await childRef(familyId: familyId, childId: childId)
            .collection("policy").document("current")
            .setData([
                "paused": paused,
                "updatedAt": FieldValue.serverTimestamp(),
            ], merge: true)
    }

    public func addBonusTime(familyId: String, childId: String, bonusMinutes: Int) async throws {
        var current = try await getPolicy(familyId: familyId, childId: childId)
        let ceiling = (current.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) + bonusMinutes
        current.dailyCeilingMinutes = ceiling
        current.bonusMinutesToday += bonusMinutes
        try await updatePolicy(familyId: familyId, childId: childId, policy: current)
    }

    public func listAppRules(familyId: String, childId: String) async throws -> [AppRule] {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("appRules").getDocuments()
        return snap.documents.map { doc in
            AppRule(
                appId: doc.documentID,
                packageOrBundleId: (doc.get("packageOrBundleId") as? String) ?? doc.documentID,
                displayName: (doc.get("displayName") as? String) ?? "",
                allowed: (doc.get("allowed") as? Bool) ?? true,
                blockMinutes: intValue(doc.get("blockMinutes"), AppConfig.defaultBlockMinutes),
                grantOnPassMinutes: intValue(
                    doc.get("grantOnPassMinutes") ?? doc.get("blockMinutes"),
                    AppConfig.defaultBlockMinutes
                ),
                cooldownMinutes: intValue(doc.get("cooldownMinutes"), AppConfig.defaultCooldownMinutes),
                isEmergency: (doc.get("isEmergency") as? Bool) ?? false
            )
        }.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    public func upsertAppRule(familyId: String, childId: String, rule: AppRule) async throws {
        let id = rule.appId.isEmpty
            ? rule.packageOrBundleId.replacingOccurrences(of: ".", with: "_")
            : rule.appId
        try await childRef(familyId: familyId, childId: childId)
            .collection("appRules").document(id)
            .setData([
                "packageOrBundleId": rule.packageOrBundleId.trimmingCharacters(in: .whitespacesAndNewlines),
                "displayName": rule.displayName.trimmingCharacters(in: .whitespacesAndNewlines),
                "allowed": rule.allowed,
                "blockMinutes": min(240, max(5, rule.blockMinutes)),
                "grantOnPassMinutes": min(240, max(5, rule.grantOnPassMinutes)),
                "cooldownMinutes": min(180, max(1, rule.cooldownMinutes)),
                "isEmergency": rule.isEmergency,
                "updatedAt": FieldValue.serverTimestamp(),
            ])
    }

    public func deleteAppRule(familyId: String, childId: String, appId: String) async throws {
        try await childRef(familyId: familyId, childId: childId)
            .collection("appRules").document(appId).delete()
    }

    public func listUsageDays(familyId: String, childId: String, limit: Int) async throws -> [UsageDaySummary] {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("usageDays").getDocuments()
        let days: [UsageDaySummary] = snap.documents.compactMap { doc in
            let minutes = intValue(doc.get("minutesUsed"), 0)
            let byApp = (doc.get("minutesByApp") as? [String: Any])?.compactMapValues { value -> Int? in
                if let number = value as? NSNumber { return number.intValue }
                return value as? Int
            } ?? [:]
            return UsageDaySummary(day: doc.documentID, minutesUsed: minutes, minutesByApp: byApp)
        }
        return Array(days.sorted { $0.day > $1.day }.prefix(limit))
    }

    public func listQuizAttempts(familyId: String, childId: String, limit: Int) async throws -> [QuizAttemptSummary] {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("quizAttempts")
            .order(by: "createdAt", descending: true)
            .limit(to: limit)
            .getDocuments()
        return snap.documents.map { doc in
            let createdMs: Int64
            if let ts = doc.get("createdAt") as? Timestamp {
                createdMs = Int64(ts.dateValue().timeIntervalSince1970 * 1000)
            } else {
                createdMs = 0
            }
            return QuizAttemptSummary(
                attemptId: doc.documentID,
                score: intValue(doc.get("score"), 0),
                total: intValue(doc.get("total"), 0),
                passed: (doc.get("passed") as? Bool) ?? false,
                extraMinutesGranted: intValue(doc.get("extraMinutes"), 0),
                topics: (doc.get("topics") as? [String]) ?? [],
                createdAtEpochMs: createdMs
            )
        }
    }

    public func getSkillState(familyId: String, childId: String) async throws -> [TopicSkillSummary] {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("skillState").document("current").getDocument()
        guard snap.exists, let data = snap.data() else { return [] }
        let topics = data["topics"] as? [String: Any] ?? data
        return topics.compactMap { key, value -> TopicSkillSummary? in
            guard let map = value as? [String: Any] else { return nil }
            return TopicSkillSummary(
                topic: key,
                level: intValue(map["level"], 1),
                streakCorrect: intValue(map["streakCorrect"], 0),
                weak: (map["weak"] as? Bool) ?? false,
                weakConcepts: (map["weakConcepts"] as? [String]) ?? [],
                masteredConcepts: (map["masteredConcepts"] as? [String]) ?? [],
                tierLabel: (map["tierLabel"] as? String) ?? "basic"
            )
        }
    }

    public func listDevices(familyId: String, childId: String) async throws -> [DeviceSummary] {
        let snap = try await childRef(familyId: familyId, childId: childId)
            .collection("devices").getDocuments()
        return snap.documents.map { doc in
            let lastSeenMs: Int64?
            if let ts = doc.get("lastSeenAt") as? Timestamp {
                lastSeenMs = Int64(ts.dateValue().timeIntervalSince1970 * 1000)
            } else if let number = doc.get("lastSeenAtEpochMs") as? NSNumber {
                lastSeenMs = number.int64Value
            } else {
                lastSeenMs = nil
            }
            return DeviceSummary(
                deviceId: doc.documentID,
                model: (doc.get("model") as? String) ?? "iPhone",
                platform: (doc.get("platform") as? String) ?? "ios",
                osVersion: (doc.get("osVersion") as? String) ?? "",
                appVersion: (doc.get("appVersion") as? String) ?? "",
                batteryPercent: intOptional(doc.get("batteryPercent")),
                revoked: (doc.get("revoked") as? Bool) ?? false,
                lastSeenAtEpochMs: lastSeenMs,
                pairedAtEpochMs: nil
            )
        }
    }

    public func setDeviceRevoked(
        familyId: String,
        childId: String,
        deviceId: String,
        revoked: Bool
    ) async throws {
        try await childRef(familyId: familyId, childId: childId)
            .collection("devices").document(deviceId)
            .setData([
                "revoked": revoked,
                "updatedAt": FieldValue.serverTimestamp(),
            ], merge: true)
    }

    public func listInstalledApps(familyId: String, childId: String) async throws -> [InstalledAppSummary] {
        let devices = try await listDevices(familyId: familyId, childId: childId)
        var apps: [InstalledAppSummary] = []
        for device in devices where !device.revoked {
            if let snap = try? await childRef(familyId: familyId, childId: childId)
                .collection("devices").document(device.deviceId)
                .collection("installedApps").getDocuments() {
                for doc in snap.documents {
                    let package = (doc.get("packageName") as? String) ?? doc.documentID
                    apps.append(
                        InstalledAppSummary(
                            packageName: package,
                            appName: (doc.get("label") as? String) ?? package,
                            category: (doc.get("category") as? String) ?? ""
                        )
                    )
                }
            }
            // Android also stores a flat inventory array on the device doc.
            if let devDoc = try? await childRef(familyId: familyId, childId: childId)
                .collection("devices").document(device.deviceId).getDocument(),
               let inventory = devDoc.get("installedApps") as? [[String: Any]] {
                for item in inventory {
                    guard let package = item["packageName"] as? String else { continue }
                    apps.append(
                        InstalledAppSummary(
                            packageName: package,
                            appName: (item["label"] as? String) ?? package,
                            category: (item["category"] as? String) ?? ""
                        )
                    )
                }
            }
        }
        var seen = Set<String>()
        return apps.filter { seen.insert($0.packageName).inserted }
            .sorted { $0.appName.localizedCaseInsensitiveCompare($1.appName) == .orderedAscending }
    }

    public func deleteFamily(familyId: String) async throws {
        _ = try await functions.httpsCallable("deleteFamily").call([
            "familyId": familyId,
        ])
    }

    private func intValue(_ raw: Any?, _ defaultValue: Int) -> Int {
        if let number = raw as? NSNumber { return number.intValue }
        if let value = raw as? Int { return value }
        return defaultValue
    }

    private func intOptional(_ raw: Any?) -> Int? {
        if raw == nil { return nil }
        return intValue(raw, 0)
    }
}
