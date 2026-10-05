import SwiftUI
import Observation

/// The step progression for the authenticated Parent Add Child flow.
/// Mirrors `com.meritscreen.feature.parent.ParentNavigation` in Android:
/// Child Profile $\rightarrow$ Timeline & Bedtime $\rightarrow$ Device Handshake (QR) $\rightarrow$
/// App Allowlist $\rightarrow$ AI Learning Context $\rightarrow$ Setup Complete Celebration.
public enum ParentAddChildStep: Int, CaseIterable, Sendable {
    case profile = 1
    case timeline = 2
    case pairing = 3
    case allowlist = 4
    case aiContext = 5
    case complete = 6

    public var stepTitle: String {
        switch self {
        case .profile: return "Child Profile"
        case .timeline: return "Timeline & Schedule"
        case .pairing: return "Device Handshake"
        case .allowlist: return "App Allowlist"
        case .aiContext: return "AI Learning Context"
        case .complete: return "Setup Complete"
        }
    }

    public var stepPillLabel: String {
        switch self {
        case .profile: return "Step 1 of 5 • Child Profile"
        case .timeline: return "Step 2 of 5 • Timeline & Rules"
        case .pairing: return "Step 3 of 5 • Device Handshake"
        case .allowlist: return "Step 4 of 5 • App Allowlist"
        case .aiContext: return "Step 5 of 5 • AI Learning"
        case .complete: return "Profile & Rules Ready!"
        }
    }
}

@Observable
@MainActor
public final class ParentAddChildViewModel {
    // Current Step
    public var currentStep: ParentAddChildStep = .profile

    // Profile inputs
    public var childDrafts: [ChildDraft] = []
    public var childName: String = ""
    public var ageBand: AgeBand = .band_7_9
    public var avatar: AvatarPreset = .rabbit
    public var curriculumTopics: Set<String> = ["math_foundations", "reading_fluency", "stem_logic"]

    // Timeline & Schedule inputs
    public var dailyBudgetMinutes: Int = 90
    public var quizFrequencyMinutes: Int = 30
    public var bedtimeEnabled: Bool = true
    public var bedtimeStart: String = "20:30"
    public var bedtimeEnd: String = "07:00"
    public var cooldownMinutes: Int = 10

    // Device Pairing inputs
    public var pairingCode: String = ""
    public var qrPayload: String = ""
    public var isDevicePaired: Bool = false
    public var isMintingPairingToken: Bool = false
    public var pairingSecondsRemaining: Int = 900

    // App Allowlist inputs
    public var appRules: [OnboardingAppRule] = OnboardingAppRule.defaultRules

    // AI Learning Context inputs
    public var aiPrompt: String = ""

    // State & Async Status
    public var isSaving: Bool = false
    public var savingStatusMessage: String = ""
    public var errorMessage: String? = nil
    public var createdProfile: FamilyChildProfile? = nil

    private let parentSessionRepo: ParentSessionRepository
    private let parentControlStore: ParentControlStoreProtocol
    private let pairingClient: PairingClientProtocol
    private let familyStore: FamilyStoreProtocol
    @ObservationIgnored private var devicePollingTask: Task<Void, Never>?

    public init(
        parentSessionRepo: ParentSessionRepository = .shared,
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared,
        pairingClient: PairingClientProtocol = FirebasePairingClient.shared,
        familyStore: FamilyStoreProtocol = FirestoreFamilyStore.shared
    ) {
        self.parentSessionRepo = parentSessionRepo
        self.parentControlStore = parentControlStore
        self.pairingClient = pairingClient
        self.familyStore = familyStore
        let initialCode = Self.generateDynamicPairingCode()
        self.pairingCode = initialCode
        self.qrPayload = "meritscreen://pair?code=\(initialCode)"

        if let stepIdx = ProcessInfo.processInfo.arguments.firstIndex(of: "-addChildStep"),
           stepIdx + 1 < ProcessInfo.processInfo.arguments.count {
            let stepArg = ProcessInfo.processInfo.arguments[stepIdx + 1]
            self.childName = "Leo"
            self.createdProfile = FamilyChildProfile(childId: "child_leo", displayName: "Leo", ageBand: .band_7_9, avatar: .rabbit)
            self.pairingCode = Self.generateDynamicPairingCode()
            self.qrPayload = "meritscreen://pair?code=\(self.pairingCode)&name=Leo"
            switch stepArg {
            case "profile": self.currentStep = .profile
            case "timeline": self.currentStep = .timeline
            case "pairing": self.currentStep = .pairing
            case "allowlist": self.currentStep = .allowlist
            case "aiContext": self.currentStep = .aiContext
            case "complete": self.currentStep = .complete
            default: break
            }
        }
    }

