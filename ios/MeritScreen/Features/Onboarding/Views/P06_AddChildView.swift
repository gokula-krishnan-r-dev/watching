import SwiftUI

/// P06: Add Child Profile screen.
/// Collects child name, age band, and preset avatar without requiring a camera or child email.
/// Mirrors `com.meritscreen.feature.onboarding.ui.AddChildScreen`.
public struct P06_AddChildView: View {
    @Binding public var children: [ChildDraft]
    public let onContinue: () -> Void
    public let onBack: () -> Void

    @State private var currentName: String = ""
    @State private var currentAgeBand: AgeBand = .band_7_9
    @State private var currentAvatar: AvatarPreset = .rabbit
    @State private var validationError: String?

    public init(
        children: Binding<[ChildDraft]>,
        onContinue: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self._children = children
        self.onContinue = onContinue
        self.onBack = onBack
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Header with Step Indicator
                stepHeader(step: "3 of 4", title: "Child Profile")

                // Headline & Subtitle
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text("Who is this device for?")
                        .font(MeritTypography.largeTitle)
                        .foregroundColor(MeritColor.label)

                    Text("We will calibrate bite-sized quiz questions to your child's age band.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, MeritSpacing.small)

                // List of already added children
                if !children.isEmpty {
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        HStack {
                            Text("Children Added (\(children.count)/\(AppConfig.maxChildrenPerParent))")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)
                            Spacer()
                        }

                        ForEach(children) { child in
                            HStack(spacing: MeritSpacing.medium) {
                                Text(child.avatar.emoji)
                                    .font(.system(size: 32))
                                    .frame(width: 48, height: 48)
                                    .background(MeritColor.secondaryFill)
                                    .clipShape(Circle())

                                VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                                    Text(child.name)
                                        .font(MeritTypography.headline)
                                        .foregroundColor(MeritColor.label)

                                    Text(child.ageBand.displayLabel)
                                        .font(MeritTypography.subheadline)
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }

                                Spacer()

                                Button(action: {
                                    children.removeAll { $0.id == child.id }
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(MeritColor.destructive)
                                }
                                .accessibilityLabel("Remove \(child.name)")
                            }
                            .padding(MeritSpacing.medium)
                            .background(MeritColor.cardBackground)
                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                        }
                    }
                }

                // Child Input Form (if under child cap)
                if children.count < AppConfig.maxChildrenPerParent {
                    VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                        // Child Name Input
                        VStack(alignment: .leading, spacing: MeritSpacing.small) {
                            Text(children.isEmpty ? "Child's First Name" : "Add Another Child")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            TextField("e.g. Leo", text: $currentName)
                                .font(MeritTypography.body)
                                .padding(MeritSpacing.medium)
                                .background(MeritColor.cardBackground)
                                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                                .autocorrectionDisabled()
                        }

                        // Avatar Preset Picker
                        VStack(alignment: .leading, spacing: MeritSpacing.small) {
                            Text("Choose an Avatar")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: MeritSpacing.small) {
                                    ForEach(AvatarPreset.allCases, id: \.self) { preset in
                                        Button(action: {
                                            currentAvatar = preset
                                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                        }) {
                                            VStack(spacing: MeritSpacing.xxxSmall) {
                                                Text(preset.emoji)
                                                    .font(.system(size: 32))
                                                Text(preset.label)
                                                    .font(MeritTypography.caption2)
                                                    .foregroundColor(currentAvatar == preset ? MeritColor.accent : MeritColor.secondaryLabel)
                                            }
                                            .frame(width: 64, height: 68)
                                            .background(currentAvatar == preset ? MeritColor.accent.opacity(0.12) : MeritColor.cardBackground)
                                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                                            .overlay(
                                                RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                                                    .stroke(currentAvatar == preset ? MeritColor.accent : Color.clear, lineWidth: 2)
                                            )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }

                        // Age Band Selector Cards
                        VStack(alignment: .leading, spacing: MeritSpacing.small) {
                            Text("Age Band")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            VStack(spacing: MeritSpacing.small) {
                                ForEach(AgeBand.allCases, id: \.self) { band in
                                    Button(action: {
                                        currentAgeBand = band
                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    }) {
                                        HStack(alignment: .top, spacing: MeritSpacing.medium) {
                                            Image(systemName: currentAgeBand == band ? "largecircle.fill.circle" : "circle")
                                                .font(.system(size: 20))
                                                .foregroundColor(currentAgeBand == band ? MeritColor.accent : MeritColor.secondaryLabel)
                                                .padding(.top, 2)

                                            VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                                                Text(band.displayLabel)
                                                    .font(MeritTypography.headline)
                                                    .foregroundColor(MeritColor.label)

                                                Text(band.subtitle)
                                                    .font(MeritTypography.subheadline)
                                                    .foregroundColor(MeritColor.secondaryLabel)
                                            }

                                            Spacer()
                                        }
                                        .padding(MeritSpacing.medium)
                                        .background(currentAgeBand == band ? MeritColor.accent.opacity(0.08) : MeritColor.cardBackground)
                                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                                                .stroke(currentAgeBand == band ? MeritColor.accent : Color.clear, lineWidth: 1.5)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        if let validationError = validationError {
                            Text(validationError)
                                .font(MeritTypography.footnote)
                                .foregroundColor(MeritColor.destructive)
                        }

                        // Add Child Button (if multiple)
                        if !children.isEmpty {
                            MeritButton("Add Another Child", icon: "plus", style: .outline) {
                                addCurrentChild()
                            }
                        }
                    }
                }

                Spacer(minLength: MeritSpacing.large)

                // Continue Button
                MeritButton(
                    "Continue to Parent PIN",
                    icon: "arrow.right",
                    style: .primary,
                    action: {
                        if children.isEmpty {
                            if addCurrentChild() {
                                onContinue()
                            }
                        } else {
                            if !currentName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                _ = addCurrentChild()
                            }
                            onContinue()
                        }
                    }
                )
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 580)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                }
                .accessibilityLabel("Back")
            }
        }
    }

    private func stepHeader(step: String, title: String) -> some View {
        HStack {
            Text("Step \(step) • \(title)")
                .font(MeritTypography.footnote.weight(.semibold))
                .foregroundColor(MeritColor.accent)
                .padding(.horizontal, MeritSpacing.small)
                .padding(.vertical, MeritSpacing.xxSmall)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())

            Spacer()
        }
    }

    @discardableResult
    private func addCurrentChild() -> Bool {
        let trimmed = currentName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            validationError = "Please enter your child's first name."
            return false
        }

        guard children.count < AppConfig.maxChildrenPerParent else {
            validationError = "Maximum of \(AppConfig.maxChildrenPerParent) children reached."
            return false
        }

        let newChild = ChildDraft(
            name: trimmed,
            ageBand: currentAgeBand,
            avatar: currentAvatar
        )
        children.append(newChild)
        currentName = ""
        validationError = nil
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        return true
    }
}
