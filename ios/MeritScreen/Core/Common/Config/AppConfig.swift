import Foundation

/// Runtime-tunable product limits and defaults.
/// Mirrors `com.meritscreen.core.common.config.AppConfig` in the Android codebase.
public enum AppConfig {
    public static let maxChildrenPerParent: Int = 5
    public static let parentPinMinLength: Int = 4
    public static let parentPinMaxLength: Int = 4
    public static let parentPinMaxAttempts: Int = 5
    public static let parentPinLockoutMinutes: Int = 5

    public static let pairingTokenTtlMinutes: Int = 10
    public static let pairingCodeLength: Int = 6
    public static let pairingConsumeMaxAttempts: Int = 8

    public static let emailOtpLength: Int = 6
    public static let emailOtpTtlMinutes: Int = 10
    public static let emailOtpResendCooldownSeconds: Int = 42
    public static let emailOtpMaxAttempts: Int = 5

    /// Default app allowance assigned when a parent newly allows an application (minutes).
    public static let defaultBlockMinutes: Int = 15
    public static let defaultCooldownMinutes: Int = 15

    /// XP granted on each passed quiz when stickers/rewards are enabled.
    public static let stickerQuizPassXp: Int = 10
    public static let stickerWeekendBonusXp: Int = 5
    public static let weekendBonusExtraMinutes: Int = 5

    /// Cumulative XP required to reach Explorer levels 1..10.
    public static let explorerLevelXpThresholds: [Int] = [
        0, // padding
        0, 20, 50, 90, 140, 200, 270, 350, 450, 580
    ]
    public static let explorerLevelMax: Int = 10

    public static let sessionChunkMinMinutes: Int = 2
    public static let sessionChunkMaxMinutes: Int = 240
    public static let sessionChunkStandardMinutes: [Int] = [20, 30, 45]
    public static let sessionChunkCushionMinutes: [Int] = [2, 3, 5]
    public static let sessionChunkExtendedMinutes: [Int] = [10, 15, 50, 60]

    public static let defaultQuestionsPerQuiz: Int = 3
    public static let defaultPassScorePercent: Int = 70
    public static let defaultDailyCeilingMinutes: Int = 120
    public static let bonusTimeMinutesDefault: Int = 15
    public static let defaultBedtimeLabel: String = "8:00 PM"

    public static let usageFlushIntervalSeconds: Int = 45
    public static let sessionPersistIntervalSeconds: Int = 45
    public static let quizQuestionHistoryDays: Int = 30
    public static let dashboardChildrenDebounceMs: Int = 350
    public static let deviceOnlineThresholdMinutes: Int = 15

    public static let reportsTodayDays: Int = 1
    public static let reportsDefaultDays: Int = 7
    public static let reportsExtendedDays: Int = 30
    public static let reportsMaxDays: Int = 90
    public static let reportsMaxAttempts: Int = 100
    public static let reportsRecentAttempts: Int = 8

    public static let quizLockoutSeconds: Int = 30
    public static let quizPackLowThreshold: Int = 12
    public static let quizPackGenerateCount: Int = 12
    public static let customPromptGuidelinesMaxChars: Int = 500

    public static let geminiFlashModel: String = "gemini-3.7-flash"
    public static let geminiFlashModelFallbacks: [String] = [
        "gemini-2.5-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash"
    ]
    public static let geminiVertexLocation: String = "us-central1"
}
