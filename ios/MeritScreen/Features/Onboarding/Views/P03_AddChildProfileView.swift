import SwiftUI

/// P03: Add First Child Profile Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.AddChildScreen` in Android.
public struct P03_AddChildProfileView: View {
    @Binding public var children: [ChildDraft]
    public let onContinue: (ChildDraft) -> Void
    public let onBack: () -> Void

    @State private var childName: String = ""
    @State private var selectedAgeBand: AgeBand = .band_7_9
    @State private var selectedAvatar: AvatarPreset = .rabbit
    @State private var selectedTopics: Set<String> = ["math_foundations", "reading_fluency", "stem_logic"]
    @State private var validationError: String?

    public init(
        children: Binding<[ChildDraft]>,
        onContinue: @escaping (ChildDraft) -> Void,
        onBack: @escaping () -> Void
    ) {
        self._children = children
        self.onContinue = onContinue
        self.onBack = onBack

        // Prepopulate with existing child if present in draft
        if let first = children.wrappedValue.first {
            _childName = State(initialValue: first.name)
            _selectedAgeBand = State(initialValue: first.ageBand)
            _selectedAvatar = State(initialValue: first.avatar)
        }
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Top Progress Pill & Back Button
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
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(MeritColor.accent)
                        Text("Step 1 of 5 • Child Profile")
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

                // Header
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text("Who is this device for?")
                        .font(MeritTypography.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)

                    Text("We calibrate adaptive learning questions and healthy guardrails to your child's age band.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Name Input Card
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Child's First Name")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundColor(MeritColor.secondaryLabel)

                        TextField("e.g. Leo, Maya, Alex", text: $childName)
                            .font(MeritTypography.body)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.words)

                        if !childName.isEmpty {
                            Button(action: { childName = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.secondaryBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                            .stroke(validationError != nil ? MeritColor.destructive : MeritColor.separator.opacity(0.4), lineWidth: 1)
                    )

                    if let error = validationError {
                        Text(error)
                            .font(MeritTypography.footnote)
                            .foregroundColor(MeritColor.destructive)
                    }
                }

                // Avatar Preset Picker
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Choose Avatar Preset")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: MeritSpacing.medium) {
                            ForEach(AvatarPreset.allCases) { preset in
                                avatarButton(preset)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                // Age Band Picker
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Age Band")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    VStack(spacing: 8) {
                        ForEach(AgeBand.allCases, id: \.self) { band in
                            ageBandRow(band)
                        }
                    }
                }

                // Curriculum Focus Tags
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Initial Curiosity Focus")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text("Select subjects your child will encounter during quiz checkpoints:")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)

                    FlowLayout(spacing: 8) {
                        topicChip(id: "math_foundations", label: "Math Foundations", icon: "plus.forwardslash.minus")
                        topicChip(id: "reading_fluency", label: "Reading & Words", icon: "book.fill")
                        topicChip(id: "stem_logic", label: "Logic & STEM", icon: "puzzlepiece.fill")
                        topicChip(id: "science_exploration", label: "Science & Nature", icon: "leaf.fill")
                        topicChip(id: "creative_arts", label: "Art & Creativity", icon: "paintbrush.fill")
                    }
                }

                Spacer(minLength: MeritSpacing.medium)

                // Continue CTA
                MeritButton(
                    "Continue to Schedule",
                    icon: "arrow.right",
                    style: .primary
                ) {
                    handleProceed()
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }

    private func avatarButton(_ preset: AvatarPreset) -> some View {
        Button(action: {
            selectedAvatar = preset
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }) {
            VStack(spacing: 6) {
                Text(preset.emoji)
                    .font(.system(size: 36))
                    .frame(width: 58, height: 58)
                    .background(selectedAvatar == preset ? MeritColor.accent.opacity(0.18) : MeritColor.secondaryBackground)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(selectedAvatar == preset ? MeritColor.accent : Color.clear, lineWidth: 2.5)
                    )

                Text(preset.displayName)
                    .font(MeritTypography.caption)
                    .fontWeight(selectedAvatar == preset ? .bold : .regular)
                    .foregroundColor(selectedAvatar == preset ? MeritColor.accent : MeritColor.secondaryLabel)
            }
        }
        .buttonStyle(.plain)
    }

    private func ageBandRow(_ band: AgeBand) -> some View {
        Button(action: {
            selectedAgeBand = band
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(band.displayLabel)
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text(ageBandSubtitle(band))
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                }

                Spacer()

                if selectedAgeBand == band {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(MeritColor.accent)
                } else {
                    Circle()
                        .stroke(MeritColor.secondaryLabel.opacity(0.4), lineWidth: 1.5)
                        .frame(width: 20, height: 20)
                }
            }
            .padding(MeritSpacing.medium)
            .background(selectedAgeBand == band ? MeritColor.accent.opacity(0.08) : MeritColor.secondaryBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                    .stroke(selectedAgeBand == band ? MeritColor.accent : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }

    private func topicChip(id: String, label: String, icon: String) -> some View {
        let isSelected = selectedTopics.contains(id)
        return Button(action: {
            if isSelected {
                selectedTopics.remove(id)
            } else {
                selectedTopics.insert(id)
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12))
                    .foregroundColor(isSelected ? .white : MeritColor.accent)
                Text(label)
                    .font(MeritTypography.caption)
                    .fontWeight(.medium)
                    .foregroundColor(isSelected ? .white : MeritColor.label)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? MeritColor.accent : MeritColor.secondaryBackground)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(isSelected ? Color.clear : MeritColor.separator.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func ageBandSubtitle(_ band: AgeBand) -> String {
        band.subtitle
    }

    private func handleProceed() {
        let trimmed = childName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            validationError = "Please enter your child's first name."
            UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            return
        }

        validationError = nil
        let newChild = ChildDraft(
            id: children.first?.id ?? UUID().uuidString,
            name: trimmed,
            ageBand: selectedAgeBand,
            avatar: selectedAvatar
        )

        if children.isEmpty {
            children.append(newChild)
        } else {
            children[0] = newChild
        }

        onContinue(newChild)
    }
}

/// Dynamic wrap layout for topic pills.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var height: CGFloat = 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > width && x > 0 {
                x = 0
                y += maxHeight + spacing
                maxHeight = 0
            }
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
        }
        height = y + maxHeight
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var maxHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX {
                x = bounds.minX
                y += maxHeight + spacing
                maxHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
        }
    }
}
