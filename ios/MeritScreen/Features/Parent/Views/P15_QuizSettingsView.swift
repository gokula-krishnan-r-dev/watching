import SwiftUI

public struct P15_QuizSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: PolicyEditorViewModel

    public init(childId: String) {
        _viewModel = State(wrappedValue: PolicyEditorViewModel(childId: childId))
    }

    public var body: some View {
        Form {
            if let error = viewModel.error {
                Section {
                    StateErrorCard(message: error)
                }
            }

            Section {
                Picker("Quiz Trigger Mode", selection: Binding(
                    get: { viewModel.policy.quizMode },
                    set: { val in viewModel.update { $0.quizMode = val } }
                )) {
                    ForEach(QuizMode.allCases, id: \.self) { mode in
                        Text(mode.displayLabel).tag(mode)
                    }
                }

                Text(viewModel.policy.quizMode.description)
                    .font(MeritTypography.caption1)
                    .foregroundColor(MeritColor.secondaryLabel)
            } header: {
                Text("Interruption Trigger")
            }

            Section {
                Stepper(
                    "Questions per Quiz: \(viewModel.policy.questionsPerQuiz)",
                    value: Binding(
                        get: { viewModel.policy.questionsPerQuiz },
                        set: { val in viewModel.update { $0.questionsPerQuiz = val } }
                    ),
                    in: 1...5
                )

                Picker("Passing Score Required", selection: Binding(
                    get: { viewModel.policy.passScorePercent },
                    set: { val in viewModel.update { $0.passScorePercent = val } }
                )) {
                    Text("50% (1 of 2)").tag(50)
                    Text("60%").tag(60)
                    Text("70% (Standard)").tag(70)
                    Text("80%").tag(80)
                    Text("100% (Mastery)").tag(100)
                }

                Toggle("Adaptive Difficulty", isOn: Binding(
                    get: { viewModel.policy.adaptiveDifficultyEnabled },
                    set: { val in viewModel.update { $0.adaptiveDifficultyEnabled = val } }
                ))

                Toggle("Show Rich Explanations", isOn: Binding(
                    get: { viewModel.policy.showExplanations },
                    set: { val in viewModel.update { $0.showExplanations = val } }
                ))
            } header: {
                Text("Quiz Rules & Mastery")
            } footer: {
                Text("Adaptive difficulty dynamically scales questions based on real-time mastery and weak concepts.")
                    .font(MeritTypography.caption1)
            }

            Section {
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text("Custom Learning Focus")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.label)

                    TextEditor(text: Binding(
                        get: { viewModel.policy.customPromptGuidelines },
                        set: { val in viewModel.update { $0.customPromptGuidelines = val } }
                    ))
                    .frame(minHeight: 90)
                    .padding(4)
                    .background(MeritColor.secondaryFill)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))

                    HStack {
                        Text("e.g. Focus on times tables, phonics, and solar system facts.")
                            .font(MeritTypography.caption2)
                            .foregroundColor(MeritColor.secondaryLabel)
                        Spacer()
                        Text("\(viewModel.policy.customPromptGuidelines.count)/\(AppConfig.customPromptGuidelinesMaxChars)")
                            .font(MeritTypography.caption2)
                            .foregroundColor(viewModel.policy.customPromptGuidelines.count > AppConfig.customPromptGuidelinesMaxChars ? MeritColor.destructive : MeritColor.secondaryLabel)
                    }
                }
                .padding(.vertical, MeritSpacing.xxSmall)
            } header: {
                Text("Personalized AI Learning Context")
            } footer: {
                Text("Personalized instructions are injected into the local question generator to emphasize topics your child needs practice with.")
                    .font(MeritTypography.caption1)
            }

            Section {
                MeritButton(
                    "Save Quiz Settings",
                    icon: "checkmark",
                    style: .primary,
                    isLoading: viewModel.isSaving
                ) {
                    viewModel.save()
                }
            }
        }
        .navigationTitle("Quiz & AI Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: viewModel.isSaved) { _, saved in
            if saved {
                _ = viewModel.consumeSaved()
                dismiss()
            }
        }
    }
}