    public var gradeLabel: String {
        switch ageBand {
        case .band_3_6: return "Kindergarten"
        case .band_7_9: return "3rd Grade"
        case .band_10_12: return "6th Grade"
        }
    }

    public var gradeStandard: String {
        switch ageBand {
        case .band_3_6: return "Grade K"
        case .band_7_9: return "Grade 3"
        case .band_10_12: return "Grade 6"
        }
    }

    public var allowedAppsCount: Int {
        appRules.filter(\.isAllowed).count
    }

    private var activeFamilyId: String {
        parentSessionRepo.current()?.familyId ?? "sample_family"
    }

    // MARK: - Step 1: Create Child Profile
    public func createChildProfile(from draft: ChildDraft) async -> Bool {
        let trimmed = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Please enter your child's first name."
            return false
        }

        isSaving = true
        savingStatusMessage = "Creating \(trimmed)'s profile..."
        errorMessage = nil

        let familyDraft = FamilyDraftChild(
            localId: draft.id,
            name: trimmed,
            ageBand: draft.ageBand,
            avatar: draft.avatar
        )

        var targetFamilyId = activeFamilyId
        if (targetFamilyId.isEmpty || targetFamilyId == "sample_family"), let uid = FirebaseAuthClient.shared.currentUser?.uid {
            if let cloudFamilyId = try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: uid), !cloudFamilyId.isEmpty {
                targetFamilyId = cloudFamilyId
            } else {
                // If user is authenticated but has no family yet, create a real family in Firestore
                let draft = FamilyDraft(
                    familyName: "My Family",
                    parentPinHash: "",
                    children: [familyDraft]
                )
                if let created = try? await FirestoreFamilyStore.shared.createFamilyFromDraft(
                    uid: uid,
                    email: FirebaseAuthClient.shared.currentUser?.email,
                    draft: draft
                ) {
                    targetFamilyId = created.familyId
                }
            }
            let currentSession = parentSessionRepo.current()
            parentSessionRepo.set(ParentSession(uid: uid, familyId: targetFamilyId, childIds: currentSession?.childIds ?? []))
        }

        do {
            let profile = try await parentControlStore.addChild(familyId: targetFamilyId, child: familyDraft)
            createdProfile = profile
            childName = trimmed
            ageBand = draft.ageBand
            avatar = draft.avatar
            pairingCode = Self.generateDynamicPairingCode()
            qrPayload = "meritscreen://pair?code=\(pairingCode)&name=\(trimmed)"
            isSaving = false
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                currentStep = .timeline
            }
            Task {
                await mintPairingToken()
            }
            return true
        } catch {
            isSaving = false
            errorMessage = AppErrorMapper.map(error).userMessage
            return false
        }
    }

    // MARK: - Step 2: Save Timeline & Schedule
    public func saveTimelineSchedule(
        budgetMinutes: Int,
        quizFreqMinutes: Int,
        bedtimeOn: Bool,
        start: String,
        end: String,
        cooldown: Int
    ) async -> Bool {
        guard let profile = createdProfile else {
            errorMessage = "No active child profile to configure."
            return false
        }

        isSaving = true
        savingStatusMessage = "Configuring 24h schedule & bedtime..."
        errorMessage = nil

        dailyBudgetMinutes = budgetMinutes
        quizFrequencyMinutes = quizFreqMinutes
        bedtimeEnabled = bedtimeOn
        bedtimeStart = start
        bedtimeEnd = end
        cooldownMinutes = cooldown

        var targetFamilyId = activeFamilyId
        if (targetFamilyId.isEmpty || targetFamilyId == "sample_family"), let uid = FirebaseAuthClient.shared.currentUser?.uid {
            if let cloudFamilyId = try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: uid), !cloudFamilyId.isEmpty {
                targetFamilyId = cloudFamilyId
            }
        }

        do {
            var existingPolicy = try await parentControlStore.getPolicy(familyId: targetFamilyId, childId: profile.childId)
            existingPolicy.dailyCeilingMinutes = budgetMinutes
            existingPolicy.defaultBlockMinutes = quizFreqMinutes
            existingPolicy.bedtimeEnabled = bedtimeOn
            existingPolicy.bedtimeStartLabel = start
            existingPolicy.bedtimeEndLabel = end
            existingPolicy.defaultCooldownMinutes = cooldown
            existingPolicy.adaptiveDifficultyEnabled = true
            existingPolicy.aiQuizzesEnabled = true
            existingPolicy.curriculumFocusIds = Array(curriculumTopics)
            existingPolicy.gradeStandard = gradeStandard

            try await parentControlStore.updatePolicy(familyId: targetFamilyId, childId: profile.childId, policy: existingPolicy)
            isSaving = false
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                currentStep = .pairing
            }
            Task {
                await mintPairingToken()
            }
            return true
        } catch {
            isSaving = false
            errorMessage = AppErrorMapper.map(error).userMessage
            return false
        }
    }

    // MARK: - Step 3: Device Pairing Confirmation
    public func confirmPairing(simulated: Bool = false) {
        devicePollingTask?.cancel()
        isDevicePaired = true
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            currentStep = .allowlist
        }
    }

    public func skipPairing() {
        devicePollingTask?.cancel()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            currentStep = .allowlist
        }
    }

    // MARK: - Dynamic Pairing Token Operations
    public func mintPairingToken() async {
        isMintingPairingToken = true
        errorMessage = nil

        // Ensure parent has a valid Firebase Auth session before minting
        if FirebaseAuthClient.shared.currentUser == nil {
            _ = try? await FirebaseAuthClient.shared.ensureAuthenticatedParent()
        }

        var targetFamilyId: String? = nil
        if let uid = FirebaseAuthClient.shared.currentUser?.uid {
            targetFamilyId = try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: uid)
        }
        if targetFamilyId == nil || targetFamilyId == "sample_family" {
            targetFamilyId = (activeFamilyId == "sample_family") ? nil : activeFamilyId
        }

        var activeProfile = createdProfile
        if activeProfile == nil, let famId = targetFamilyId {
            if let children = try? await familyStore.listChildren(familyId: famId), let first = children.first {
                activeProfile = first
                self.createdProfile = first
                if self.childName.isEmpty {
                    self.childName = first.displayName
                }
            }
        }

        if activeProfile == nil, let famId = targetFamilyId {
            let fallbackName = self.childName.isEmpty ? "My Child" : self.childName
            let draft = FamilyDraftChild(
                localId: UUID().uuidString.lowercased(),
                name: fallbackName,
                ageBand: self.ageBand,
                avatar: self.avatar
            )
            if let created = try? await parentControlStore.addChild(familyId: famId, child: draft) {
                activeProfile = created
                self.createdProfile = created
            }
        }

        let childIdToMint = activeProfile?.childId ?? "child_leo"

        do {
            print("[ParentAddChildViewModel] Requesting pairing token for childId: \(childIdToMint) familyId: \(targetFamilyId ?? "nil")...")
            let tokenInfo = try await pairingClient.createPairingToken(childId: childIdToMint, familyId: targetFamilyId)
            print("[ParentAddChildViewModel] createPairingToken SUCCEEDED! code=\(tokenInfo.code)")
            self.pairingCode = tokenInfo.code // 6-digit numeric string from Cloud Functions
            self.qrPayload = tokenInfo.qrPayload
            let expiryDate = Date(timeIntervalSince1970: TimeInterval(tokenInfo.expiresAtEpochMs / 1000))
            self.pairingSecondsRemaining = max(0, Int(expiryDate.timeIntervalSinceNow))
            self.isMintingPairingToken = false
            if let currentSession = self.parentSessionRepo.current() {
                self.parentSessionRepo.set(ParentSession(
                    uid: currentSession.uid,
                    familyId: tokenInfo.familyId,
                    childIds: currentSession.childIds
                ))
            }
            startDevicePairingWatcher(familyId: tokenInfo.familyId, childId: childIdToMint)
        } catch {
            print("[ParentAddChildViewModel] mintPairingToken FAILED: \(error)")
            // Retry once after ensuring auth
            if let _ = try? await FirebaseAuthClient.shared.ensureAuthenticatedParent() {
                if let retried = try? await pairingClient.createPairingToken(childId: childIdToMint, familyId: targetFamilyId) {
                    print("[ParentAddChildViewModel] createPairingToken retry SUCCEEDED! code=\(retried.code)")
                    self.pairingCode = retried.code
                    self.qrPayload = retried.qrPayload
                    let expiryDate = Date(timeIntervalSince1970: TimeInterval(retried.expiresAtEpochMs / 1000))
                    self.pairingSecondsRemaining = max(0, Int(expiryDate.timeIntervalSinceNow))
                    self.isMintingPairingToken = false
                    startDevicePairingWatcher(familyId: retried.familyId, childId: childIdToMint)
                    return
                }
            }
            self.errorMessage = AppErrorMapper.map(error).userMessage
            self.isMintingPairingToken = false
        }
    }

    public func refreshPairingCode() async {
        await mintPairingToken()
    }

    private func startDevicePairingWatcher(familyId: String, childId: String) {
        devicePollingTask?.cancel()
        devicePollingTask = Task { @MainActor [weak self] in
            while let self = self, !self.isDevicePaired, self.currentStep == .pairing {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard !Task.isCancelled else { break }

                let checkFamId = familyId.isEmpty || familyId == "sample_family" ? self.activeFamilyId : familyId
                if let devices = try? await self.familyStore.listDevices(familyId: checkFamId, childId: childId),
                   devices.contains(where: { !$0.revoked }) {
                    self.confirmPairing()
                    break
                }
            }
        }
    }

    // MARK: - Step 4: Save App Allowlist
    public func saveAllowlist(rules: [OnboardingAppRule]) async -> Bool {
        guard let profile = createdProfile else {
            errorMessage = "No active child profile found."
            return false
        }

        isSaving = true
        savingStatusMessage = "Saving approved apps & safe list..."
        errorMessage = nil
        appRules = rules

        do {
            for rule in rules {
                let storeRule = AppRule(
                    appId: rule.id,
                    packageOrBundleId: rule.id,
                    displayName: rule.name,
                    allowed: rule.isAllowed,
                    blockMinutes: quizFrequencyMinutes,
                    grantOnPassMinutes: 15,
                    cooldownMinutes: cooldownMinutes,
                    isEmergency: rule.isSystemLocked
                )
                try await parentControlStore.upsertAppRule(
                    familyId: activeFamilyId,
                    childId: profile.childId,
                    rule: storeRule
                )
            }
            isSaving = false
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                currentStep = .aiContext
            }
            return true
        } catch {
            isSaving = false
            errorMessage = AppErrorMapper.map(error).userMessage
            return false
        }
    }

    // MARK: - Step 5: Save AI Learning Context
    public func saveAiLearningContext(prompt: String) async -> Bool {
        guard let profile = createdProfile else {
            errorMessage = "No active child profile found."
            return false
        }

        isSaving = true
        savingStatusMessage = "Calibrating AI micro-quiz generator..."
        errorMessage = nil
        aiPrompt = prompt

        do {
            var policy = try await parentControlStore.getPolicy(familyId: activeFamilyId, childId: profile.childId)
            policy.customPromptGuidelines = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
            policy.gradeStandard = gradeStandard
            policy.aiQuizzesEnabled = true
            policy.adaptiveDifficultyEnabled = true

            try await parentControlStore.updatePolicy(familyId: activeFamilyId, childId: profile.childId, policy: policy)
            isSaving = false
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                currentStep = .complete
            }
            return true
        } catch {
            isSaving = false
            errorMessage = AppErrorMapper.map(error).userMessage
            return false
        }
    }

    // MARK: - Navigation Back
    public func navigateBack() {
        errorMessage = nil
        devicePollingTask?.cancel()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            switch currentStep {
            case .profile:
                break
            case .timeline:
                currentStep = .profile
            case .pairing:
                currentStep = .timeline
            case .allowlist:
                currentStep = .pairing
            case .aiContext:
                currentStep = .allowlist
            case .complete:
                currentStep = .aiContext
            }
        }
    }

    deinit {
        devicePollingTask?.cancel()
    }

    // MARK: - Helpers
    /// Generates a purely numeric 6-digit dynamic pairing code (000000 - 999999).
    public static func generateDynamicPairingCode() -> String {
        String(format: "%06d", Int.random(in: 100000...999999))
    }
}
