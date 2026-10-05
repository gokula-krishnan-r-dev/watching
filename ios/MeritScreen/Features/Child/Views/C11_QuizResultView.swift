import SwiftUI

/// Screen C11: Quiz Result View.
/// Celebratory pass screen with XP, unlocked time, and sticker badge.
/// Calm, encouraging fail screen leading into the resting cooldown shield.
public struct C11_QuizResultView: View {
    public let childName: String
    public let result: QuizSessionResult
    public let appLabel: String
    public let unlockedSticker: ChildSticker?
    public let onFinish: () -> Void

    public init(
        childName: String,
        result: QuizSessionResult,
        appLabel: String,
        unlockedSticker: ChildSticker? = nil,
        onFinish: @escaping () -> Void
    ) {
        self.childName = childName
        self.result = result
        self.appLabel = appLabel
        self.unlockedSticker = unlockedSticker
        self.onFinish = onFinish
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.xLarge) {
                Spacer().frame(height: MeritSpacing.large)

                // Status Icon
                ZStack {
                    Circle()
                        .fill(result.passed ? Color.green.opacity(0.15) : MeritColor.accent.opacity(0.15))
                        .frame(width: 104, height: 104)

                    Text(result.passed ? "🏆" : "🌱")
                        .font(.system(size: 52))
                }

                // Headline & Subtitle
                VStack(spacing: MeritSpacing.xSmall) {
                    Text(result.passed ? "Awesome Job, \(childName)!" : "Good Effort, \(childName)!")
                        .font(MeritTypography.title)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("You answered \(result.correctCount) of \(result.total) questions correctly (\(result.percent)%).")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                }

                if result.passed {
                    // Passed Reward Summary
                    VStack(spacing: MeritSpacing.medium) {
                        HStack(spacing: MeritSpacing.medium) {
                            VStack(spacing: 4) {
                                Text("+\(result.unlockedMinutes)m")
                                    .font(MeritTypography.title2)
                                    .fontWeight(.bold)
                                    .foregroundColor(MeritColor.accent)
                                Text("Time for \(appLabel)")
                                    .font(MeritTypography.caption)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                            .frame(maxWidth: .infinity)

                            Divider().frame(height: 40)

                            VStack(spacing: 4) {
                                Text("+\(result.xpGained) XP")
                                    .font(MeritTypography.title2)
                                    .fontWeight(.bold)
                                    .foregroundColor(Color.green)
                                Text("Explorer Score")
                                    .font(MeritTypography.caption)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                            .frame(maxWidth: .infinity)
                        }

                        // Sticker Badge (if newly earned)
                        if let sticker = unlockedSticker {
                            HStack(spacing: MeritSpacing.medium) {
                                Text(sticker.emoji)
                                    .font(.system(size: 36))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("New Sticker Unlocked!")
                                        .font(MeritTypography.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(MeritColor.accent)
                                    Text(sticker.title)
                                        .font(MeritTypography.headline)
                                        .foregroundColor(MeritColor.label)
                                }
                                Spacer()
                            }
                            .padding(MeritSpacing.medium)
                            .background(MeritColor.secondaryFill)
                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                        }
                    }
                    .padding(MeritSpacing.large)
                    .frame(maxWidth: .infinity)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
                } else {
                    // Encouraging Calm resting prompt
                    VStack(spacing: MeritSpacing.small) {
                        Text("Let's take a calm rest for a few minutes.")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        Text("Resting gives your brain and eyes time to recharge. You can try the quiz again during cooldown, or wait for the timer to finish.")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .multilineTextAlignment(.center)
                    }
                    .padding(MeritSpacing.large)
                    .frame(maxWidth: .infinity)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
                }

                Spacer()

                // Bottom Action
                MeritButton(result.passed ? "Continue Using App" : "OK, Let's Rest", style: .primary) {
                    onFinish()
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }
}
