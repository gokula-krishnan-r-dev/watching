import SwiftUI

/// Screen C09: Quiz Question View with adaptive layouts for age bands.
/// Age 3–6: Big tactile touch targets, picture/emoji taxonomy support.
/// Age 7–12: Clean multiple-choice cards with option badges.
public struct C09_QuizQuestionView: View {
    public let question: QuizQuestion
    public let questionIndex: Int
    public let totalQuestions: Int
    public let selectedChoiceId: String?
    public let ageBand: AgeBand
    public let onSelectChoice: (String) -> Void
    public let onSubmit: () -> Void

    public init(
        question: QuizQuestion,
        questionIndex: Int,
        totalQuestions: Int,
        selectedChoiceId: String?,
        ageBand: AgeBand,
        onSelectChoice: @escaping (String) -> Void,
        onSubmit: @escaping () -> Void
    ) {
        self.question = question
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.selectedChoiceId = selectedChoiceId
        self.ageBand = ageBand
        self.onSelectChoice = onSelectChoice
        self.onSubmit = onSubmit
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Header: Step and Topic
                HStack {
                    Text("Question \(questionIndex + 1) of \(totalQuestions)")
                        .font(MeritTypography.caption)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.secondaryLabel)

                    Spacer()

                    Text(question.topic.capitalized)
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(MeritColor.accent.opacity(0.12))
                        .clipShape(Capsule())
                }
                .padding(.top, MeritSpacing.small)

                // Progress Bar
                ProgressView(value: Double(questionIndex + 1), total: Double(totalQuestions))
                    .tint(MeritColor.accent)

                // Question Prompt
                VStack(spacing: MeritSpacing.medium) {
                    Text(question.prompt)
                        .font(ageBand == .band_3_6 ? MeritTypography.title2 : MeritTypography.title3)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, MeritSpacing.medium)
                }
                .frame(maxWidth: .infinity)
                .padding(MeritSpacing.large)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Choices
                VStack(spacing: MeritSpacing.medium) {
                    ForEach(Array(question.choices.enumerated()), id: \.element.id) { index, choice in
                        let isSelected = (selectedChoiceId == choice.id)
                        let letter = choiceLetter(for: index)

                        Button {
                            onSelectChoice(choice.id)
                        } label: {
                            HStack(spacing: MeritSpacing.medium) {
                                // Option letter pill
                                ZStack {
                                    Circle()
                                        .fill(isSelected ? MeritColor.accent : MeritColor.secondaryFill)
                                        .frame(width: 38, height: 38)

                                    Text(letter)
                                        .font(MeritTypography.headline)
                                        .foregroundColor(isSelected ? .white : MeritColor.label)
                                }

                                Text(choice.text)
                                    .font(ageBand == .band_3_6 ? MeritTypography.headline : MeritTypography.body)
                                    .fontWeight(isSelected ? .bold : .regular)
                                    .foregroundColor(MeritColor.label)
                                    .multilineTextAlignment(.leading)

                                Spacer()

                                if isSelected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 22))
                                        .foregroundColor(MeritColor.accent)
                                }
                            }
                            .padding(ageBand == .band_3_6 ? MeritSpacing.large : MeritSpacing.medium)
                            .frame(maxWidth: .infinity, minHeight: ageBand == .band_3_6 ? 72 : 56)
                            .background(
                                RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                                    .fill(isSelected ? MeritColor.accent.opacity(0.12) : MeritColor.cardBackground)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                                    .stroke(isSelected ? MeritColor.accent : Color.clear, lineWidth: 2)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                // Submit Button
                MeritButton("Check Answer", style: .primary) {
                    onSubmit()
                }
                .disabled(selectedChoiceId == nil)
                .padding(.top, MeritSpacing.medium)
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }

    private func choiceLetter(for index: Int) -> String {
        let letters = ["A", "B", "C", "D", "E"]
        return index < letters.count ? letters[index] : "\(index + 1)"
    }
}
