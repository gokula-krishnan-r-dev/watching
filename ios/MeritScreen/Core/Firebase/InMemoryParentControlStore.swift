import Foundation

/// In-memory Parent Control Store for unit tests.
/// Handles parent-facing reads and writes for policy, allowlist rules, usage, analytics, and devices.
public actor InMemoryParentControlStore: ParentControlStoreProtocol {
    public static let shared = InMemoryParentControlStore()

    private var familyMetas: [String: FamilyMeta] = [:]
    private var childrenByFamily: [String: [FamilyChildProfile]] = [:]
    private var policiesByChild: [String: ChildPolicy] = [:] // childId -> policy
    private var rulesByChild: [String: [AppRule]] = [:] // childId -> rules
    private var devicesByChild: [String: [DeviceSummary]] = [:] // childId -> devices
    private var usageByChild: [String: [UsageDaySummary]] = [:] // childId -> usage
    private var quizzesByChild: [String: [QuizAttemptSummary]] = [:] // childId -> quizzes
    private var skillsByChild: [String: [TopicSkillSummary]] = [:] // childId -> skills
    private var installedAppsByChild: [String: [InstalledAppSummary]] = [:] // childId -> apps

    private struct SampleSeed: Sendable {
        var familyMetas: [String: FamilyMeta]
        var childrenByFamily: [String: [FamilyChildProfile]]
        var policiesByChild: [String: ChildPolicy]
        var rulesByChild: [String: [AppRule]]
        var devicesByChild: [String: [DeviceSummary]]
        var usageByChild: [String: [UsageDaySummary]]
        var quizzesByChild: [String: [QuizAttemptSummary]]
        var skillsByChild: [String: [TopicSkillSummary]]
        var installedAppsByChild: [String: [InstalledAppSummary]]
    }

    public init() {
        let seed = Self.makeSampleSeed()
        self.familyMetas = seed.familyMetas
        self.childrenByFamily = seed.childrenByFamily
        self.policiesByChild = seed.policiesByChild
        self.rulesByChild = seed.rulesByChild
        self.devicesByChild = seed.devicesByChild
        self.usageByChild = seed.usageByChild
        self.quizzesByChild = seed.quizzesByChild
        self.skillsByChild = seed.skillsByChild
        self.installedAppsByChild = seed.installedAppsByChild
    }

    private static func makeSampleSeed() -> SampleSeed {
        var familyMetas: [String: FamilyMeta] = [:]
        var childrenByFamily: [String: [FamilyChildProfile]] = [:]
        var policiesByChild: [String: ChildPolicy] = [:]
        var rulesByChild: [String: [AppRule]] = [:]
        var devicesByChild: [String: [DeviceSummary]] = [:]
        var usageByChild: [String: [UsageDaySummary]] = [:]
        var quizzesByChild: [String: [QuizAttemptSummary]] = [:]
        var skillsByChild: [String: [TopicSkillSummary]] = [:]
        var installedAppsByChild: [String: [InstalledAppSummary]] = [:]

        let sampleFamilyId = "sample_family"
        familyMetas[sampleFamilyId] = FamilyMeta(familyId: sampleFamilyId, name: "The Family", ownerUid: "parent_1")

        let child1 = FamilyChildProfile(
            childId: "child_leo",
            displayName: "Leo",
            ageBand: .age7To9,
            avatar: .fox,
            language: "en"
        )
        let child2 = FamilyChildProfile(
            childId: "child_maya",
            displayName: "Maya",
            ageBand: .age4To6,
            avatar: .bear,
            language: "en"
        )
        childrenByFamily[sampleFamilyId] = [child1, child2]

        // Seed Leo's policy
        policiesByChild["child_leo"] = ChildPolicy(
            paused: false,
            bonusMinutesToday: 0,
            quizMode: .appBlock,
            allowRetryDuringCooldown: true,
            dailyCeilingMinutes: 120,
            questionsPerQuiz: 3,
            passScorePercent: 70,
            rewardsEnabled: true,
            weekendBonusEnabled: true,
            extraMinutesOnPass: 15,
            aiQuizzesEnabled: true,
            adaptiveDifficultyEnabled: true,
            showExplanations: true,
            defaultBlockMinutes: 15,
            defaultCooldownMinutes: 15,
            bedtimeEnabled: true,
            bedtimeStartLabel: "8:00 PM",
            bedtimeEndLabel: "7:00 AM",
            learningProfile: LearningProfile(
                gradeStandard: "Grade 3",
                region: .US,
                customPromptGuidelines: "Focus on multiplication tables and reading comprehension."
            )
        )

        // Seed Maya's policy
        policiesByChild["child_maya"] = ChildPolicy(
            paused: false,
            bonusMinutesToday: 0,
            quizMode: .everySession,
            allowRetryDuringCooldown: true,
            dailyCeilingMinutes: 60,
            questionsPerQuiz: 2,
            passScorePercent: 60,
            rewardsEnabled: true,
            weekendBonusEnabled: false,
            extraMinutesOnPass: 10,
            aiQuizzesEnabled: true,
            adaptiveDifficultyEnabled: true,
            showExplanations: true,
            defaultBlockMinutes: 10,
            defaultCooldownMinutes: 10,
            bedtimeEnabled: true,
            bedtimeStartLabel: "7:30 PM",
            bedtimeEndLabel: "7:00 AM",
            learningProfile: LearningProfile(
                gradeStandard: "Kindergarten",
                region: .US,
                customPromptGuidelines: "Phonics, letter sounds, and animal vocabulary."
            )
        )

        // Seed Leo's devices
        devicesByChild["child_leo"] = [
            DeviceSummary(
                deviceId: "device_ipad_1",
                model: "iPad Air (5th gen)",
                platform: "ios",
                osVersion: "iPadOS 18.1",
                appVersion: "1.0.0",
                batteryPercent: 88,
                revoked: false,
                lastSeenAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) - 120_000,
                pairedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) - 86400_000
            )
        ]

        // Seed Maya's devices
        devicesByChild["child_maya"] = [
            DeviceSummary(
                deviceId: "device_tab_2",
                model: "iPad mini (6th gen)",
                platform: "ios",
                osVersion: "iPadOS 18.0",
                appVersion: "1.0.0",
                batteryPercent: 72,
                revoked: false,
                lastSeenAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) - 450_000,
                pairedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) - 172800_000
            )
        ]

        // Seed Leo's rules
        rulesByChild["child_leo"] = [
            AppRule(appId: "rule_ytkids", packageOrBundleId: "com.google.ios.youtubekids", displayName: "YouTube Kids", allowed: true, blockMinutes: 15, grantOnPassMinutes: 15),
            AppRule(appId: "rule_duolingo", packageOrBundleId: "com.duolingo.DuolingoMobile", displayName: "Duolingo", allowed: true, blockMinutes: 30, grantOnPassMinutes: 30),
            AppRule(appId: "rule_roblox", packageOrBundleId: "com.roblox.robloxmobile", displayName: "Roblox", allowed: false, blockMinutes: 15, grantOnPassMinutes: 15),
            AppRule(appId: "rule_scratch", packageOrBundleId: "org.scratch.junior", displayName: "Scratch Jr", allowed: true, blockMinutes: 30, grantOnPassMinutes: 30)
        ]

        // Seed Leo's usage
        let todayFormatter = ISO8601DateFormatter()
        let todayStr = String(todayFormatter.string(from: Date()).prefix(10))
        usageByChild["child_leo"] = [
            UsageDaySummary(day: todayStr, minutesUsed: 45, minutesByApp: ["com.google.ios.youtubekids": 30, "com.duolingo.DuolingoMobile": 15])
        ]

        // Seed Leo's quiz attempts
        quizzesByChild["child_leo"] = [
            QuizAttemptSummary(
                attemptId: "quiz_1",
                score: 3,
                total: 3,
                passed: true,
                extraMinutesGranted: 15,
                topics: ["Math", "Geometry"],
                createdAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) - 3600_000
            )
        ]

        // Seed Leo's skills
        skillsByChild["child_leo"] = [
            TopicSkillSummary(topic: "Math Foundations", level: 3, streakCorrect: 4, weak: false),
            TopicSkillSummary(topic: "Reading Quest", level: 2, streakCorrect: 3, weak: false),
            TopicSkillSummary(topic: "Geometry & Shapes", level: 1, streakCorrect: 1, weak: true, weakConcepts: ["Polygon Angles"])
        ]

        // Seed installed apps
        installedAppsByChild["child_leo"] = [
            InstalledAppSummary(packageName: "com.google.ios.youtubekids", appName: "YouTube Kids", category: "Entertainment"),
            InstalledAppSummary(packageName: "com.duolingo.DuolingoMobile", appName: "Duolingo", category: "Educational"),
            InstalledAppSummary(packageName: "com.roblox.robloxmobile", appName: "Roblox", category: "Games"),
            InstalledAppSummary(packageName: "org.scratch.junior", appName: "Scratch Jr", category: "Educational"),
            InstalledAppSummary(packageName: "com.khanacademy.KhanAcademyKids", appName: "Khan Academy Kids", category: "Educational")
        ]

        return SampleSeed(
            familyMetas: familyMetas,
            childrenByFamily: childrenByFamily,
            policiesByChild: policiesByChild,
            rulesByChild: rulesByChild,
            devicesByChild: devicesByChild,
            usageByChild: usageByChild,
            quizzesByChild: quizzesByChild,
            skillsByChild: skillsByChild,
            installedAppsByChild: installedAppsByChild
        )
    }

    public func getFamilyMeta(familyId: String) async throws -> FamilyMeta? {
        if let meta = familyMetas[familyId] {
            return meta
        }
        return FamilyMeta(familyId: familyId, name: "The Family", ownerUid: "parent_user")
    }

    public func listChildren(familyId: String) async throws -> [FamilyChildProfile] {
        return childrenByFamily[familyId] ?? []
    }

    public func addChild(familyId: String, child: FamilyDraftChild) async throws -> FamilyChildProfile {
        var current = childrenByFamily[familyId] ?? []
        if current.count >= AppConfig.maxChildrenPerParent {
            throw AppError.validation("You can add up to \(AppConfig.maxChildrenPerParent) children.")
        }
        let childId = child.localId.isEmpty ? UUID().uuidString.lowercased() : child.localId
        let profile = FamilyChildProfile(
            childId: childId,
            displayName: child.name.trimmingCharacters(in: .whitespacesAndNewlines),
            ageBand: child.ageBand,
            avatar: child.avatar,
            language: child.language
        )
        current.append(profile)
        childrenByFamily[familyId] = current

        // Set default policy
        let defaultPolicy = ChildPolicy(
            paused: false,
            bonusMinutesToday: 0,
            quizMode: .appBlock,
            dailyCeilingMinutes: AppConfig.defaultDailyCeilingMinutes,
            learningProfile: LearningProfile(
                gradeStandard: child.ageBand.displayLabel,
                region: .US,
                customPromptGuidelines: ""
            )
        )
        policiesByChild[childId] = defaultPolicy
        return profile
    }

    public func updateChild(familyId: String, childId: String, child: FamilyDraftChild) async throws -> FamilyChildProfile {
        var current = childrenByFamily[familyId] ?? []
        let profile = FamilyChildProfile(
            childId: childId,
            displayName: child.name.trimmingCharacters(in: .whitespacesAndNewlines),
            ageBand: child.ageBand,
            avatar: child.avatar,
            language: child.language
        )
        if let idx = current.firstIndex(where: { $0.childId == childId }) {
            current[idx] = profile
        } else {
            current.append(profile)
        }
        childrenByFamily[familyId] = current
        return profile
    }

    public func deleteChild(familyId: String, childId: String) async throws {
        if var list = childrenByFamily[familyId] {
            list.removeAll { $0.childId == childId }
            childrenByFamily[familyId] = list
        }
        policiesByChild.removeValue(forKey: childId)
        rulesByChild.removeValue(forKey: childId)
        devicesByChild.removeValue(forKey: childId)
        usageByChild.removeValue(forKey: childId)
        quizzesByChild.removeValue(forKey: childId)
        skillsByChild.removeValue(forKey: childId)
    }

    public func getPolicy(familyId: String, childId: String) async throws -> ChildPolicy {
        if let policy = policiesByChild[childId] {
            return policy
        }
        let fallback = ChildPolicy()
        policiesByChild[childId] = fallback
        return fallback
    }

    public func updatePolicy(familyId: String, childId: String, policy: ChildPolicy) async throws {
        policiesByChild[childId] = policy
    }

    public func setChildPaused(familyId: String, childId: String, paused: Bool) async throws {
        var current = try await getPolicy(familyId: familyId, childId: childId)
        current.paused = paused
        policiesByChild[childId] = current
    }

    public func addBonusTime(familyId: String, childId: String, bonusMinutes: Int) async throws {
        var current = try await getPolicy(familyId: familyId, childId: childId)
        let ceiling = (current.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) + bonusMinutes
        current.dailyCeilingMinutes = ceiling
        current.bonusMinutesToday += bonusMinutes
        policiesByChild[childId] = current
    }

    public func listAppRules(familyId: String, childId: String) async throws -> [AppRule] {
        return rulesByChild[childId] ?? []
    }

    public func upsertAppRule(familyId: String, childId: String, rule: AppRule) async throws {
        var rules = rulesByChild[childId] ?? []
        if let idx = rules.firstIndex(where: { $0.appId == rule.appId || $0.packageOrBundleId == rule.packageOrBundleId }) {
            rules[idx] = rule
        } else {
            rules.append(rule)
        }
        rulesByChild[childId] = rules
    }

    public func deleteAppRule(familyId: String, childId: String, appId: String) async throws {
        var rules = rulesByChild[childId] ?? []
        rules.removeAll { $0.appId == appId || $0.packageOrBundleId == appId }
        rulesByChild[childId] = rules
    }

    public func listUsageDays(familyId: String, childId: String, limit: Int) async throws -> [UsageDaySummary] {
        let list = usageByChild[childId] ?? []
        return Array(list.prefix(limit))
    }

    public func listQuizAttempts(familyId: String, childId: String, limit: Int) async throws -> [QuizAttemptSummary] {
        let list = quizzesByChild[childId] ?? []
        return Array(list.prefix(limit))
    }

    public func getSkillState(familyId: String, childId: String) async throws -> [TopicSkillSummary] {
        return skillsByChild[childId] ?? []
    }

    public func listDevices(familyId: String, childId: String) async throws -> [DeviceSummary] {
        return devicesByChild[childId] ?? []
    }

    public func setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Bool) async throws {
        var devices = devicesByChild[childId] ?? []
        if let idx = devices.firstIndex(where: { $0.deviceId == deviceId }) {
            let existing = devices[idx]
            devices[idx] = DeviceSummary(
                deviceId: existing.deviceId,
                model: existing.model,
                platform: existing.platform,
                osVersion: existing.osVersion,
                appVersion: existing.appVersion,
                batteryPercent: existing.batteryPercent,
                revoked: revoked,
                lastSeenAtEpochMs: existing.lastSeenAtEpochMs,
                pairedAtEpochMs: existing.pairedAtEpochMs
            )
            devicesByChild[childId] = devices
        }
    }

    public func listInstalledApps(familyId: String, childId: String) async throws -> [InstalledAppSummary] {
        return installedAppsByChild[childId] ?? []
    }

    public func deleteFamily(familyId: String) async throws {
        familyMetas.removeValue(forKey: familyId)
        let children = childrenByFamily[familyId] ?? []
        for child in children {
            policiesByChild.removeValue(forKey: child.childId)
            rulesByChild.removeValue(forKey: child.childId)
            devicesByChild.removeValue(forKey: child.childId)
            usageByChild.removeValue(forKey: child.childId)
            quizzesByChild.removeValue(forKey: child.childId)
            skillsByChild.removeValue(forKey: child.childId)
            installedAppsByChild.removeValue(forKey: child.childId)
        }
        childrenByFamily.removeValue(forKey: familyId)
    }
}
