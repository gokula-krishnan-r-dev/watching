import SwiftUI

/// Upgraded Production-Ready Multi-Step "Add Child" Wizard for Authenticated Parents.
/// Mirrors `com.meritscreen.feature.parent.ParentNavigation` in Android:
/// 1. Child Profile & Curiosity Topics
/// 2. 24h Timeline, Play Budget & Bedtime Blackout
/// 3. Device Handshake (Live QR & 6-Digit PIN)
/// 4. App Allowlist Rules & Categories
/// 5. AI Learning Context Customizer
/// 6. Setup Complete Bento Celebration
///
/// Features real-time Firestore persistence, loading spinners, network error recovery,
/// and responsive Calm Horizon styling across iPhone and iPad.
public struct AddChildSheet: View {
    @Environment(\.dismiss) private var dismiss

    @State private var viewModel: ParentAddChildViewModel
    @State private var showDiscardAlert: Bool = false

    public let onFinished: ((FamilyChildProfile?) -> Void)?
    public let legacyOnChildAdded: ((String, AgeBand, AvatarPreset) async -> Bool)?

    /// Primary production initializer: notifies with the created profile upon finishing the full wizard.
    public init(onFinished: ((FamilyChildProfile?) -> Void)? = nil) {
        self._viewModel = State(initialValue: ParentAddChildViewModel())
        self.onFinished = onFinished
        self.legacyOnChildAdded = nil
    }

    /// Backwards-compatible initializer for legacy callers.
    public init(onChildAdded: @escaping (String, AgeBand, AvatarPreset) async -> Bool) {
        self._viewModel = State(initialValue: ParentAddChildViewModel())
        self.onFinished = nil
        self.legacyOnChildAdded = onChildAdded
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                MeritColor.background
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Error Banner if an operation fails
                    if let error = viewModel.errorMessage {
                        errorBanner(error)
                    }

                    // Wizard Step Content
                    stepContent
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                }
                .responsiveContainer(maxWidth: 540)

                // Real-time Loading & Saving Overlay
                if viewModel.isSaving {
                    savingOverlay
                }
            }
            .confirmationDialog(
                "Discard Child Setup?",
                isPresented: $showDiscardAlert,
                titleVisibility: .visible
            ) {
                Button("Discard and Exit", role: .destructive) {
                    dismiss()
                }
                Button("Keep Editing", role: .cancel) {}
            } message: {
                Text("Are you sure you want to stop? Progress for this child's rules will not be saved.")
            }
        }
    }



    // MARK: - Step Router
    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .profile:
            P03_AddChildProfileView(
                children: $viewModel.childDrafts,
                onContinue: { draft in
                    Task {
                        // If legacy callback is set, invoke it as well
                        if let legacy = legacyOnChildAdded {
                            _ = await legacy(draft.name, draft.ageBand, draft.avatar)
                        }
                        _ = await viewModel.createChildProfile(from: draft)
                    }
                },
                onBack: {
                    handleCloseTapped()
                }
            )

        case .timeline:
            P05_TimelineScheduleView(
                childName: viewModel.childName,
                avatar: viewModel.avatar,
                initialBudgetMinutes: viewModel.dailyBudgetMinutes,
                initialQuizFreqMinutes: viewModel.quizFrequencyMinutes,
                initialBedtimeEnabled: viewModel.bedtimeEnabled,
                initialBedtimeStart: viewModel.bedtimeStart,
                initialBedtimeEnd: viewModel.bedtimeEnd,
                initialCooldownMinutes: viewModel.cooldownMinutes,
                onContinue: { budget, freq, bedtimeOn, start, end, cooldown in
                    Task {
                        _ = await viewModel.saveTimelineSchedule(
                            budgetMinutes: budget,
                            quizFreqMinutes: freq,
                            bedtimeOn: bedtimeOn,
                            start: start,
                            end: end,
                            cooldown: cooldown
                        )
                    }
                },
                onBack: {
                    viewModel.navigateBack()
                }
            )

        case .pairing:
            P03c_DeviceHandshakeView(
                childName: viewModel.childName,
                pairingCode: viewModel.pairingCode,
                qrPayload: viewModel.qrPayload.isEmpty ? nil : viewModel.qrPayload,
                isRefreshing: viewModel.isMintingPairingToken,
                onRefresh: {
                    Task {
                        await viewModel.refreshPairingCode()
                    }
                },
                onPaired: {
                    viewModel.confirmPairing()
                },
                onSkip: {
                    viewModel.skipPairing()
                },
                onBack: {
                    viewModel.navigateBack()
                }
            )
            .task {
                await viewModel.mintPairingToken()
            }

        case .allowlist:
            P04_AppAllowlistRulesView(
                rules: $viewModel.appRules,
                ctaTitle: "Continue to AI Learning",
                ctaIcon: "brain.head.profile",
                stepPillLabel: "Step 4 of 5 • App Rules",
                onContinue: { updatedRules in
                    Task {
                        _ = await viewModel.saveAllowlist(rules: updatedRules)
                    }
                },
                onBack: {
                    viewModel.navigateBack()
                }
            )

        case .aiContext:
            P03d_AiLearningContextView(
                childName: viewModel.childName,
                grade: viewModel.gradeLabel,
                avatar: viewModel.avatar,
                initialPrompt: viewModel.aiPrompt,
                stepPillLabel: "Step 5 of 5 • AI Learning",
                onContinue: { prompt in
                    Task {
                        _ = await viewModel.saveAiLearningContext(prompt: prompt)
                    }
                },
                onBack: {
                    viewModel.navigateBack()
                }
            )

        case .complete:
            P06_SetupCompleteView(
                childName: viewModel.childName,
                grade: viewModel.gradeLabel,
                avatar: viewModel.avatar,
                allowanceMinutes: viewModel.dailyBudgetMinutes,
                allowlistCount: viewModel.allowedAppsCount,
                quizIntervalMinutes: viewModel.quizFrequencyMinutes,
                cooldownMinutes: viewModel.cooldownMinutes,
                bedtimeRange: "\(viewModel.bedtimeStart) – \(viewModel.bedtimeEnd)",
                autoRedirect: false,
                onOpenDashboard: {
                    onFinished?(viewModel.createdProfile)
                    dismiss()
                }
            )
        }
    }

    // MARK: - Error Banner
    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)
                .font(.system(size: 18))

            Text(message)
                .font(MeritTypography.footnote)
                .foregroundColor(MeritColor.label)
                .lineLimit(3)

            Spacer()

            Button(action: { viewModel.errorMessage = nil }) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(MeritColor.secondaryLabel)
                    .padding(6)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.red.opacity(0.12))
        .cornerRadius(MeritSpacing.radiusMedium)
        .padding(.horizontal, MeritSpacing.large)
        .padding(.top, MeritSpacing.small)
    }

    // MARK: - Saving Indicator Overlay
    private var savingOverlay: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()

            VStack(spacing: MeritSpacing.medium) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: MeritColor.accent))
                    .scaleEffect(1.4)

                Text(viewModel.savingStatusMessage)
                    .font(MeritTypography.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
            .background(MeritColor.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
            .shadow(color: Color.black.opacity(0.15), radius: 20, x: 0, y: 10)
        }
    }

    // MARK: - Actions
    private func handleCloseTapped() {
        if viewModel.currentStep == .profile && viewModel.childName.isEmpty {
            dismiss()
        } else {
            showDiscardAlert = true
        }
    }
}
