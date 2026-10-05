import Foundation
import SwiftUI

/// App category types for report visualizers and breakdown cards.
public enum AppCategoryType: String, Sendable, CaseIterable {
    case learn = "Learn"
    case media = "Media"
    case language = "Language"
    case logic = "Logic"
    case general = "General"
}

/// Profile information for child selection pills in Reports.
public struct ChildReportProfile: Sendable, Equatable, Identifiable {
    public var id: String { childId }
    public let childId: String
    public let displayName: String
    public let ageLabel: String
    public let avatar: AvatarPreset

    public init(childId: String, displayName: String, ageLabel: String, avatar: AvatarPreset) {
        self.childId = childId
        self.displayName = displayName
        self.ageLabel = ageLabel
        self.avatar = avatar
    }
}

/// Rollup of minutes spent on a specific app.
public struct AppMinutesRollup: Sendable, Equatable, Identifiable {
    public var id: String { packageName }
    public let packageName: String
    public let displayName: String
    public let minutes: Int

    public init(packageName: String, displayName: String, minutes: Int) {
        self.packageName = packageName
        self.displayName = displayName
        self.minutes = minutes
    }
}

/// Daily screen time point used for Swift Charts bar graphs.
public struct DailyMinutesPoint: Sendable, Equatable, Identifiable {
    public var id: String { day }
    public let day: String // YYYY-MM-DD
    public let minutes: Int
    public let byApp: [AppMinutesRollup]

    public init(day: String, minutes: Int, byApp: [AppMinutesRollup] = []) {
        self.day = day
        self.minutes = minutes
        self.byApp = byApp
    }
}

/// Aggregated stats across quiz attempts within the selected time window.
public struct QuizWindowStats: Sendable, Equatable {
    public let attemptCount: Int
    public let passedCount: Int
    public let failedCount: Int
    public let accuracyPercent: Int?
    public let questionsCorrect: Int
    public let questionsTotal: Int
    public let extraMinutesEarned: Int

    public init(
        attemptCount: Int,
        passedCount: Int,
        failedCount: Int,
        accuracyPercent: Int?,
        questionsCorrect: Int,
        questionsTotal: Int,
        extraMinutesEarned: Int
    ) {
        self.attemptCount = attemptCount
        self.passedCount = passedCount
        self.failedCount = failedCount
        self.accuracyPercent = accuracyPercent
        self.questionsCorrect = questionsCorrect
        self.questionsTotal = questionsTotal
        self.extraMinutesEarned = extraMinutesEarned
    }
}

/// Weak concept hint identified by adaptive learning engine.
public struct WeakConceptHint: Sendable, Equatable, Identifiable {
    public var id: String { conceptId }
    public let conceptId: String
    public let title: String

    public init(conceptId: String, title: String) {
        self.conceptId = conceptId
        self.title = title
    }
}

/// Fully aggregated snapshot for one child and one window (1, 7, or 30 days).
public struct ChildReportsSnapshot: Sendable, Equatable {
    public let days: Int
    public let totalMinutes: Int
    public let averageMinutesPerDay: Int
    public let trendPercent: Int?
    public let educationalPercent: Int?
    public let busiestDay: DailyMinutesPoint?
    public let daily: [DailyMinutesPoint]
    public let byApp: [AppMinutesRollup]
    public let quiz: QuizWindowStats
    public let recentAttempts: [QuizAttemptSummary]
    public let topics: [TopicSkillSummary]
    public let practiceHints: [WeakConceptHint]
    public let hasAnyData: Bool
    public let streakDays: Int
    public let masteryRatePercent: Int?

    public init(
        days: Int,
        totalMinutes: Int,
        averageMinutesPerDay: Int,
        trendPercent: Int?,
        educationalPercent: Int?,
        busiestDay: DailyMinutesPoint?,
        daily: [DailyMinutesPoint],
        byApp: [AppMinutesRollup],
        quiz: QuizWindowStats,
        recentAttempts: [QuizAttemptSummary],
        topics: [TopicSkillSummary],
        practiceHints: [WeakConceptHint],
        hasAnyData: Bool,
        streakDays: Int = 0,
        masteryRatePercent: Int? = nil
    ) {
        self.days = days
        self.totalMinutes = totalMinutes
        self.averageMinutesPerDay = averageMinutesPerDay
        self.trendPercent = trendPercent
        self.educationalPercent = educationalPercent
        self.busiestDay = busiestDay
        self.daily = daily
        self.byApp = byApp
        self.quiz = quiz
        self.recentAttempts = recentAttempts
        self.topics = topics
        self.practiceHints = practiceHints
        self.hasAnyData = hasAnyData
        self.streakDays = streakDays
        self.masteryRatePercent = masteryRatePercent
    }
}

/// Formatted item for app allocation lists and pie/bar breakdowns.
public struct AppAllocationItem: Sendable, Equatable, Identifiable {
    public var id: String { packageName }
    public let packageName: String
    public let displayName: String
    public let minutes: Int
    public let percentage: Int
    public let categoryLabel: String
    public let categoryType: AppCategoryType

    public init(
        packageName: String,
        displayName: String,
        minutes: Int,
        percentage: Int,
        categoryLabel: String,
        categoryType: AppCategoryType
    ) {
        self.packageName = packageName
        self.displayName = displayName
        self.minutes = minutes
        self.percentage = percentage
        self.categoryLabel = categoryLabel
        self.categoryType = categoryType
    }
}

