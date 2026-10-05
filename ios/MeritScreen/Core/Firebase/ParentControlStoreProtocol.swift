import Foundation

public struct FamilyMeta: Sendable, Equatable {
    public let familyId: String
    public let name: String
    public let ownerUid: String

    public init(familyId: String, name: String, ownerUid: String) {
        self.familyId = familyId
        self.name = name
        self.ownerUid = ownerUid
    }
}

public struct AppRule: Sendable, Equatable, Identifiable, Codable {
    public var id: String { appId }
    public let appId: String
    public let packageOrBundleId: String
    public var displayName: String
    public var allowed: Bool
    public var blockMinutes: Int
    public var grantOnPassMinutes: Int
    public var cooldownMinutes: Int
    public var isEmergency: Bool

    public init(
        appId: String,
        packageOrBundleId: String,
        displayName: String,
        allowed: Bool = true,
        blockMinutes: Int = AppConfig.defaultBlockMinutes,
        grantOnPassMinutes: Int = AppConfig.defaultBlockMinutes,
        cooldownMinutes: Int = AppConfig.defaultCooldownMinutes,
        isEmergency: Bool = false
    ) {
        self.appId = appId
        self.packageOrBundleId = packageOrBundleId
        self.displayName = displayName
        self.allowed = allowed
        self.blockMinutes = blockMinutes
        self.grantOnPassMinutes = grantOnPassMinutes
        self.cooldownMinutes = cooldownMinutes
        self.isEmergency = isEmergency
    }
}

public struct DeviceSummary: Sendable, Equatable, Identifiable, Codable {
    public var id: String { deviceId }
    public let deviceId: String
    public let model: String
    public let platform: String // "ios" or "android"
    public let osVersion: String
    public let appVersion: String
    public let batteryPercent: Int?
    public let revoked: Bool
    public let lastSeenAtEpochMs: Int64?
    public let pairedAtEpochMs: Int64?

    public init(
        deviceId: String,
        model: String,
        platform: String = "ios",
        osVersion: String = "iOS 18",
        appVersion: String = "1.0.0",
        batteryPercent: Int? = 85,
        revoked: Bool = false,
        lastSeenAtEpochMs: Int64? = nil,
        pairedAtEpochMs: Int64? = nil
    ) {
        self.deviceId = deviceId
        self.model = model
        self.platform = platform
        self.osVersion = osVersion
        self.appVersion = appVersion
        self.batteryPercent = batteryPercent
        self.revoked = revoked
        self.lastSeenAtEpochMs = lastSeenAtEpochMs
        self.pairedAtEpochMs = pairedAtEpochMs
    }
}

public struct UsageDaySummary: Sendable, Equatable, Identifiable {
    public var id: String { day }
    public let day: String // YYYY-MM-DD
    public let minutesUsed: Int
    public let minutesByApp: [String: Int]

    public init(day: String, minutesUsed: Int, minutesByApp: [String: Int] = [:]) {
        self.day = day
        self.minutesUsed = minutesUsed
        self.minutesByApp = minutesByApp
    }
}

public struct QuizAttemptSummary: Sendable, Equatable, Identifiable {
    public var id: String { attemptId }
    public let attemptId: String
    public let score: Int
    public let total: Int
    public let passed: Bool
    public let extraMinutesGranted: Int
    public let topics: [String]
    public let createdAtEpochMs: Int64

    public init(
        attemptId: String,
        score: Int,
        total: Int,
        passed: Bool,
        extraMinutesGranted: Int,
        topics: [String],
        createdAtEpochMs: Int64
    ) {
        self.attemptId = attemptId
        self.score = score
        self.total = total
        self.passed = passed
        self.extraMinutesGranted = extraMinutesGranted
        self.topics = topics
        self.createdAtEpochMs = createdAtEpochMs
    }
}

public struct TopicSkillSummary: Sendable, Equatable, Identifiable {
    public var id: String { topic }
    public let topic: String
    public let level: Int
    public let streakCorrect: Int
    public let weak: Bool
    public let weakConcepts: [String]
    public let masteredConcepts: [String]
    public let tierLabel: String

    public init(
        topic: String,
        level: Int,
        streakCorrect: Int,
        weak: Bool,
        weakConcepts: [String] = [],
        masteredConcepts: [String] = [],
        tierLabel: String = "basic"
    ) {
        self.topic = topic
        self.level = level
        self.streakCorrect = streakCorrect
        self.weak = weak
        self.weakConcepts = weakConcepts
        self.masteredConcepts = masteredConcepts
        self.tierLabel = tierLabel
    }
}

public struct InstalledAppSummary: Sendable, Equatable, Identifiable {
    public var id: String { packageName }
    public let packageName: String
    public let appName: String
    public let category: String

    public init(packageName: String, appName: String, category: String) {
        self.packageName = packageName
        self.appName = appName
        self.category = category
    }
}

/// Protocol abstracting parent-facing Firestore operations for child policy, allowlist, usage, and devices.
public protocol ParentControlStoreProtocol: Sendable {
    func getFamilyMeta(familyId: String) async throws -> FamilyMeta?
    func listChildren(familyId: String) async throws -> [FamilyChildProfile]
    func addChild(familyId: String, child: FamilyDraftChild) async throws -> FamilyChildProfile
    func updateChild(familyId: String, childId: String, child: FamilyDraftChild) async throws -> FamilyChildProfile
    func deleteChild(familyId: String, childId: String) async throws

    func getPolicy(familyId: String, childId: String) async throws -> ChildPolicy
    func updatePolicy(familyId: String, childId: String, policy: ChildPolicy) async throws
    func setChildPaused(familyId: String, childId: String, paused: Bool) async throws
    func addBonusTime(familyId: String, childId: String, bonusMinutes: Int) async throws

    func listAppRules(familyId: String, childId: String) async throws -> [AppRule]
    func upsertAppRule(familyId: String, childId: String, rule: AppRule) async throws
    func deleteAppRule(familyId: String, childId: String, appId: String) async throws

    func listUsageDays(familyId: String, childId: String, limit: Int) async throws -> [UsageDaySummary]
    func listQuizAttempts(familyId: String, childId: String, limit: Int) async throws -> [QuizAttemptSummary]
    func getSkillState(familyId: String, childId: String) async throws -> [TopicSkillSummary]
    func listDevices(familyId: String, childId: String) async throws -> [DeviceSummary]
    func setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Bool) async throws
    func listInstalledApps(familyId: String, childId: String) async throws -> [InstalledAppSummary]

    func deleteFamily(familyId: String) async throws
}
