import SwiftUI

/// P05: Create Family Space screen.
/// Allows parents to optionally label their household space before adding child profiles.
/// Mirrors `com.meritscreen.feature.onboarding.ui.CreateFamilyScreen`.
public struct P05_CreateFamilyView: View {
    @Binding public var familyName: String
    public let onContinue: () -> Void
    public let onBack: () -> Void

    private let suggestions = ["Our Family", "The Explorers", "Home Space", "Learning Hub"]

    public init(
        familyName: Binding<String>,
        onContinue: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self._familyName = familyName
        self.onContinue = onContinue
        self.onBack = onBack
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Header with Step Indicator
                stepHeader(step: "2 of 4", title: "Family Space")

                // Headline & Subtitle
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text("Name your family space")
                        .font(MeritTypography.largeTitle)
                        .foregroundColor(MeritColor.label)

                    Text("Optional. This is the friendly name shown on your parent dashboard.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, MeritSpacing.small)

                // Family Name Input Field
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Family Name")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    HStack {
                        Image(systemName: "house.fill")
                            .foregroundColor(MeritColor.secondaryLabel)

                        TextField("e.g. The Taylor Family", text: $familyName)
                            .font(MeritTypography.body)
                            .autocorrectionDisabled()

                        if !familyName.isEmpty {
                            Button(action: { familyName = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                }

                // Quick Suggestions Chips
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Suggestions")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: MeritSpacing.small) {
                            ForEach(suggestions, id: \.self) { suggestion in
                                Button(action: {
                                    familyName = suggestion
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                }) {
                                    Text(suggestion)
                                        .font(MeritTypography.subheadline)
                                        .foregroundColor(familyName == suggestion ? MeritColor.accent : MeritColor.label)
                                        .padding(.horizontal, MeritSpacing.medium)
                                        .padding(.vertical, MeritSpacing.small)
                                        .background(familyName == suggestion ? MeritColor.accent.opacity(0.12) : MeritColor.cardBackground)
                                        .clipShape(Capsule())
                                        .overlay(
                                            Capsule()
                                                .stroke(familyName == suggestion ? MeritColor.accent : Color.clear, lineWidth: 1)
                                        )
                                }
                            }
                        }
                    }
                }

                Spacer(minLength: MeritSpacing.xxLarge)

                // Actions
                VStack(spacing: MeritSpacing.medium) {
                    MeritButton(
                        "Continue to Add Child",
                        icon: "arrow.right",
                        style: .primary,
                        action: onContinue
                    )

                    if familyName.isEmpty {
                        Button("Skip for Now", action: onContinue)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
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
}
