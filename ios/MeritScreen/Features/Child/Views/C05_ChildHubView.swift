import SwiftUI

/// Screen C05: Child Hub Control Center.
/// Serves as the child status hub on iOS (alongside SpringBoard).
/// Adaptive layout supporting iPhone and iPad in portrait and landscape.
public struct C05_ChildHubView: View {
    public let onResetRole: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var viewModel = ChildHubViewModel()
    @State private var quizFlowViewModel: QuizFlowViewModel?
    @State private var failLockViewModel: FailLockViewModel?
    @State private var showingParentPinForMenu = false
    @State private var authManager = ScreenTimeAuthorizationManager.shared
    @State private var showingScreenTimeSetup = false

    public init(onResetRole: @escaping () -> Void) {
        self.onResetRole = onResetRole
    }

    public var body: some View {
        NavigationStack {
            mainScrollView
                .navigationTitle("Watching Hub")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button {
                            showingParentPinForMenu = true
                        } label: {
                            Image(systemName: "gearshape.fill")
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                }
                .onAppear(perform: handleAppear)
                .onDisappear { viewModel.stopClock() }
                .onChange(of: scenePhase, handleScenePhaseChange)
                .onReceive(NotificationCenter.default.publisher(for: .childDeviceRevoked)) { _ in onResetRole() }
                .onReceive(NotificationCenter.default.publisher(for: .childFamilyDeleted)) { _ in onResetRole() }
                .onChange(of: viewModel.showingFailLock, handleFailLockChange)
                .fullScreenCover(isPresented: $viewModel.showingFailLock) { failLockCover }
                .fullScreenCover(isPresented: $viewModel.showingBedtimeLock) { bedtimeLockCover }
                .fullScreenCover(isPresented: $viewModel.showingDailyLimitLock) { dailyLimitLockCover }
                .fullScreenCover(isPresented: isShowingQuizBinding) { quizCover }
                .sheet(isPresented: $viewModel.showingParentPinSheet) { pinOverrideSheet }
                .sheet(isPresented: $showingParentPinForMenu) { pinSettingsSheet }
                .sheet(isPresented: $viewModel.showingSettingsMenu) { settingsSheet }
                .sheet(isPresented: $viewModel.showingStickerBook) { stickerBookSheet }
                .sheet(isPresented: $showingScreenTimeSetup) { screenTimeSetupSheet }
        }
    }

    private var mainScrollView: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Header: Child Info & Sticker Button
                headerSection

                // Screen Time Setup Checklist Banner (if unauthorized OR no apps selected)
                let activationCoordinator = ScreenTimeActivationCoordinator.shared
                if !authManager.isAuthorized || !activationCoordinator.isActivated {
                    screenTimeSetupCard
                }

                // Active Block Status Card
                activeBlockCard

                // Daily Ceiling Status
                if viewModel.snapshot.minutesUsedToday >= (viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) {
                    C13_DailyCeilingBanner(
                        minutesUsedToday: viewModel.snapshot.minutesUsedToday,
                        dailyCeilingMinutes: viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes,
                        onOpenPin: { viewModel.showingParentPinSheet = true }
                    )
                } else {
                    dailyCeilingCard
                }

                // Approved Apps Section
                approvedAppsSection

                // Sticker Collection Shortcut
                stickerShortcutCard

