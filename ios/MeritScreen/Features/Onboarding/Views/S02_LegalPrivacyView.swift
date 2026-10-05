import SwiftUI

/// S02: Legal & Kids Privacy Consent screen.
/// Explicit COPPA-compliant parental consent before account creation.
/// Mirrors `com.meritscreen.feature.onboarding.ui.LegalConsentScreen`.
public struct S02_LegalPrivacyView: View {
    @Binding public var consentGiven: Bool
    public let onContinue: () -> Void
    public let onBack: () -> Void

    public init(
        consentGiven: Binding<Bool>,
        onContinue: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self._consentGiven = consentGiven
        self.onContinue = onContinue
        self.onBack = onBack
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Header with Step Indicator
                stepHeader(step: "1 of 4", title: "Parental Consent")

                // Headline & Subtitle
                VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                    Text("A safe space, by design")
                        .font(MeritTypography.largeTitle)
                        .foregroundColor(MeritColor.label)

                    Text("Before setting up your family account, here is our strict privacy pledge.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, MeritSpacing.small)

                // Privacy Value Cards
                VStack(spacing: MeritSpacing.medium) {
                    privacyCard(
                        icon: "person.crop.circle.badge.xmark",
                        title: "No child accounts, ever",
                        description: "Children do not have email addresses, passwords, or public accounts. Their device is simply paired to yours."
                    )

                    privacyCard(
                        icon: "hand.raised.fill",
                        title: "Zero tracking, zero ads",
                        description: "No advertising networks, behavior trackers, or data brokering. MeritScreen is funded by subscriptions, not surveillance."
                    )

                    privacyCard(
                        icon: "lock.shield.fill",
                        title: "Local-first on device",
                        description: "Quizzes and block timers run on the device offline. Your child's learning history stays private."
                    )
                }

                // Explicit Parental Consent Checkbox
                Button(action: {
                    consentGiven.toggle()
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }) {
                    HStack(alignment: .top, spacing: MeritSpacing.medium) {
                        Image(systemName: consentGiven ? "checkmark.square.fill" : "square")
                            .font(.system(size: 24))
                            .foregroundColor(consentGiven ? MeritColor.accent : MeritColor.secondaryLabel)

                        Text("I am a parent or legal guardian, and I consent to MeritScreen's kids privacy policy and offline data storage.")
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.label)
                            .multilineTextAlignment(.leading)
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                }
                .buttonStyle(.plain)

                Spacer(minLength: MeritSpacing.large)

                // Navigation Controls
                MeritButton(
                    "Continue to Family Space",
                    icon: "arrow.right",
                    style: .primary,
                    isEnabled: consentGiven,
                    action: onContinue
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

    private func privacyCard(icon: String, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundColor(MeritColor.accent)
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                Text(title)
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)

                Text(description)
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            Spacer()
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
    }
}
