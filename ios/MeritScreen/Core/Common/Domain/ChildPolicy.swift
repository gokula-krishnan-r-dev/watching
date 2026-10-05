import Foundation

/// When a quiz interrupt fires. Matches `policy/current.quizMode` in Firestore.
public enum QuizMode: String, Codable, Sendable, CaseIterable {
    case appBlock = "app_block"
    case everySession = "every_session"
    case dailyCeiling = "daily_ceiling"

    public var displayLabel: String {
        switch self {
        case .appBlock:
            return "After each app block"
        case .everySession:
            return "Every session"
        case .dailyCeiling:
            return "At the daily ceiling"
        }
    }

    public var description: String {
        switch self {
        case .appBlock:
            return "Recommended. When an app's time block ends, a short quiz unlocks another block."
        case .everySession:
            return "A quiz when the child opens MeritScreen for a new session."
        case .dailyCeiling:
            return "Quiz only when the optional daily minute cap is reached."
        }
    }

    public static func fromStorage(_ raw: String?) -> QuizMode {
        guard let raw = raw?.lowercased() else { return .appBlock }
        switch raw {
        case "every_session", "everysession": return .everySession
        case "daily_ceiling", "dailyceiling": return .dailyCeiling
        default: return .appBlock
        }
    }
}

public enum LearningRegion: String, Codable, Sendable, CaseIterable {
    case IN = "IN"
    case US = "US"
    case UK = "UK"
    case AU = "AU"
    case CA = "CA"
    case OTHER = "OTHER"

    public var displayLabel: String {
        switch self {
        case .IN: return "India"
        case .US: return "United States"
        case .UK: return "United Kingdom"
        case .AU: return "Australia"
        case .CA: return "Canada"
        case .OTHER: return "Other"
        }
    }
}

public struct LearningProfile: Codable, Sendable, Equatable {
    public var gradeStandard: String
    public var region: LearningRegion
    public var customPromptGuidelines: String

    public init(
        gradeStandard: String = "",
        region: LearningRegion = .IN,
        customPromptGuidelines: String = ""
    ) {
        self.gradeStandard = gradeStandard
        self.region = region
        self.customPromptGuidelines = customPromptGuidelines
    }
}

/// Child-level policy document (`families/.../policy/current`).
/// Fail lock is ALWAYS device-wide for all non-emergency apps.
public struct ChildPolicy: Codable, Sendable, Equatable {
    public var paused: Bool
    public var bonusMinutesToday: Int
    public var quizMode: QuizMode
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
    public var bedtimeEnabled: Bool
    public var bedtimeStartLabel: String
    public var bedtimeEndLabel: String
    public var curriculumFocusIds: [String]
    public var learningProfile: LearningProfile

    public var customPromptGuidelines: String {
        get { learningProfile.customPromptGuidelines }
        set { learningProfile.customPromptGuidelines = newValue }
    }

    public var gradeStandard: String {
        get { learningProfile.gradeStandard }
        set { learningProfile.gradeStandard = newValue }
    }

    public init(
        paused: Bool = false,
        bonusMinutesToday: Int = 0,
        quizMode: QuizMode = .appBlock,
        allowRetryDuringCooldown: Bool = true,
        dailyCeilingMinutes: Int? = nil,
        questionsPerQuiz: Int = AppConfig.defaultQuestionsPerQuiz,
        passScorePercent: Int = AppConfig.defaultPassScorePercent,
        rewardsEnabled: Bool = true,
        weekendBonusEnabled: Bool = false,
        extraMinutesOnPass: Int = 0,
        aiQuizzesEnabled: Bool = true,
        adaptiveDifficultyEnabled: Bool = true,
        showExplanations: Bool = true,
        defaultBlockMinutes: Int = AppConfig.defaultBlockMinutes,
        defaultCooldownMinutes: Int = AppConfig.defaultCooldownMinutes,
        bedtimeEnabled: Bool = true,
        bedtimeStartLabel: String = AppConfig.defaultBedtimeLabel,
        bedtimeEndLabel: String = "7:00 AM",
        curriculumFocusIds: [String] = [],
        learningProfile: LearningProfile = LearningProfile()
    ) {
        self.paused = paused
        self.bonusMinutesToday = bonusMinutesToday
        self.quizMode = quizMode
        self.allowRetryDuringCooldown = allowRetryDuringCooldown
        self.dailyCeilingMinutes = dailyCeilingMinutes
        self.questionsPerQuiz = questionsPerQuiz
        self.passScorePercent = passScorePercent
        self.rewardsEnabled = rewardsEnabled
        self.weekendBonusEnabled = weekendBonusEnabled
        self.extraMinutesOnPass = extraMinutesOnPass
        self.aiQuizzesEnabled = aiQuizzesEnabled
        self.adaptiveDifficultyEnabled = adaptiveDifficultyEnabled
        self.showExplanations = showExplanations
        self.defaultBlockMinutes = defaultBlockMinutes
        self.defaultCooldownMinutes = defaultCooldownMinutes
        self.bedtimeEnabled = bedtimeEnabled
        self.bedtimeStartLabel = bedtimeStartLabel
        self.bedtimeEndLabel = bedtimeEndLabel
        self.curriculumFocusIds = curriculumFocusIds
        self.learningProfile = learningProfile
    }
}