                // Parent Lock Entry
                MeritButton(
                    "Parent Controls (PIN)",
                    icon: "lock.fill",
                    style: .secondary
                ) {
                    showingParentPinForMenu = true
                }
                .padding(.top, MeritSpacing.small)
                .padding(.bottom, MeritSpacing.xLarge)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 620)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }

    @ViewBuilder
    private var failLockCover: some View {
        let failVM = failLockViewModel ?? FailLockViewModel(childName: viewModel.childName)
        C12_FailLockView(
            viewModel: failVM,
            onRetryQuiz: {
                let rule = viewModel.appRules.first { $0.allowed && !$0.isEmergency } ??
                    AppRule(appId: "com.watching.app", packageOrBundleId: "com.watching.app", displayName: "Apps")
                quizFlowViewModel = QuizFlowViewModel(
                    targetRule: rule,
                    isRetryMode: true,
                    childName: viewModel.childName,
                    ageBand: viewModel.ageBand
                )
            },
            onOpenParentPin: {
                viewModel.showingParentPinSheet = true
            },
            onCooldownFinished: {
                viewModel.onFailLockCooldownFinished()
            }
        )
    }

    @ViewBuilder
    private var bedtimeLockCover: some View {
        C16_BedtimeLockView(
            childName: viewModel.childName,
            bedtimeEndLabel: viewModel.policy.bedtimeEndLabel,
            onOpenParentPin: {
                viewModel.showingParentPinSheet = true
            }
        )
    }

    @ViewBuilder
    private var dailyLimitLockCover: some View {
        let ceiling = viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        C17_DailyLimitLockView(
            childName: viewModel.childName,
            minutesUsedToday: viewModel.snapshot.minutesUsedToday,
            dailyCeilingMinutes: ceiling,
            onGrantBonus: { bonusMinutes in
                viewModel.grantDailyBonusWithParentPin(minutes: bonusMinutes)
            },
            onOpenParentPin: {
                viewModel.showingDailyLimitLock = false
            },
            onEmergencyCall: {
                if let url = URL(string: "tel:"), UIApplication.shared.canOpenURL(url) {
                    UIApplication.shared.open(url)
                }
            }
        )
    }

    private var isShowingQuizBinding: Binding<Bool> {
        Binding(
            get: { quizFlowViewModel != nil || viewModel.showingQuizInterrupt },
            set: { if !$0 { quizFlowViewModel = nil; viewModel.showingQuizInterrupt = false } }
        )
    }

    @ViewBuilder
    private var quizCover: some View {
        let activeQuizVM = quizFlowViewModel ?? QuizFlowViewModel(
            targetRule: viewModel.quizInterruptRule ?? (viewModel.appRules.first { $0.allowed && !$0.isEmergency } ??
                AppRule(appId: "com.watching.app", packageOrBundleId: "com.watching.app", displayName: "Apps")),
            isRetryMode: viewModel.snapshot.phase == .shielded,
            childName: viewModel.childName,
            ageBand: viewModel.ageBand
        )
        QuizCoordinatorView(
            viewModel: activeQuizVM,
            onComplete: {
                quizFlowViewModel = nil
                viewModel.showingQuizInterrupt = false
                viewModel.loadLocalData()
            },
            onDismiss: {
                quizFlowViewModel = nil
                viewModel.showingQuizInterrupt = false
                viewModel.loadLocalData()
            }
        )
    }

    @ViewBuilder
    private var pinOverrideSheet: some View {
        C14_ParentPinSheet(
            onSuccess: {
                let ceiling = viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
                if viewModel.showingDailyLimitLock || viewModel.snapshot.minutesUsedToday >= ceiling {
                    viewModel.grantDailyBonusWithParentPin(minutes: 15)
                } else {
                    viewModel.endFailLockWithParentPin()
                }
            },
            onDismiss: {
                viewModel.showingParentPinSheet = false
            }
        )
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var pinSettingsSheet: some View {
        C14_ParentPinSheet(
            onSuccess: {
                showingParentPinForMenu = false
                viewModel.showingSettingsMenu = true
            },
            onDismiss: {
                showingParentPinForMenu = false
            }
        )
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var settingsSheet: some View {
        C15_ChildSettingsMenu(
            viewModel: viewModel,
            onUnpair: {
                viewModel.showingSettingsMenu = false
                onResetRole()
            },
            onDismiss: {
                viewModel.showingSettingsMenu = false
            }
        )
    }

    @ViewBuilder
    private var stickerBookSheet: some View {
        ChildStickerBookView(
            stickers: viewModel.unlockedStickers,
            totalXp: viewModel.totalXp,
            explorerLevel: viewModel.explorerLevel,
            onDismiss: {
                viewModel.showingStickerBook = false
            }
        )
    }

    @ViewBuilder
    private var screenTimeSetupSheet: some View {
        I01_ScreenTimeSetupView(onComplete: {
            showingScreenTimeSetup = false
            authManager.refreshStatus()
        })
    }

    private func handleAppear() {
        viewModel.loadLocalData()
        viewModel.startActiveBlockIfNeeded()
        viewModel.startClock()
        StartupPerformanceCoordinator.shared.markFirstFrameRendered()
        authManager.refreshStatus()
        checkPendingQuizFromExtension()
        checkDeviceRevocation()
        ScreenTimeEnforcementController.shared.reconcileShieldsOnLaunch()

        // Sync policy values to shared store so DeviceActivity extension reads correct block minutes
        syncPolicyToSharedStore()

        // Reactivate monitoring from saved selection (handles reboots, app updates)
        if authManager.isAuthorized {
            ScreenTimeActivationCoordinator.shared.reactivateFromSavedPolicy(
                blockMinutes: viewModel.policy.defaultBlockMinutes > 0 ? viewModel.policy.defaultBlockMinutes : 30,
                cooldownMinutes: viewModel.policy.defaultCooldownMinutes > 0 ? viewModel.policy.defaultCooldownMinutes : 15,
                childName: viewModel.childName
            )
        }

        StartupPerformanceCoordinator.shared.deferUntilFirstFrameRendered {
            _ = await DeviceHeartbeatCoordinator.shared.performHeartbeat()
            _ = await UsageSyncCoordinator.shared.syncUsageAndAttempts()
        }
    }

    private func syncPolicyToSharedStore() {
        let store = ScreenTimeSharedStore.shared
        store.saveBlockMinutes(viewModel.policy.defaultBlockMinutes > 0 ? viewModel.policy.defaultBlockMinutes : 30)
        store.saveCooldownMinutes(viewModel.policy.defaultCooldownMinutes > 0 ? viewModel.policy.defaultCooldownMinutes : 15)
        store.saveChildName(viewModel.childName)
    }

    private func handleScenePhaseChange(oldPhase: ScenePhase, newPhase: ScenePhase) {
        switch newPhase {
        case .active:
            viewModel.startActiveBlockIfNeeded()
            authManager.refreshStatus()
            checkPendingQuizFromExtension()
            checkDeviceRevocation()
            ScreenTimeEnforcementController.shared.reconcileShieldsOnLaunch()
            syncPolicyToSharedStore()
            if authManager.isAuthorized {
                ScreenTimeActivationCoordinator.shared.reactivateFromSavedPolicy(
                    blockMinutes: viewModel.policy.defaultBlockMinutes > 0 ? viewModel.policy.defaultBlockMinutes : 30,
                    cooldownMinutes: viewModel.policy.defaultCooldownMinutes > 0 ? viewModel.policy.defaultCooldownMinutes : 15,
                    childName: viewModel.childName
                )
            }
            StartupPerformanceCoordinator.shared.deferUntilFirstFrameRendered {
                _ = await DeviceHeartbeatCoordinator.shared.performHeartbeat()
                _ = await UsageSyncCoordinator.shared.syncUsageAndAttempts()
            }
        case .background:
            viewModel.flushPendingSessionState()
        default:
            break
        }
    }

    private func handleFailLockChange(oldVal: Bool, isLocked: Bool) {
        if isLocked {
            ScreenTimeEnforcementController.shared.applyDeviceWideFailLock(
                cooldownMinutes: viewModel.policy.defaultCooldownMinutes
            )
        } else {
            ScreenTimeEnforcementController.shared.clearFailLock()
        }
    }

    // MARK: - Subviews

    private var headerSection: some View {
        HStack(spacing: MeritSpacing.medium) {
            Text(viewModel.childAvatar)
                .font(.system(size: 48))
                .frame(width: 72, height: 72)
                .background(MeritColor.secondaryFill)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text("Hi, \(viewModel.childName)!")
                    .font(MeritTypography.title2)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)

                HStack(spacing: MeritSpacing.xSmall) {
                    Text("🌟 Explorer Level \(viewModel.explorerLevel)")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)

                    Text("•")
                        .foregroundColor(MeritColor.secondaryLabel)

                    Text("\(viewModel.totalXp) XP")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
            }

            Spacer()

            Button {
                viewModel.showingStickerBook = true
            } label: {
                VStack(spacing: 2) {
                    Text("🏆")
                        .font(.system(size: 24))
                    Text("Stickers")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
            }
        }
        .padding(.top, MeritSpacing.medium)
    }

    private var activeBlockCard: some View {
        let isDailyLimit = viewModel.isDailyLimitLocked
        return VStack(spacing: MeritSpacing.medium) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(isDailyLimit ? "Daily Limit Reached" : "Current App Block")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text(isDailyLimit ? "Device Locked Until Tomorrow" : viewModel.activeAppName)
                        .font(MeritTypography.subheadline)
                        .foregroundColor(isDailyLimit ? .orange : MeritColor.accent)
                }

                Spacer()

                HStack(spacing: 4) {
                    Circle()
                        .fill(isDailyLimit ? Color.orange : (viewModel.snapshot.phase == .inBlock ? Color.green : MeritColor.secondaryLabel))
                        .frame(width: 8, height: 8)
                    Text(isDailyLimit ? "Locked" : viewModel.snapshot.phase.displayLabel)
                        .font(MeritTypography.caption)
                        .foregroundColor(isDailyLimit ? .orange : MeritColor.secondaryLabel)
                }
            }

            if isDailyLimit {
                VStack(spacing: MeritSpacing.small) {
                    Text("🔒 0m 00s")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundColor(.orange)

                    Text("Device resting mode active. Screen time limit reached for today. Non-emergency apps are resting.")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.small)

                    Button {
                        viewModel.showingParentPinSheet = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "key.fill")
                                .font(.system(size: 13, weight: .semibold))
                            Text("Parent Unlock / +15m Bonus")
                                .font(MeritTypography.subheadline)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.orange)
                        .clipShape(Capsule())
                    }
                    .padding(.top, 4)
                }
            } else {
                Text(viewModel.remainingBlockFormatted)
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundColor(MeritColor.label)

                ProgressView(value: viewModel.remainingBlockProgress, total: 1.0)
                    .tint(MeritColor.accent)

                Text("Open approved apps from your Home Screen. When your block ends, Watching will give you a quick quiz to unlock more time.")
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(MeritSpacing.large)
        .frame(maxWidth: .infinity)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private var dailyCeilingCard: some View {
        let isDailyLimit = viewModel.isDailyLimitLocked
        return HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text("Daily Screen Time")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                    if isDailyLimit {
                        Text("• Limit Reached")
                            .font(MeritTypography.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                    }
                }

                Text(viewModel.dailyRemainingFormatted)
                    .font(MeritTypography.headline)
                    .foregroundColor(isDailyLimit ? .orange : MeritColor.label)
            }

            Spacer()

            if isDailyLimit {
                Button {
                    viewModel.showingDailyLimitLock = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 14))
                        Text("View Lock")
                            .font(MeritTypography.caption)
                            .fontWeight(.semibold)
                    }
                    .foregroundColor(.orange)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.orange.opacity(0.15))
                    .clipShape(Capsule())
                }
            } else {
                ProgressView(value: viewModel.dailyCeilingProgress, total: 1.0)
                    .progressViewStyle(CircularProgressViewStyle())
                    .tint(MeritColor.accent)
            }
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private var approvedAppsSection: some View {
        let isDailyLimit = viewModel.isDailyLimitLocked
        return VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            HStack {
                Text("Approved Apps")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)

                Spacer()

                if isDailyLimit {
                    Text("Non-emergency locked")
                        .font(MeritTypography.caption)
                        .foregroundColor(.orange)
                }
            }

            VStack(spacing: MeritSpacing.small) {
                ForEach(viewModel.appRules.filter { $0.allowed }) { rule in
                    let isLocked = isDailyLimit && !rule.isEmergency
                    HStack(spacing: 12) {
                        // Professional App Icon
                        AppIconView(rule: rule, size: 48)

                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(rule.displayName)
                                    .font(MeritTypography.headline)
                                    .foregroundColor(isLocked ? MeritColor.secondaryLabel : MeritColor.label)

                                if rule.isEmergency {
                                    Text("ALWAYS ON")
                                        .font(.system(size: 9, weight: .heavy))
                                        .foregroundColor(.green)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.12))
                                        .clipShape(Capsule())
                                }
                            }

                            Text(rule.isEmergency ? "Emergency & Family Line" : (isLocked ? "Daily Limit Reached • Resting" : "\(rule.blockMinutes) min block"))
                                .font(MeritTypography.caption)
                                .foregroundColor(isLocked ? .orange : (rule.isEmergency ? .green : MeritColor.secondaryLabel))
                        }

                        Spacer()

                        if isLocked {
                            HStack(spacing: 4) {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 11, weight: .bold))
                                Text("Locked")
                                    .font(MeritTypography.caption)
                                    .fontWeight(.bold)
                            }
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(MeritColor.secondaryFill)
                            .clipShape(Capsule())
                        } else {
                            Button(action: {
                                viewModel.requestOpenApp(rule: rule)
                            }) {
                                HStack(spacing: 4) {
                                    Image(systemName: rule.isEmergency ? "phone.fill" : "arrow.up.right.square.fill")
                                        .font(.system(size: 11, weight: .semibold))
                                    Text(rule.isEmergency ? "Call" : "Launch")
                                        .font(MeritTypography.callout)
                                        .fontWeight(.semibold)
                                }
                                .foregroundColor(rule.isEmergency ? .white : MeritColor.accent)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(rule.isEmergency ? Color.green : MeritColor.accent.opacity(0.12))
                                .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                    .opacity(isLocked ? 0.75 : 1.0)
                }
            }
        }
    }

    private var stickerShortcutCard: some View {
        Button {
            viewModel.showingStickerBook = true
        } label: {
            HStack(spacing: MeritSpacing.medium) {
                Text("🏆")
                    .font(.system(size: 32))

                VStack(alignment: .leading, spacing: 2) {
                    Text("My Sticker Collection")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text("\(viewModel.unlockedStickers.count) badges earned • Tap to view")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundColor(MeritColor.secondaryLabel)
            }
            .padding(MeritSpacing.large)
            .background(MeritColor.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var screenTimeSetupCard: some View {
        Button {
            showingScreenTimeSetup = true
        } label: {
            HStack(spacing: MeritSpacing.medium) {
                ZStack {
                    Circle()
                        .fill(MeritColor.warning.opacity(0.15))
                        .frame(width: 44, height: 44)
                    Image(systemName: "hourglass.badge.plus")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(MeritColor.warning)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Screen Time Setup Required")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text("Tap to authorize Screen Time controls so Watching can manage screen limits.")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.leading)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundColor(MeritColor.secondaryLabel)
            }
            .padding(MeritSpacing.large)
            .background(MeritColor.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                    .stroke(MeritColor.warning.opacity(0.4), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Screen Time Extension Integration

    private func checkPendingQuizFromExtension() {
        if let request = ScreenTimeSharedStore.shared.consumePendingQuizRequest() {
            let rule = viewModel.appRules.first { $0.allowed && !$0.isEmergency } ??
                AppRule(appId: "com.watching.app", packageOrBundleId: "com.watching.app", displayName: "Apps")
            let isRetry = request.isRetry || viewModel.showingFailLock || viewModel.snapshot.phase == .shielded
            quizFlowViewModel = QuizFlowViewModel(
                targetRule: rule,
                isRetryMode: isRetry,
                childName: viewModel.childName,
                ageBand: viewModel.ageBand
            )
        }
    }

    private func checkDeviceRevocation() {
        if ScreenTimeSharedStore.shared.isDeviceRevoked() {
            ScreenTimeSharedStore.shared.setDeviceRevoked(false)
            onResetRole()
        }
    }
}

/// Helper coordinator rendering the multi-step quiz flow in a clean container.
struct QuizCoordinatorView: View {
    @Bindable var viewModel: QuizFlowViewModel
    let onComplete: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.step {
                case .intro:
                    C07_QuizInterruptView(
                        childName: viewModel.childName,
                        appRule: viewModel.targetRule,
                        totalQuestions: viewModel.totalQuestions,
                        minutesToEarn: viewModel.targetRule.grantOnPassMinutes,
                        isRetryMode: viewModel.isRetryMode,
                        onStartQuiz: {
                            viewModel.startQuiz()
                        },
                        onDismiss: {
                            onDismiss()
                        }
                    )

                case .question:
                    if let q = viewModel.currentQuestion {
                        C09_QuizQuestionView(
                            question: q,
                            questionIndex: viewModel.currentQuestionIndex,
                            totalQuestions: viewModel.totalQuestions,
                            selectedChoiceId: viewModel.selectedChoiceId,
                            ageBand: viewModel.ageBand,
                            onSelectChoice: { choiceId in
                                viewModel.selectChoice(choiceId)
                            },
                            onSubmit: {
                                viewModel.submitAnswer()
                            }
                        )
                    }

                case .explanation:
                    if let q = viewModel.currentQuestion, let fb = viewModel.feedback {
                        C10_QuizExplanationView(
                            feedback: fb,
                            question: q,
                            isLastQuestion: viewModel.currentQuestionIndex + 1 >= viewModel.questions.count,
                            onContinue: {
                                viewModel.advanceToNext()
                            }
                        )
                    }

                case .result:
                    if let res = viewModel.finalResult {
                        C11_QuizResultView(
                            childName: viewModel.childName,
                            result: res,
                            appLabel: viewModel.targetRule.displayName,
                            unlockedSticker: viewModel.unlockedSticker,
                            onFinish: {
                                onComplete()
                            }
                        )
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
