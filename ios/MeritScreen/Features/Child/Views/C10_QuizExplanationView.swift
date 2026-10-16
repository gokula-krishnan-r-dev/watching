import SwiftUI

/// Screen C10: Quiz Explanation & Learn More.
/// Correct answers: short affirmation. Wrong answers: sticky Learn More resources
/// with an `AppConfig.quizLockoutSeconds` teaching pause before Continue.
public struct C10_QuizExplanationView: View {
    public let feedback: QuizAnswerFeedback
    public let question: QuizQuestion
    public let isLastQuestion: Bool
    public let learningResource: LearningResource?
    public let learnMoreRequired: Bool
    public let learnMoreLockSecondsRemaining: Int
    public let onContinue: () -> Void

    public init(
        feedback: QuizAnswerFeedback,
        question: QuizQuestion,
        isLastQuestion: Bool,
        learningResource: LearningResource? = nil,
        learnMoreRequired: Bool = false,
        learnMoreLockSecondsRemaining: Int = 0,
        onContinue: @escaping () -> Void
    ) {
        self.feedback = feedback
        self.question = question
        self.isLastQuestion = isLastQuestion
        self.learningResource = learningResource
        self.learnMoreRequired = learnMoreRequired
        self.learnMoreLockSecondsRemaining = learnMoreLockSecondsRemaining
        self.onContinue = onContinue
    }

    private var canContinue: Bool {
        if feedback.correct { return true }
        return learnMoreRequired && learnMoreLockSecondsRemaining <= 0
    }

    private var lockProgress: Double {
        let total = Double(max(AppConfig.quizLockoutSeconds, 1))
        let remaining = Double(max(learnMoreLockSecondsRemaining, 0))
        return ((total - remaining) / total).clamped(to: 0...1)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                statusHeader

                if feedback.correct {
                    correctContent
                } else {
                    wrongLearnMoreContent
                }

                continueButton
                    .padding(.top, MeritSpacing.medium)
                    .padding(.bottom, MeritSpacing.large)
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
        .interactiveDismissDisabled(learnMoreRequired && learnMoreLockSecondsRemaining > 0)
    }

    // MARK: - Shared header

