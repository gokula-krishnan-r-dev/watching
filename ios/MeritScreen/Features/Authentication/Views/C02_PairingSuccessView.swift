import SwiftUI

/// C02: Child Device Pairing Success Screen.
/// Confirms the child profile and welcomes the child to MeritScreen.
public struct C02_PairingSuccessView: View {
    public let credential: ChildPairingCredential
    public let onContinue: () -> Void

    public init(
        credential: ChildPairingCredential,
        onContinue: @escaping () -> Void
    ) {
        self.credential = credential
        self.onContinue = onContinue
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            Spacer()

            // Success Check & Animation
            VStack(spacing: MeritSpacing.medium) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 72))
                    .foregroundColor(MeritColor.pass)

                Text("You're Connected!")
                    .font(MeritTypography.largeTitle)
                    .foregroundColor(MeritColor.label)

                Text("This device is now linked to your family.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            // Child Profile Card
            VStack(spacing: MeritSpacing.small) {
                Text(credential.displayName)
                    .font(MeritTypography.title2)
                    .foregroundColor(MeritColor.label)

                Text("Protected by Parent PIN")
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.secondaryLabel)
            }
            .padding(.vertical, MeritSpacing.large)
            .padding(.horizontal, MeritSpacing.xxLarge)
            .background(MeritColor.secondaryBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

            Spacer()

            // Continue CTA
            MeritButton(
                "Enter MeritScreen",
                icon: "arrow.right",
                style: .primary,
                action: onContinue
            )
            .padding(.bottom, MeritSpacing.large)
        }
        .padding(.horizontal, MeritSpacing.large)
        .responsiveContainer(maxWidth: 480)
        .background(MeritColor.background.ignoresSafeArea())
    }
}
