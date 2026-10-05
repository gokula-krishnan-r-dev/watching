import Foundation

/// Active session state on the child device.
/// Controls shielding, quiz triggering, and fail-lock.
/// Mirrors `com.meritscreen.core.common.domain.SessionPhase`.
public enum SessionPhase: String, Codable, Sendable, Equatable {
    case idle
    case inBlock = "in_block"
    case quizDue = "quiz_due"
    case shielded
    case bedtimeLock = "bedtime_lock"
    case dailyLimitLock = "daily_limit_lock"
    case parentPaused = "parent_paused"
    case unsupervised

    public var isShielded: Bool {
        self == .shielded
    }

    public var isQuizDue: Bool {
        self == .quizDue
    }

    public var isDailyLimitLocked: Bool {
        self == .dailyLimitLock
    }

    public var displayLabel: String {
        switch self {
        case .idle: return "Ready"
        case .inBlock: return "Active Block"
        case .quizDue: return "Quiz Due"
        case .shielded: return "Fail Lock"
        case .bedtimeLock: return "Bedtime"
        case .dailyLimitLock: return "Daily Limit Reached"
        case .parentPaused: return "Paused by Parent"
        case .unsupervised: return "Unsupervised"
        }
    }
}