    private var statusHeader: some View {
        VStack(spacing: MeritSpacing.xSmall) {
            ZStack {
                Circle()
                    .fill(feedback.correct ? Color.green.opacity(0.15) : Color.orange.opacity(0.15))
                    .frame(width: 88, height: 88)

                Image(systemName: feedback.correct ? "checkmark.circle.fill" : "lightbulb.circle.fill")
                    .font(.system(size: 48))
                    .foregroundColor(feedback.correct ? .green : .orange)
            }
            .padding(.top, MeritSpacing.large)

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
    }

    // MARK: - Correct path

    private var correctContent: some View {
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
    }

    // MARK: - Wrong path: Learn More resources + lock

    @ViewBuilder
    private var wrongLearnMoreContent: some View {
        lockBanner

        if let resource = learningResource {
            guideCard(resource)
            if !resource.videos.isEmpty {
                videosCard(resource.videos)
            }
        } else if let lesson = question.miniLesson {
            miniLessonCard(lesson)
        } else {
            conceptFallbackCard
        }
    }

    private var lockBanner: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            HStack {
                Label(
                    canContinue ? "Learning pause complete" : "Focused learning pause",
                    systemImage: "timer"
                )
                .font(MeritTypography.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(MeritColor.label)

                Spacer()

                Text(canContinue ? "Ready" : String(format: "00:%02d", learnMoreLockSecondsRemaining))
                    .font(MeritTypography.headline)
                    .fontWeight(.bold)
                    .monospacedDigit()
                    .foregroundColor(MeritColor.accent)
            }

            ProgressView(value: lockProgress)
                .tint(MeritColor.accent)

            Text(
                canContinue
                    ? "Nice focus! You can continue to the next question."
                    : "Take a moment to learn why — continue unlocks in \(learnMoreLockSecondsRemaining)s."
            )
            .font(MeritTypography.caption)
            .foregroundColor(MeritColor.secondaryLabel)
        }
        .padding(MeritSpacing.large)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MeritColor.accent.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private func guideCard(_ resource: LearningResource) -> some View {
        let blog = resource.summaryBlog
        return VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            HStack(spacing: MeritSpacing.xSmall) {
                Image(systemName: "book.fill")
                    .foregroundColor(MeritColor.accent)
                Text("Learn more")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Spacer()
                Text(resource.topic.uppercased())
                    .font(MeritTypography.caption)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.accent)
            }

            Text(blog.title)
                .font(MeritTypography.title3)
                .fontWeight(.bold)
                .foregroundColor(MeritColor.label)

            Text(blog.coreRule)
                .font(MeritTypography.subheadline)
                .foregroundColor(MeritColor.secondaryLabel)
                .lineSpacing(3)

            ForEach(Array(blog.sections.enumerated()), id: \.offset) { _, section in
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text(section.heading)
                        .font(MeritTypography.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.label)
                    Text(section.content)
                        .font(MeritTypography.callout)
                        .foregroundColor(MeritColor.secondaryLabel)
                    ForEach(section.bulletPoints, id: \.self) { bullet in
                        HStack(alignment: .top, spacing: MeritSpacing.small) {
                            Text("•")
                                .fontWeight(.bold)
                                .foregroundColor(MeritColor.accent)
                            Text(bullet)
                                .font(MeritTypography.callout)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                Text("Memory tip")
                    .font(MeritTypography.caption)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.accent)
                Text(blog.quickMemoryTip)
                    .font(MeritTypography.callout)
                    .foregroundColor(MeritColor.label)
            }
            .padding(MeritSpacing.medium)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(MeritColor.accent.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

            if let funFact = blog.funFact {
                Text("Fun fact: \(funFact)")
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
            }
        }
        .padding(MeritSpacing.large)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private func videosCard(_ videos: [LearningResourceVideo]) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            HStack(spacing: MeritSpacing.xSmall) {
                Image(systemName: "play.rectangle.fill")
                    .foregroundColor(MeritColor.accent)
                Text("Helpful videos")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
            }

            ForEach(videos) { video in
                if let url = video.youtubeURL {
                    Link(destination: url) {
                        videoRow(video)
                    }
                    .buttonStyle(.plain)
                } else {
                    videoRow(video)
                }
            }
        }
        .padding(MeritSpacing.large)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private func videoRow(_ video: LearningResourceVideo) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.medium) {
            Image(systemName: "play.circle.fill")
                .font(.system(size: 28))
                .foregroundColor(MeritColor.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(video.title)
                    .font(MeritTypography.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(MeritColor.label)
                    .multilineTextAlignment(.leading)
                Text("\(video.channelName) · \(video.durationLabel)")
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                Text(video.description)
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.groupedBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
    }

    private func miniLessonCard(_ lesson: MiniLesson) -> some View {
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

    private var conceptFallbackCard: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            Text(question.conceptTitle)
                .font(MeritTypography.headline)
                .foregroundColor(MeritColor.label)
            Text(feedback.conceptLine)
                .font(MeritTypography.subheadline)
                .foregroundColor(MeritColor.secondaryLabel)
                .lineSpacing(3)
        }
        .padding(MeritSpacing.large)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    // MARK: - Continue

    private var continueButton: some View {
        MeritButton(
            continueTitle,
            icon: canContinue ? "arrow.right" : "lock.fill",
            style: .primary,
            isEnabled: canContinue
        ) {
            onContinue()
        }
    }

    private var continueTitle: String {
        if feedback.correct {
            return isLastQuestion ? "See Results" : "Next Question"
        }
        if canContinue {
            return isLastQuestion ? "See Results" : "Continue to next question"
        }
        return "Keep learning (\(learnMoreLockSecondsRemaining)s)"
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
