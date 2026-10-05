import SwiftUI

/// P03d: AI Learning Profile & Context Customizer.
/// Mirrors `com.meritscreen.feature.onboarding.ui.AiLearningContextScreen` in Android.
public struct P03d_AiLearningContextView: View {
    public let childName: String
    public let grade: String
    public let avatar: AvatarPreset
    public let initialPrompt: String
    public let onContinue: (String) -> Void
    public let onBack: () -> Void

    @State private var promptText: String
    @State private var isListening: Bool = false

    public let stepPillLabel: String

    private let suggestions = [
        "Focus on 3rd grade fractions and word problems",
        "Enjoys space, dinosaurs, and animal trivia",
        "Needs gentle encouragement with reading vocabulary",
        "Encourage logical reasoning and puzzle quests",
        "Keep questions concise and upbeat"
    ]

    private var wordCount: Int {
        let trimmed = promptText.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? 0 : trimmed.split(separator: " ").count
    }

    public init(
        childName: String = "your child",
        grade: String = "Elementary",
        avatar: AvatarPreset = .rabbit,
        initialPrompt: String = "",
        stepPillLabel: String = "Step 5 of 5 • AI Learning",
        onContinue: @escaping (String) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.childName = childName
        self.grade = grade
        self.avatar = avatar
        self.initialPrompt = initialPrompt
        self.stepPillLabel = stepPillLabel
        self.onContinue = onContinue
        self.onBack = onBack
        self._promptText = State(initialValue: initialPrompt)
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: MeritSpacing.large) {
                    // Top Bar
                    HStack {
                        Button(action: onBack) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                Text("Back")
                            }
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.accent)
                        }

                        Spacer()

                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(MeritColor.accent)
                            Text(stepPillLabel)
                                .font(MeritTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.accent)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(MeritColor.accent.opacity(0.12))
                        .clipShape(Capsule())
                    }
                    .padding(.top, MeritSpacing.small)

                    // Active Child Context Badge
                    HStack(spacing: 12) {
                        Text(avatar.emoji)
                            .font(.system(size: 32))
                            .frame(width: 48, height: 48)
                            .background(MeritColor.secondaryFill)
                            .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(childName) (\(grade))")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            Text("• Adaptive Profile Ready")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(MeritColor.accent)
                        }

                        Spacer()

                        Text("AI Tailored")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(MeritColor.accent)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(MeritColor.accent.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.secondaryBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    // Title & Description
                    VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                        Text("AI Learning Context")
                            .font(MeritTypography.largeTitle)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)

                        Text("Describe your child's learning preferences, topics they love, or areas needing reinforcement to shape their daily micro-quizzes.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    // Voice Input Banner (when active)
                    if isListening {
                        HStack(spacing: 10) {
                            Circle()
                                .fill(MeritColor.destructive)
                                .frame(width: 10, height: 10)

                            Text("Listening... Speak clearly about \(childName)")
                                .font(MeritTypography.caption)
                                .fontWeight(.bold)
                                .foregroundColor(MeritColor.destructive)

                            Spacer()

                            Button("Done") {
                                isListening = false
                            }
                            .font(MeritTypography.caption)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.destructive)
                        }
                        .padding(10)
                        .background(MeritColor.destructive.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    // Text Input Area Card
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        HStack {
                            Text("Learning Prompt")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            Spacer()

                            Button(action: {
                                isListening.toggle()
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                if isListening {
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                        if isListening {
                                            promptText += (promptText.isEmpty ? "" : " ") + "Enjoys interactive science questions with visual examples."
                                            isListening = false
                                        }
                                    }
                                }
                            }) {
                                HStack(spacing: 4) {
                                    Image(systemName: isListening ? "mic.fill" : "mic")
                                    Text(isListening ? "Listening..." : "Dictate")
                                }
                                .font(MeritTypography.caption)
                                .foregroundColor(isListening ? MeritColor.destructive : MeritColor.accent)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(MeritColor.accent.opacity(0.1))
                                .clipShape(Capsule())
                            }
                        }

                        TextEditor(text: $promptText)
                            .frame(minHeight: 120)
                            .padding(8)
                            .background(MeritColor.background)
                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous)
                                    .stroke(MeritColor.separator.opacity(0.35), lineWidth: 1)
                            )

                        HStack {
                            Text("\(wordCount) words")
                                .font(.system(size: 11))
                                .foregroundColor(MeritColor.secondaryLabel)
                            Spacer()
                            Text("Max 250 words")
                                .font(.system(size: 11))
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.secondaryBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                    // Suggestion Pills
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        Text("Quick Inspirations")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(suggestions, id: \.self) { item in
                                Button(action: {
                                    if promptText.isEmpty {
                                        promptText = item
                                    } else {
                                        promptText += ". " + item
                                    }
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "plus.circle.fill")
                                            .font(.system(size: 13))
                                            .foregroundColor(MeritColor.accent)

                                        Text(item)
                                            .font(MeritTypography.footnote)
                                            .foregroundColor(MeritColor.label)
                                            .multilineTextAlignment(.leading)

                                        Spacer()
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(MeritColor.secondaryBackground)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    Spacer(minLength: MeritSpacing.medium)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 540)
            }

            // Bottom Sticky Action Button
            VStack(spacing: 0) {
                Divider()
                MeritButton(
                    "Save & Finish Setup",
                    icon: "checkmark.shield.fill",
                    style: .primary
                ) {
                    onContinue(promptText)
                }
                .padding(.horizontal, MeritSpacing.large)
                .padding(.vertical, MeritSpacing.medium)
                .responsiveContainer(maxWidth: 540)
            }
            .background(MeritColor.background)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }
}
