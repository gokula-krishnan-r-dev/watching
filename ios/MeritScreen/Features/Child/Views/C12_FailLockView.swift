import SwiftUI

/// Screen C12: Fail Lock Calm Cooldown (Resting Horizon screen).
///
/// Invariants (Product locked - docs 07 & 13):
/// 1. Cooldown countdown finishes -> unlocks all approved apps.
/// 2. Retry Quiz during cooldown is supported.
///    - Pass -> unlocks immediately!
///    - Fail -> keeps shield and RESTARTS cooldown timer from zero.
/// 3. Emergency contacts (Phone, Call Mom, Call Dad) are always accessible.
/// 4. Parent PIN override unlocks device immediately.
public struct C12_FailLockView: View {
    @Bindable public var viewModel: FailLockViewModel
    public let onRetryQuiz: () -> Void
    public let onOpenParentPin: () -> Void
    public let onCooldownFinished: () -> Void

    public init(
        viewModel: FailLockViewModel,
        onRetryQuiz: @escaping () -> Void,
        onOpenParentPin: @escaping () -> Void,
        onCooldownFinished: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onRetryQuiz = onRetryQuiz
        self.onOpenParentPin = onOpenParentPin
        self.onCooldownFinished = onCooldownFinished
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Calm Badge
                HStack(spacing: MeritSpacing.xSmall) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 14))
                        .foregroundColor(MeritColor.accent)
                    Text("Fail Lock Shield")
                        .font(MeritTypography.caption)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())
                .padding(.top, MeritSpacing.large)

                // Moon Icon
                ZStack {
                    Circle()
                        .fill(MeritColor.accent.opacity(0.15))
                        .frame(width: 104, height: 104)

                    Circle()
                        .fill(MeritColor.cardBackground)
                        .frame(width: 80, height: 80)

                    Text("🌙")
                        .font(.system(size: 40))
                }

                // Calm Header Copy
                VStack(spacing: MeritSpacing.xSmall) {
                    Text("Let's rest our eyes and brain")
                        .font(MeritTypography.title2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("Great try, \(viewModel.childName)! Non-emergency apps are resting until this timer finishes. Stretch, drink water, or look out the window.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Resting Window & Countdown Card
                VStack(spacing: MeritSpacing.medium) {
                    HStack(spacing: MeritSpacing.xSmall) {
                        Image(systemName: "timer")
                            .foregroundColor(MeritColor.accent)
                        Text("Resting Window")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    Text(viewModel.formattedRemainingTime)
                        .font(.system(size: 48, weight: .bold, design: .rounded))
                        .foregroundColor(MeritColor.label)

                    Text(viewModel.secondsRemaining > 0 ? "Cooldown finishes at \(viewModel.finishesAtLabel)" : "Cooldown finished")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(MeritColor.secondaryFill)
                        .clipShape(Capsule())

                    // Tip Row
                    HStack {
                        HStack(spacing: 4) {
                            Image(systemName: "drop.fill")
                                .font(.system(size: 12))
                                .foregroundColor(MeritColor.accent)
                            Text(viewModel.currentTip)
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                        Spacer()
                        Text("Pause & breathe")
                            .font(MeritTypography.caption)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.accent)
                    }

                    ProgressView(value: viewModel.progress, total: 1.0)
                        .tint(MeritColor.accent)
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Retry Quiz Button (Doc 07 / 13 Requirement)
                VStack(spacing: MeritSpacing.xSmall) {
                    MeritButton(
                        "Retry Quiz Now",
                        icon: "arrow.clockwise",
                        style: .primary
                    ) {
                        onRetryQuiz()
                    }

                    Text("Pass to unlock your app immediately. A failed attempt will reset the timer.")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                }

                // Emergency Contacts Card (Always Reachable)
                VStack(spacing: MeritSpacing.medium) {
                    HStack {
                        HStack(spacing: MeritSpacing.small) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(MeritColor.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Emergency Contacts")
                                    .font(MeritTypography.headline)
                                    .foregroundColor(MeritColor.label)
                                Text("Always Unlocked & Reachable")
                                    .font(MeritTypography.caption)
                                    .foregroundColor(MeritColor.accent)
                            }
                        }
                        Spacer()
                        Image(systemName: "phone.fill")
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    HStack(spacing: MeritSpacing.medium) {
                        MeritButton("Call Mom", icon: "phone.fill", style: .secondary) {
                            viewModel.openEmergencyDialer(type: .mom)
                        }

                        MeritButton("Call Dad", icon: "phone.fill", style: .secondary) {
                            viewModel.openEmergencyDialer(type: .dad)
                        }
                    }
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Parent PIN Override
                Button {
                    onOpenParentPin()
                } label: {
                    Text("Parent override? Enter PIN")
                        .font(MeritTypography.callout)
                        .fontWeight(.medium)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, MeritSpacing.small)
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
        .onAppear {
            viewModel.loadLockData()
            viewModel.startTimer {
                onCooldownFinished()
            }
        }
        .onDisappear {
            viewModel.stopTimer()
        }
    }
}
