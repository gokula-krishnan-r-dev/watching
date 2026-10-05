import SwiftUI

public enum OnboardingStep: CaseIterable, Sendable {
    case valueTour
    case addChild
    case timelineSchedule
    case deviceHandshake
    case appAllowlist
    case setParentPin
    case aiLearningContext
    case setupComplete
}

/// Orchestrates the complete Android-parity parent onboarding wizard:
/// Value Tour $\rightarrow$ Add First Child $\rightarrow$ Timeline & Bedtime Schedule $\rightarrow$
/// Device Handshake (QR) $\rightarrow$ App Allowlist Rules $\rightarrow$ Parent PIN $\rightarrow$
/// AI Learning Context $\rightarrow$ Setup Complete Celebration.
///
/// Fully operational offline. Draft is persisted in OnboardingDraftRepository at every step.
public struct OnboardingWizardView: View {
    public let onFinished: (OnboardingDraft) -> Void
    public let onCancel: () -> Void

    @State private var repository = OnboardingDraftRepository.shared
    @State private var draft: OnboardingDraft
    @State private var currentStep: OnboardingStep

    public init(
        onFinished: @escaping (OnboardingDraft) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.onFinished = onFinished
        self.onCancel = onCancel

        let initialDraft = OnboardingDraftRepository.shared.getDraft()
        var workingDraft = initialDraft
        let stepOverride: String? = {
            if let env = ProcessInfo.processInfo.environment["ONBOARDING_STEP"], !env.isEmpty {
                return env
            }
            if let idx = ProcessInfo.processInfo.arguments.firstIndex(of: "-onboardingStep"),
               idx + 1 < ProcessInfo.processInfo.arguments.count {
                return ProcessInfo.processInfo.arguments[idx + 1]
            }
            return nil
        }()

        if let stepArg = stepOverride {
            if workingDraft.children.isEmpty {
                workingDraft.children = [ChildDraft(name: "Maya", ageBand: .band_7_9, avatar: .rabbit)]
            }
            self._draft = State(initialValue: workingDraft)
            switch stepArg {
            case "valueTour": self._currentStep = State(initialValue: .valueTour)
            case "addChild": self._currentStep = State(initialValue: .addChild)
            case "timelineSchedule": self._currentStep = State(initialValue: .timelineSchedule)
            case "deviceHandshake": self._currentStep = State(initialValue: .deviceHandshake)
            case "appAllowlist": self._currentStep = State(initialValue: .appAllowlist)
            case "setParentPin": self._currentStep = State(initialValue: .setParentPin)
            case "aiLearningContext": self._currentStep = State(initialValue: .aiLearningContext)
            case "setupComplete": self._currentStep = State(initialValue: .setupComplete)
            default: self._currentStep = State(initialValue: .valueTour)
            }
        } else {
            self._draft = State(initialValue: workingDraft)
            // Determine resume step if draft was already partially started
            if workingDraft.children.isEmpty {
                self._currentStep = State(initialValue: .valueTour)
            } else if (workingDraft.parentPinHash ?? "").isEmpty {
                self._currentStep = State(initialValue: .setParentPin)
            } else if workingDraft.isReadyToCommit {
                self._currentStep = State(initialValue: .setupComplete)
            } else {
                self._currentStep = State(initialValue: .valueTour)
            }
        }
    }

    private var activeChild: ChildDraft {
        draft.children.first ?? ChildDraft(name: "your child", ageBand: .band_7_9, avatar: .rabbit)
    }

