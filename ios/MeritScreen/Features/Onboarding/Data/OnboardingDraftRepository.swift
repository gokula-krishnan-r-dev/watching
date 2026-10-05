import Foundation

public protocol OnboardingDraftRepositoryProtocol: Sendable {
    func getDraft() -> OnboardingDraft
    func saveDraft(_ draft: OnboardingDraft)
    func setConsentGiven(_ given: Bool)
    func setFamilyName(_ name: String)
    func addChild(_ child: ChildDraft) throws
    func removeChild(id: String)
    func setParentPin(pin: String) throws
    func setSchedule(
        dailyBudgetMinutes: Int,
        quizFrequencyMinutes: Int,
        bedtimeEnabled: Bool,
        bedtimeStart: String,
        bedtimeEnd: String,
        cooldownMinutes: Int
    )
    func setAppRules(_ rules: [OnboardingAppRule])
    func setAiLearningContext(prompt: String, topics: [String])
    func setPairedDevice(token: String?, isPaired: Bool)
    func clearDraft()
}

/// Offline-first repository persisting the parent's onboarding draft in UserDefaults.
/// Survives application process kill and restart.
public final class OnboardingDraftRepository: OnboardingDraftRepositoryProtocol, @unchecked Sendable {
    public static let shared = OnboardingDraftRepository()

    private let defaultsKey = "meritscreen_onboarding_draft"
    private let defaults: UserDefaults
    private let pinHasher: PinHasherProtocol

    public init(
        defaults: UserDefaults = .standard,
        pinHasher: PinHasherProtocol = Pbkdf2PinHasher.shared
    ) {
        self.defaults = defaults
        self.pinHasher = pinHasher
    }

    public func getDraft() -> OnboardingDraft {
        guard let data = defaults.data(forKey: defaultsKey),
              let draft = try? JSONDecoder().decode(OnboardingDraft.self, from: data) else {
            return OnboardingDraft()
        }
        return draft
    }

    public func current() -> OnboardingDraft {
        getDraft()
    }

    public func clear() {
        clearDraft()
    }

    public func saveDraft(_ draft: OnboardingDraft) {
        if let data = try? JSONEncoder().encode(draft) {
            defaults.set(data, forKey: defaultsKey)
        }
    }

    public func setConsentGiven(_ given: Bool) {
        var draft = getDraft()
        draft.consentGiven = given
        saveDraft(draft)
    }

    public func setFamilyName(_ name: String) {
        var draft = getDraft()
        draft.familyName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        saveDraft(draft)
    }

    public func addChild(_ child: ChildDraft) throws {
        var draft = getDraft()
        let trimmedName = child.name.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmedName.isEmpty else {
            throw AppError.validation(userMessage: "Please enter your child's name.")
        }

        guard draft.children.count < AppConfig.maxChildrenPerParent else {
            throw AppError.validation(
                userMessage: "You can add up to \(AppConfig.maxChildrenPerParent) children in a family profile."
            )
        }

        var newChild = child
        newChild.name = trimmedName
        draft.children.append(newChild)
        saveDraft(draft)
    }

    public func removeChild(id: String) {
        var draft = getDraft()
        draft.children.removeAll { $0.id == id }
        saveDraft(draft)
    }

    public func setParentPin(pin: String) throws {
        let trimmedPin = pin.trimmingCharacters(in: .whitespacesAndNewlines)

        guard trimmedPin.count == AppConfig.parentPinMinLength,
              trimmedPin.allSatisfy({ $0.isNumber }) else {
            throw AppError.validation(userMessage: "Parent PIN must be exactly \(AppConfig.parentPinMinLength) digits.")
        }

        let hash = pinHasher.hash(pin: trimmedPin)
        var draft = getDraft()
        draft.parentPinHash = hash
        saveDraft(draft)
    }

    public func setSchedule(
        dailyBudgetMinutes: Int,
        quizFrequencyMinutes: Int,
        bedtimeEnabled: Bool,
        bedtimeStart: String,
        bedtimeEnd: String,
        cooldownMinutes: Int
    ) {
        var draft = getDraft()
        draft.dailyBudgetMinutes = dailyBudgetMinutes
        draft.quizFrequencyMinutes = quizFrequencyMinutes
        draft.bedtimeEnabled = bedtimeEnabled
        draft.bedtimeStart = bedtimeStart
        draft.bedtimeEnd = bedtimeEnd
        draft.cooldownMinutes = cooldownMinutes
        saveDraft(draft)
    }

    public func setAppRules(_ rules: [OnboardingAppRule]) {
        var draft = getDraft()
        draft.allowedApps = rules
        saveDraft(draft)
    }

    public func setAiLearningContext(prompt: String, topics: [String]) {
        var draft = getDraft()
        draft.aiLearningPrompt = prompt
        draft.curriculumFocusIds = topics
        saveDraft(draft)
    }

    public func setPairedDevice(token: String?, isPaired: Bool) {
        var draft = getDraft()
        draft.pairedDeviceToken = token
        draft.isDevicePaired = isPaired
        saveDraft(draft)
    }

    public func clearDraft() {
        defaults.removeObject(forKey: defaultsKey)
    }
}
