import SwiftUI

/// Screen C10: Quiz Explanation & Mini-Lesson.
/// Encouraging, gentle step-by-step feedback. Zero shame on mistakes.
public struct C10_QuizExplanationView: View {
    public let feedback: QuizAnswerFeedback
    public let question: QuizQuestion
    public let isLastQuestion: Bool
    public let onContinue: () -> Void

    public init(
        feedback: QuizAnswerFeedback,
        question: QuizQuestion,
        isLastQuestion: Bool,
        onContinue: @escaping () -> Void
    ) {
        self.feedback = feedback
        self.question = question
        self.isLastQuestion = isLastQuestion
        self.onContinue = onContinue
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Status Header Badge
                ZStack {
                    Circle()
                        .fill(feedback.correct ? Color.green.opacity(0.15) : Color.orange.opacity(0.15))
                        .frame(width: 88, height: 88)

                    Image(systemName: feedback.correct ? "checkmark.circle.fill" : "lightbulb.circle.fill")
                        .font(.system(size: 48))
                        .foregroundColor(feedback.correct ? .green : .orange)
                }
                .padding(.top, MeritSpacing.large)

                VStack(spacing: MeritSpacing.xSmall) {
                    Text(feedback.resultLine)
                        .font(MeritTypography.title2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text(feedback.whyLine)
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Concept Explainer Card
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    HStack(spacing: MeritSpacing.xSmall) {
                        Image(systemName: "sparkles")
                            .foregroundColor(MeritColor.accent)
                        Text(question.conceptTitle)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                    }

                    Text(feedback.conceptLine)
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .lineSpacing(3)
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Mini-Lesson (if available)
                if let lesson = question.miniLesson {
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        Text(lesson.title)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        ForEach(lesson.bodyLines, id: \.self) { line in
                            HStack(alignment: .top, spacing: MeritSpacing.small) {
                                Text("•")
                                    .fontWeight(.bold)
                                    .foregroundColor(MeritColor.accent)
                                Text(line)
                                    .font(MeritTypography.callout)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                    }
                    .padding(MeritSpacing.large)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
                }

                // Continue Button
                MeritButton(isLastQuestion ? "See Results" : "Next Question", icon: "arrow.right", style: .primary) {
                    onContinue()
                }
                .padding(.top, MeritSpacing.medium)
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }
}