    public var body: some View {
        NavigationStack {
            Group {
                switch currentStep {
                case .valueTour:
                    P02_ValueTourView(
                        onContinue: {
                            currentStep = .addChild
                        },
                        onSkip: {
                            currentStep = .addChild
                        },
                        onBack: onCancel
                    )

                case .addChild:
                    P03_AddChildProfileView(
                        children: Binding(
                            get: { draft.children },
                            set: { newChildren in
                                draft.children = newChildren
                                repository.saveDraft(draft)
                            }
                        ),
                        onContinue: { child in
                            if draft.children.isEmpty {
                                draft.children = [child]
                            } else {
                                draft.children[0] = child
                            }
                            repository.saveDraft(draft)
                            currentStep = .timelineSchedule
                        },
                        onBack: {
                            currentStep = .valueTour
                        }
                    )

                case .timelineSchedule:
                    P05_TimelineScheduleView(
                        childName: activeChild.name,
                        avatar: activeChild.avatar,
                        initialBudgetMinutes: draft.dailyBudgetMinutes,
                        initialQuizFreqMinutes: draft.quizFrequencyMinutes,
                        initialBedtimeEnabled: draft.bedtimeEnabled,
                        initialBedtimeStart: draft.bedtimeStart,
                        initialBedtimeEnd: draft.bedtimeEnd,
                        initialCooldownMinutes: draft.cooldownMinutes,
                        onContinue: { budget, freq, bedtimeOn, start, end, cooldown in
                            draft.dailyBudgetMinutes = budget
                            draft.quizFrequencyMinutes = freq
                            draft.bedtimeEnabled = bedtimeOn
                            draft.bedtimeStart = start
                            draft.bedtimeEnd = end
                            draft.cooldownMinutes = cooldown
                            repository.setSchedule(
                                dailyBudgetMinutes: budget,
                                quizFrequencyMinutes: freq,
                                bedtimeEnabled: bedtimeOn,
                                bedtimeStart: start,
                                bedtimeEnd: end,
                                cooldownMinutes: cooldown
                            )
                            currentStep = .deviceHandshake
                        },
                        onBack: {
                            currentStep = .addChild
                        }
                    )

                case .deviceHandshake:
                    let pairingCode = draft.pairedDeviceToken ?? ParentAddChildViewModel.generateDynamicPairingCode()
                    P03c_DeviceHandshakeView(
                        childName: activeChild.name,
                        pairingCode: pairingCode,
                        onPaired: {
                            draft.isDevicePaired = true
                            repository.setPairedDevice(token: pairingCode, isPaired: true)
                            currentStep = .appAllowlist
                        },
                        onSkip: {
                            currentStep = .appAllowlist
                        },
                        onBack: {
                            currentStep = .timelineSchedule
                        }
                    )

                case .appAllowlist:
                    P04_AppAllowlistRulesView(
                        rules: Binding(
                            get: { draft.allowedApps },
                            set: { newRules in
                                draft.allowedApps = newRules
                                repository.setAppRules(newRules)
                            }
                        ),
                        onContinue: { updatedRules in
                            draft.allowedApps = updatedRules
                            repository.setAppRules(updatedRules)
                            currentStep = .setParentPin
                        },
                        onBack: {
                            currentStep = .deviceHandshake
                        }
                    )

                case .setParentPin:
                    P04b_SetParentPinView(
                        onComplete: { hash in
                            draft.parentPinHash = hash
                            draft.consentGiven = true
                            repository.saveDraft(draft)
                            currentStep = .aiLearningContext
                        },
                        onBack: {
                            currentStep = .appAllowlist
                        }
                    )

                case .aiLearningContext:
                    P03d_AiLearningContextView(
                        childName: activeChild.name,
                        grade: activeChild.ageBand.displayLabel,
                        avatar: activeChild.avatar,
                        initialPrompt: draft.aiLearningPrompt,
                        onContinue: { prompt in
                            draft.aiLearningPrompt = prompt
                            repository.setAiLearningContext(prompt: prompt, topics: draft.curriculumFocusIds)
                            currentStep = .setupComplete
                        },
                        onBack: {
                            currentStep = .setParentPin
                        }
                    )

                case .setupComplete:
                    P06_SetupCompleteView(
                        childName: activeChild.name,
                        grade: activeChild.ageBand.displayLabel,
                        avatar: activeChild.avatar,
                        allowanceMinutes: draft.dailyBudgetMinutes,
                        allowlistCount: draft.allowedApps.filter { $0.isAllowed }.count,
                        quizIntervalMinutes: draft.quizFrequencyMinutes,
                        cooldownMinutes: draft.cooldownMinutes,
                        bedtimeRange: "\(draft.bedtimeStart) – \(draft.bedtimeEnd)",
                        autoRedirect: false,
                        onOpenDashboard: {
                            repository.saveDraft(draft)
                            onFinished(draft)
                        }
                    )
                }
            }
            .animation(.easeInOut(duration: 0.25), value: currentStep)
        }
    }
}
