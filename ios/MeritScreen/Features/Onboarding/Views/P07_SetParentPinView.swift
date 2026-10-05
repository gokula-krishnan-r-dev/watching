import SwiftUI

/// P07: Set Parent PIN screen.
/// 2-step setup (enter 4 digits + confirm). Hashes PIN on-device with PBKDF2 before saving.
/// Mirrors `com.meritscreen.feature.onboarding.ui.SetParentPinScreen`.
public struct P07_SetParentPinView: View {
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
                // Header with Step Indicator
                stepHeader(step: "4 of 4", title: "Parent PIN")

                PinPadView(
                    pin: step == .enterInitial ? $initialPin : $confirmPin,
                    title: step == .enterInitial ? "Create a 4-Digit PIN" : "Confirm Your PIN",
                    subtitle: step == .enterInitial
                        ? "This PIN protects parent controls and allows on-device recovery."
                        : "Re-enter the same 4-digit PIN to confirm.",
                    errorMessage: errorMessage
                ) { enteredPin in
                    handlePinCompleted(enteredPin)
                }

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
            }
            .padding(MeritSpacing.large)
            .responsiveContainer(maxWidth: 580)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: {
                    if step == .confirm {
                        step = .enterInitial
                        confirmPin = ""
                        errorMessage = nil
                    } else {
                        onBack()
                    }
                }) {
                    Image(systemName: "chevron.left")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                }
                .accessibilityLabel("Back")
            }
        }
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