/// Item representing a recent quiz session in the report.
public struct FocusSessionItem: Sendable, Equatable, Identifiable {
    public var id: String { attemptId }
    public let attemptId: String
    public let title: String
    public let passed: Bool
    public let scoreLabel: String
    public let extraMinutes: Int

    public init(
        attemptId: String,
        title: String,
        passed: Bool,
        scoreLabel: String,
        extraMinutes: Int
    ) {
        self.attemptId = attemptId
        self.title = title
        self.passed = passed
        self.scoreLabel = scoreLabel
        self.extraMinutes = extraMinutes
    }
}

/// Learning topic mastery insight.
public struct AiTopicInsight: Sendable, Equatable, Identifiable {
    public var id: String { topic }
    public let topic: String
    public let levelLabel: String
    public let tierLabel: String
    public let weak: Bool
    public let masteredCount: Int

    public init(
        topic: String,
        levelLabel: String,
        tierLabel: String,
        weak: Bool,
        masteredCount: Int
    ) {
        self.topic = topic
        self.levelLabel = levelLabel
        self.tierLabel = tierLabel
        self.weak = weak
        self.masteredCount = masteredCount
    }
}

/// Complete presentation state model consumed by `P17_ReportsView`.
public struct ReportsUi: Sendable, Equatable {
    public let selectedChildId: String
    public let selectedDays: Int
    public let children: [ChildReportProfile]
    public let report: ChildReportsSnapshot
    public let selectedChildName: String
    public let periodLabel: String
    public let dailyAverageFormatted: String
    public let totalScreenTimeFormatted: String
    public let totalAiFocusTimeFormatted: String
    public let trendPercentage: Int?
    public let streakDays: Int
    public let masteryRatePercent: Int?
    public let balancedScore: Int?
    public let balancedScoreLabel: String
    public let balancedScoreDescription: String
    public let educationalPercent: Int?
    public let appAllocations: [AppAllocationItem]
    public let quizPassPercent: Int?
    public let focusSessionCount: Int
    public let focusSessionsPassed: Int
    public let extraMinutesEarned: Int
    public let focusSessions: [FocusSessionItem]
    public let aiTopics: [AiTopicInsight]
    public let demonstratedStrengths: [String]
    public let growthRecommended: [String]
    public let masteredConceptCount: Int
    public let cooldownIncidentsCount: Int
    public let avgCooldownRestMinutes: Int
    public let hasUsageData: Bool
    public let hasQuizData: Bool
    public let hasSkillData: Bool
    public let isRefreshing: Bool

    public init(
        selectedChildId: String,
        selectedDays: Int,
        children: [ChildReportProfile],
        report: ChildReportsSnapshot,
        selectedChildName: String,
        periodLabel: String,
        dailyAverageFormatted: String,
        totalScreenTimeFormatted: String,
        totalAiFocusTimeFormatted: String,
        trendPercentage: Int?,
        streakDays: Int,
        masteryRatePercent: Int?,
        balancedScore: Int?,
        balancedScoreLabel: String,
        balancedScoreDescription: String,
        educationalPercent: Int?,
        appAllocations: [AppAllocationItem],
        quizPassPercent: Int?,
        focusSessionCount: Int,
        focusSessionsPassed: Int,
        extraMinutesEarned: Int,
        focusSessions: [FocusSessionItem],
        aiTopics: [AiTopicInsight],
        demonstratedStrengths: [String],
        growthRecommended: [String],
        masteredConceptCount: Int,
        cooldownIncidentsCount: Int,
        avgCooldownRestMinutes: Int,
        hasUsageData: Bool,
        hasQuizData: Bool,
        hasSkillData: Bool,
        isRefreshing: Bool = false
    ) {
        self.selectedChildId = selectedChildId
        self.selectedDays = selectedDays
        self.children = children
        self.report = report
        self.selectedChildName = selectedChildName
        self.periodLabel = periodLabel
        self.dailyAverageFormatted = dailyAverageFormatted
        self.totalScreenTimeFormatted = totalScreenTimeFormatted
        self.totalAiFocusTimeFormatted = totalAiFocusTimeFormatted
        self.trendPercentage = trendPercentage
        self.streakDays = streakDays
        self.masteryRatePercent = masteryRatePercent
        self.balancedScore = balancedScore
        self.balancedScoreLabel = balancedScoreLabel
        self.balancedScoreDescription = balancedScoreDescription
        self.educationalPercent = educationalPercent
        self.appAllocations = appAllocations
        self.quizPassPercent = quizPassPercent
        self.focusSessionCount = focusSessionCount
        self.focusSessionsPassed = focusSessionsPassed
        self.extraMinutesEarned = extraMinutesEarned
        self.focusSessions = focusSessions
        self.aiTopics = aiTopics
        self.demonstratedStrengths = demonstratedStrengths
        self.growthRecommended = growthRecommended
        self.masteredConceptCount = masteredConceptCount
        self.cooldownIncidentsCount = cooldownIncidentsCount
        self.avgCooldownRestMinutes = avgCooldownRestMinutes
        self.hasUsageData = hasUsageData
        self.hasQuizData = hasQuizData
        self.hasSkillData = hasSkillData
        self.isRefreshing = isRefreshing
    }
}
