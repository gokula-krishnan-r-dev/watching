import Foundation

/// In-progress first-run onboarding draft state collected offline prior to authentication.
/// Mirrors `com.meritscreen.feature.onboarding.domain.OnboardingDraft`.
public struct OnboardingDraft: Codable, Sendable, Equatable {
    public var consentGiven: Bool
    public var familyName: String
    public var children: [ChildDraft]
    public var parentPinHash: String?

    // Schedule & Boundaries
    public var dailyBudgetMinutes: Int
    public var quizFrequencyMinutes: Int
    public var bedtimeEnabled: Bool
    public var bedtimeStart: String
    public var bedtimeEnd: String
    public var cooldownMinutes: Int

    // App Allowlist & AI Learning Context
    public var allowedApps: [OnboardingAppRule]
    public var aiLearningPrompt: String
    public var curriculumFocusIds: [String]
    public var pairedDeviceToken: String?
    public var isDevicePaired: Bool

    public init(
        consentGiven: Bool = false,
        familyName: String = "",
        children: [ChildDraft] = [],
        parentPinHash: String? = nil,
        dailyBudgetMinutes: Int = 90,
        quizFrequencyMinutes: Int = 30,
        bedtimeEnabled: Bool = true,
        bedtimeStart: String = "20:30",
        bedtimeEnd: String = "07:00",
        cooldownMinutes: Int = 10,
        allowedApps: [OnboardingAppRule] = OnboardingAppRule.defaultRules,
        aiLearningPrompt: String = "",
        curriculumFocusIds: [String] = ["math_foundations", "reading_fluency", "stem_logic"],
        pairedDeviceToken: String? = nil,
        isDevicePaired: Bool = false
    ) {
        self.consentGiven = consentGiven
        self.familyName = familyName
        self.children = children
        self.parentPinHash = parentPinHash
        self.dailyBudgetMinutes = dailyBudgetMinutes
        self.quizFrequencyMinutes = quizFrequencyMinutes
        self.bedtimeEnabled = bedtimeEnabled
        self.bedtimeStart = bedtimeStart
        self.bedtimeEnd = bedtimeEnd
        self.cooldownMinutes = cooldownMinutes
        self.allowedApps = allowedApps
        self.aiLearningPrompt = aiLearningPrompt
        self.curriculumFocusIds = curriculumFocusIds
        self.pairedDeviceToken = pairedDeviceToken
        self.isDevicePaired = isDevicePaired
    }

    /// Custom decoder ensuring backwards compatibility with older serialized JSON drafts.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.consentGiven = try container.decodeIfPresent(Bool.self, forKey: .consentGiven) ?? false
        self.familyName = try container.decodeIfPresent(String.self, forKey: .familyName) ?? ""
        self.children = try container.decodeIfPresent([ChildDraft].self, forKey: .children) ?? []
        self.parentPinHash = try container.decodeIfPresent(String.self, forKey: .parentPinHash)

        self.dailyBudgetMinutes = try container.decodeIfPresent(Int.self, forKey: .dailyBudgetMinutes) ?? 90
        self.quizFrequencyMinutes = try container.decodeIfPresent(Int.self, forKey: .quizFrequencyMinutes) ?? 30
        self.bedtimeEnabled = try container.decodeIfPresent(Bool.self, forKey: .bedtimeEnabled) ?? true
        self.bedtimeStart = try container.decodeIfPresent(String.self, forKey: .bedtimeStart) ?? "20:30"
        self.bedtimeEnd = try container.decodeIfPresent(String.self, forKey: .bedtimeEnd) ?? "07:00"
        self.cooldownMinutes = try container.decodeIfPresent(Int.self, forKey: .cooldownMinutes) ?? 10

        self.allowedApps = try container.decodeIfPresent([OnboardingAppRule].self, forKey: .allowedApps) ?? OnboardingAppRule.defaultRules
        self.aiLearningPrompt = try container.decodeIfPresent(String.self, forKey: .aiLearningPrompt) ?? ""
        self.curriculumFocusIds = try container.decodeIfPresent([String].self, forKey: .curriculumFocusIds) ?? ["math_foundations", "reading_fluency", "stem_logic"]
        self.pairedDeviceToken = try container.decodeIfPresent(String.self, forKey: .pairedDeviceToken)
        self.isDevicePaired = try container.decodeIfPresent(Bool.self, forKey: .isDevicePaired) ?? false
    }

    /// Ready to commit once at least one child profile and a Parent PIN hash exist.
    public var isReadyToCommit: Bool {
        consentGiven && !children.isEmpty && !(parentPinHash ?? "").isEmpty
    }

    public var canAddChild: Bool {
        children.count < AppConfig.maxChildrenPerParent
    }
}
