import Foundation
import SwiftData

@Model
public final class ChildPolicyEntity {
    @Attribute(.unique) public var id: String
    public var quizModeRaw: String
    public var allowRetryDuringCooldown: Bool
    public var dailyCeilingMinutes: Int?
    public var questionsPerQuiz: Int
    public var passScorePercent: Int
    public var rewardsEnabled: Bool
    public var weekendBonusEnabled: Bool
    public var extraMinutesOnPass: Int
    public var aiQuizzesEnabled: Bool
    public var adaptiveDifficultyEnabled: Bool
    public var showExplanations: Bool
    public var defaultBlockMinutes: Int
    public var defaultCooldownMinutes: Int
    public var updatedAt: Date

    public init(
        id: String = "current",
        policy: ChildPolicy = ChildPolicy(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.quizModeRaw = policy.quizMode.rawValue
        self.allowRetryDuringCooldown = policy.allowRetryDuringCooldown
        self.dailyCeilingMinutes = policy.dailyCeilingMinutes
        self.questionsPerQuiz = policy.questionsPerQuiz
        self.passScorePercent = policy.passScorePercent
        self.rewardsEnabled = policy.rewardsEnabled
        self.weekendBonusEnabled = policy.weekendBonusEnabled
        self.extraMinutesOnPass = policy.extraMinutesOnPass
        self.aiQuizzesEnabled = policy.aiQuizzesEnabled
        self.adaptiveDifficultyEnabled = policy.adaptiveDifficultyEnabled
        self.showExplanations = policy.showExplanations
        self.defaultBlockMinutes = policy.defaultBlockMinutes
        self.defaultCooldownMinutes = policy.defaultCooldownMinutes
        self.updatedAt = updatedAt
    }

    public func toDomain() -> ChildPolicy {
        ChildPolicy(
            quizMode: QuizMode.fromStorage(quizModeRaw),
            allowRetryDuringCooldown: allowRetryDuringCooldown,
            dailyCeilingMinutes: dailyCeilingMinutes,
            questionsPerQuiz: questionsPerQuiz,
            passScorePercent: passScorePercent,
            rewardsEnabled: rewardsEnabled,
            weekendBonusEnabled: weekendBonusEnabled,
            extraMinutesOnPass: extraMinutesOnPass,
            aiQuizzesEnabled: aiQuizzesEnabled,
            adaptiveDifficultyEnabled: adaptiveDifficultyEnabled,
            showExplanations: showExplanations,
            defaultBlockMinutes: defaultBlockMinutes,
            defaultCooldownMinutes: defaultCooldownMinutes
        )
    }
}

@Model
public final class AppRuleEntity {
    @Attribute(.unique) public var id: String // Bundle ID or token representation
    public var displayName: String
    public var allowed: Bool
    public var blockMinutes: Int
    public var passGrantMinutes: Int
    public var cooldownMinutes: Int
    public var isEmergency: Bool

    public init(
        id: String,
        displayName: String,
        allowed: Bool = true,
        blockMinutes: Int = AppConfig.defaultBlockMinutes,
        passGrantMinutes: Int = AppConfig.defaultBlockMinutes,
        cooldownMinutes: Int = AppConfig.defaultCooldownMinutes,
        isEmergency: Bool = false
    ) {
        self.id = id
        self.displayName = displayName
        self.allowed = allowed
        self.blockMinutes = blockMinutes
        self.passGrantMinutes = passGrantMinutes
        self.cooldownMinutes = cooldownMinutes
        self.isEmergency = isEmergency
    }
}

@Model
public final class SessionStateEntity {
    @Attribute(.unique) public var id: String
    public var phaseRaw: String
    public var activeAppToken: String?
    public var blockStartedAt: Date?
    public var blockDurationMinutes: Int
    public var shieldedUntil: Date?
    public var lastCalculatedAt: Date

    public init(
        id: String = "current",
        phase: SessionPhase = .idle,
        activeAppToken: String? = nil,
        blockStartedAt: Date? = nil,
        blockDurationMinutes: Int = AppConfig.defaultBlockMinutes,
        shieldedUntil: Date? = nil,
        lastCalculatedAt: Date = Date()
    ) {
        self.id = id
        self.phaseRaw = phase.rawValue
        self.activeAppToken = activeAppToken
        self.blockStartedAt = blockStartedAt
        self.blockDurationMinutes = blockDurationMinutes
        self.shieldedUntil = shieldedUntil
        self.lastCalculatedAt = lastCalculatedAt
    }
}

@Model
public final class QuizAttemptEntity {
    @Attribute(.unique) public var id: String
    public var childId: String
    public var timestamp: Date
    public var scorePercent: Int
    public var passed: Bool
    public var questionsCount: Int
    public var correctCount: Int
    public var topicsSummary: String

    public init(
        id: String = UUID().uuidString,
        childId: String,
        timestamp: Date = Date(),
        scorePercent: Int,
        passed: Bool,
        questionsCount: Int,
        correctCount: Int,
        topicsSummary: String = ""
    ) {
        self.id = id
        self.childId = childId
        self.timestamp = timestamp
        self.scorePercent = scorePercent
        self.passed = passed
        self.questionsCount = questionsCount
        self.correctCount = correctCount
        self.topicsSummary = topicsSummary
    }
}

@Model
public final class StickerEntity {
    @Attribute(.unique) public var id: String
    public var childId: String
    public var unlockedAt: Date
    public var explorerLevel: Int
    public var xpTotal: Int

    public init(
        id: String,
        childId: String,
        unlockedAt: Date = Date(),
        explorerLevel: Int = 1,
        xpTotal: Int = 0
    ) {
        self.id = id
        self.childId = childId
        self.unlockedAt = unlockedAt
        self.explorerLevel = explorerLevel
        self.xpTotal = xpTotal
    }
}
