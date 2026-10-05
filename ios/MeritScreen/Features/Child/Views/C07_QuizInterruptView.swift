import SwiftUI

/// Screen C07b: Quiz Interruption Intro.
/// Explains that answering a few quick questions will unlock another block.
public struct C07_QuizInterruptView: View {
    public let childName: String
    public let appRule: AppRule
    public let totalQuestions: Int
    public let minutesToEarn: Int
    public let isRetryMode: Bool
    public let onStartQuiz: () -> Void
    public let onDismiss: () -> Void

    public init(
        childName: String,
        appRule: AppRule,
        totalQuestions: Int,
        minutesToEarn: Int,
        isRetryMode: Bool = false,
        onStartQuiz: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.childName = childName
        self.appRule = appRule
        self.totalQuestions = totalQuestions
        self.minutesToEarn = minutesToEarn
        self.isRetryMode = isRetryMode
        self.onStartQuiz = onStartQuiz
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            Spacer()

            // Visual Icon
            ZStack {
                Circle()
                    .fill(MeritColor.accent.opacity(0.15))
                    .frame(width: 110, height: 110)

                Image(systemName: isRetryMode ? "arrow.clockwise.circle.fill" : "lightbulb.fill")
                    .font(.system(size: 54))
                    .foregroundColor(MeritColor.accent)
            }

            // Headline & Description
            VStack(spacing: MeritSpacing.small) {
                Text(isRetryMode ? "Retry & Unlock Now" : "Time for a Quick Quiz!")
                    .font(MeritTypography.title)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.center)

                Text(isRetryMode ?
                     "Hi \(childName)! Pass \(totalQuestions) questions to unlock \(appRule.displayName) immediately." :
                     "Nice progress on \(appRule.displayName)! Answer \(totalQuestions) quick questions to earn \(minutesToEarn) more minutes.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, MeritSpacing.medium)
            }

            // Reward Preview Pill
            HStack(spacing: MeritSpacing.medium) {
                Label("\(minutesToEarn) min unlock", systemImage: "clock.fill")
                    .font(MeritTypography.callout)
                    .foregroundColor(MeritColor.label)

                Text("•")
                    .foregroundColor(MeritColor.secondaryLabel)

                Label("+10 XP", systemImage: "sparkles")
                    .font(MeritTypography.callout)
                    .foregroundColor(MeritColor.accent)
            }
            .padding(.horizontal, MeritSpacing.large)
            .padding(.vertical, MeritSpacing.small)
            .background(MeritColor.cardBackground)
            .clipShape(Capsule())

            Spacer()

            // Actions
            VStack(spacing: MeritSpacing.medium) {
                MeritButton("Let's Go!", icon: "play.fill", style: .primary) {
                    onStartQuiz()
                }

                Button("Not Now (Return Home)") {
                    onDismiss()
                }
                .font(MeritTypography.callout)
                .foregroundColor(MeritColor.secondaryLabel)
            }
            .padding(.bottom, MeritSpacing.large)
        }
        .padding(MeritSpacing.large)
        .responsiveContainer(maxWidth: 480)
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }
}
