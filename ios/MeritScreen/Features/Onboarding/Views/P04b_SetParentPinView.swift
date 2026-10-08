import SwiftUI

/// P04b: Set Parent PIN Bypass Gate Screen.
/// 2-step setup (enter 4 digits + confirm). Hashes PIN on-device with PBKDF2 before saving.
/// Mirrors `com.meritscreen.feature.onboarding.ui.SetParentPinScreen` in Android.
public struct P04b_SetParentPinView: View {
    public let onComplete: (String) -> Void
    public let onBack: () -> Void

    private enum PinStep {
        case enterInitial
        case confirm
    }

    @State private var step: PinStep = .enterInitial
    @State private var initialPin: String = ""
    @State private var confirmPin: String = ""
    @State private var errorMessage: String?

    public init(
        onComplete: @escaping (String) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.onComplete = onComplete
        self.onBack = onBack
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Top Bar
                HStack {
                    Button(action: {
                        if step == .confirm {
                            step = .enterInitial
                            confirmPin = ""
                            errorMessage = nil
                        } else {
                            onBack()
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.accent)
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(MeritColor.accent)
                        Text("Step 5 of 5 • Parent Security")
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

                // PIN Pad Component
                PinPadView(
                    pin: step == .enterInitial ? $initialPin : $confirmPin,
                    title: step == .enterInitial ? "Create a 4-Digit Parent PIN" : "Confirm Your PIN",
                    subtitle: step == .enterInitial
                        ? "Only parents can exit Watching, change schedules, or approve bonus time."
                        : "Re-enter your 4-digit PIN to confirm.",
                    errorMessage: errorMessage
                ) { enteredPin in
                    handlePinCompleted(enteredPin)
                }

                // Security Callout Box
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 16))
                            .foregroundColor(MeritColor.accent)

                        Text("Cryptographic Bypass Guarantee")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                    }

                    Text("Your PIN is salted and hashed on-device using PBKDF2. It is never logged, stored in plain text, or uploaded to servers.")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(MeritSpacing.medium)
                .background(MeritColor.secondaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                        .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
                )

                if step == .confirm {
                    Button("Start Over") {
                        step = .enterInitial
                        initialPin = ""
                        confirmPin = ""
                        errorMessage = nil
                    }
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.accent)
                }

                Spacer(minLength: MeritSpacing.medium)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }

    private func handlePinCompleted(_ entered: String) {
        switch step {
        case .enterInitial:
            step = .confirm
            errorMessage = nil
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        case .confirm:
            if entered == initialPin {
                errorMessage = nil
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                let hash = Pbkdf2PinHasher.shared.hash(pin: entered)
                onComplete(hash)
            } else {
                errorMessage = "PINs do not match. Please try again."
                confirmPin = ""
                UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            }
        }
    }
}
