import SwiftUI
import FamilyControls
import ManagedSettings

/// C02b: Mandatory permissions onboarding shown immediately after child device pairing.
/// Guides the child/parent through:
///   1. Screen Time (FamilyControls) authorization
///   2. FamilyActivityPicker — select which apps to monitor
///   3. Notification permission (so Watching can wake the app for quiz alerts)
///
/// This screen must be completed before the child reaches the ChildHubView.
/// It cannot be skipped — Screen Time enforcement will not work without authorization.
public struct C02b_PermissionsOnboardingView: View {
    public let credential: ChildPairingCredential
    public let policy: ChildPolicy
    public let appRules: [AppRule]
    public let onComplete: () -> Void

    @State private var step: Step = .intro
    @State private var activationCoordinator = ScreenTimeActivationCoordinator.shared
    @State private var activitySelection = FamilyActivitySelection()
    @State private var showingActivityPicker = false
    @State private var isActivating = false
    @State private var notificationGranted = false
    @State private var errorMessage: String?

    enum Step {
        case intro
        case screenTimeAuth
        case appSelection
        case notifications
        case activating
        case done
    }

    public init(
        credential: ChildPairingCredential,
        policy: ChildPolicy,
        appRules: [AppRule],
        onComplete: @escaping () -> Void
    ) {
        self.credential = credential
        self.policy = policy
        self.appRules = appRules
        self.onComplete = onComplete
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Progress bar
                progressBar

                ScrollView {
                    VStack(spacing: MeritSpacing.xLarge) {
                        switch step {
                        case .intro:
                            introContent
                        case .screenTimeAuth:
                            screenTimeAuthContent
                        case .appSelection:
                            appSelectionContent
                        case .notifications:
                            notificationsContent
                        case .activating:
                            activatingContent
                        case .done:
                            doneContent
                        }

                        if let err = errorMessage {
                            Text(err)
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.destructive)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, MeritSpacing.large)
                        }
                    }
                    .padding(.horizontal, MeritSpacing.large)
                    .padding(.vertical, MeritSpacing.large)
                    .responsiveContainer(maxWidth: 520)
                }
            }
            .background(MeritColor.groupedBackground.ignoresSafeArea())
            .navigationTitle("Device Setup")
            .navigationBarTitleDisplayMode(.inline)
        }
        .familyActivityPicker(isPresented: $showingActivityPicker, selection: $activitySelection)
        .onChange(of: showingActivityPicker) { _, isShowing in
            if !isShowing {
                // Picker was dismissed — proceed to notifications step
                step = .notifications
            }
        }
    }

    // MARK: - Progress Bar

    private var progressBar: some View {
        HStack(spacing: 4) {
            ForEach(0..<4) { idx in
                let isCompleted = stepIndex > idx
                let isCurrent = stepIndex == idx
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(isCompleted || isCurrent ? MeritColor.accent : MeritColor.secondaryFill)
                    .frame(height: 4)
                    .opacity(isCompleted ? 1.0 : (isCurrent ? 0.8 : 0.35))
            }
        }
        .padding(.horizontal, MeritSpacing.large)
        .padding(.top, MeritSpacing.medium)
        .animation(.easeInOut, value: step)
    }

    private var stepIndex: Int {
        switch step {
        case .intro: return 0
        case .screenTimeAuth: return 1
        case .appSelection: return 2
        case .notifications: return 2
        case .activating: return 3
        case .done: return 4
        }
    }

    // MARK: - Step: Intro

    private var introContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            iconBadge("checkmark.seal.fill", color: MeritColor.pass)

            VStack(spacing: MeritSpacing.small) {
                Text("Welcome, \(credential.displayName)! 🎉")
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text("Let's set up Screen Time supervision so Watching can manage your daily app limits.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                featureRow(icon: "timer", title: "Timed Learning Blocks",
                           desc: "Use apps in focused blocks. Earn more time by answering quick questions.")
                featureRow(icon: "shield.fill", title: "Smart App Shield",
                           desc: "When your time is up, apps pause. No ads, no spam — just a quiz.")
                featureRow(icon: "moon.stars.fill", title: "Bedtime Calm",
                           desc: "Non-essential apps rest during bedtime. Emergency contacts always available.")
                featureRow(icon: "lock.shield.fill", title: "Zero Snooping",
                           desc: "Watching cannot read messages, photos, or anything private.")
            }
            .padding(MeritSpacing.large)
            .background(MeritColor.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

            MeritButton("Get Started", icon: "arrow.right", style: .primary) {
                step = .screenTimeAuth
            }
        }
    }

    // MARK: - Step: Screen Time Auth

    private var screenTimeAuthContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            iconBadge("hourglass.badge.shield.half.filled", color: MeritColor.accent)

            VStack(spacing: MeritSpacing.small) {
                Text("Screen Time Permission")
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text("Watching needs Apple's Screen Time permission to pause and shield apps automatically at the OS level.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: MeritSpacing.small) {
                Text("⚠️ A system dialog will appear.")
                    .font(MeritTypography.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(MeritColor.warning)
                Text("Tap **Allow** to enable Screen Time supervision.")
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
            .padding(MeritSpacing.medium)
            .background(MeritColor.warning.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

            MeritButton("Authorize Screen Time", icon: "lock.shield.fill", style: .primary) {
                Task { await requestAuthorization() }
            }

            MeritButton("Already Authorized → Continue", style: .secondary) {
                activationCoordinator.refreshState()
                if activationCoordinator.authorizationStatus == .approved {
                    step = .appSelection
                } else {
                    errorMessage = "Please tap 'Authorize Screen Time' and allow the permission."
                }
            }
        }
    }

    // MARK: - Step: App Selection

    private var appSelectionContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            iconBadge("apps.iphone", color: .purple)

            VStack(spacing: MeritSpacing.small) {
                Text("Select Apps to Monitor")
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text("Choose which apps Watching will manage with timed blocks and quizzes. You can change this later from Parent Controls.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            // Show pre-selected apps from parent's allowlist if any
            if !appRules.filter({ $0.allowed && !$0.isEmergency }).isEmpty {
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Apps from Parent's Allowlist")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.horizontal, 4)

                    ForEach(appRules.filter { $0.allowed && !$0.isEmergency }.prefix(4)) { rule in
                        HStack(spacing: MeritSpacing.small) {
                            Image(systemName: "app.fill")
                                .foregroundColor(MeritColor.accent)
                                .frame(width: 28)
                            Text(rule.displayName)
                                .font(MeritTypography.body)
                                .foregroundColor(MeritColor.label)
                            Spacer()
                            Text("\(rule.blockMinutes) min")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                        .padding(.horizontal, MeritSpacing.medium)
                        .padding(.vertical, MeritSpacing.small)
                        .background(MeritColor.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous))
                    }
                }
            }

            // Selection status pill
            if !activitySelection.applicationTokens.isEmpty || !activitySelection.categoryTokens.isEmpty {
                HStack(spacing: MeritSpacing.xSmall) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("\(activitySelection.applicationTokens.count) app(s) selected")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.green)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.green.opacity(0.12))
                .clipShape(Capsule())
            }

            MeritButton(
                activitySelection.applicationTokens.isEmpty ? "Choose Apps to Monitor" : "Change Selection",
                icon: "checklist",
                style: .primary
            ) {
                showingActivityPicker = true
            }

            if !activitySelection.applicationTokens.isEmpty {
                MeritButton("Continue with Selection", icon: "arrow.right", style: .secondary) {
                    step = .notifications
                }
            }
        }
    }

    // MARK: - Step: Notifications

    private var notificationsContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            iconBadge("bell.badge.fill", color: .orange)

            VStack(spacing: MeritSpacing.small) {
                Text("Enable Notifications")
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text("Watching uses notifications to alert you when a quiz is ready and when your fail-lock cooldown ends.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            MeritButton("Allow Notifications", icon: "bell.fill", style: .primary) {
                Task { await requestNotifications() }
            }

            MeritButton("Skip for Now", style: .secondary) {
                step = .activating
                Task { await activateScreenTime() }
            }
        }
    }

    // MARK: - Step: Activating

    private var activatingContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(MeritColor.accent)

            VStack(spacing: MeritSpacing.small) {
                Text("Activating Screen Time...")
                    .font(MeritTypography.title2)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)

                Text("Setting up your daily app limits and learning blocks.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.top, MeritSpacing.xxLarge)
    }

    // MARK: - Step: Done

    private var doneContent: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            iconBadge("checkmark.seal.fill", color: MeritColor.pass)

            VStack(spacing: MeritSpacing.small) {
                Text("You're All Set! 🚀")
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text("Watching is now protecting your screen time. Open your apps from the home screen — your timer starts when you do.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            MeritButton("Enter Watching Hub", icon: "house.fill", style: .primary) {
                onComplete()
            }
        }
    }

    // MARK: - Actions

    private func requestAuthorization() async {
        errorMessage = nil
        let granted = await ScreenTimeAuthorizationManager.shared.requestAuthorization(for: .individual)
        activationCoordinator.refreshState()
        if granted {
            step = .appSelection
        } else {
            errorMessage = "Screen Time authorization was denied. Please go to iOS Settings > Screen Time > Allow Watching."
        }
    }

    private func requestNotifications() async {
        let center = UNUserNotificationCenter.current()
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            notificationGranted = granted
        } catch {
            print("[PermissionsOnboarding] Notification request error: \(error)")
        }
        step = .activating
        await activateScreenTime()
    }

    private func activateScreenTime() async {
        isActivating = true
        defer { isActivating = false }
        errorMessage = nil

        // Build BedtimeWindow from policy if available
        let bedtime: BedtimeWindow? = nil // Will be populated from parent policy in future

        let blockMin = policy.defaultBlockMinutes > 0 ? policy.defaultBlockMinutes : 30
        let cooldownMin = policy.defaultCooldownMinutes > 0 ? policy.defaultCooldownMinutes : 15

        let success = await activationCoordinator.activate(
            selection: activitySelection,
            blockMinutes: blockMin,
            cooldownMinutes: cooldownMin,
            childName: credential.displayName,
            bedtime: bedtime
        )

        if success || activitySelection.applicationTokens.isEmpty {
            step = .done
        } else {
            errorMessage = activationCoordinator.errorMessage ?? "Activation failed. You can complete setup from the hub."
            // Still let them through after delay
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            step = .done
        }
    }

    // MARK: - Helpers

    private func iconBadge(_ name: String, color: Color) -> some View {
        ZStack {
            Circle()
                .fill(color.opacity(0.15))
                .frame(width: 96, height: 96)
            Image(systemName: name)
                .font(.system(size: 48))
                .foregroundColor(color)
        }
        .padding(.top, MeritSpacing.medium)
    }

    private func featureRow(icon: String, title: String, desc: String) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(MeritColor.accent)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Text(desc)
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .lineSpacing(2)
            }
        }
    }
}

// Safe import for UserNotifications
import UserNotifications
