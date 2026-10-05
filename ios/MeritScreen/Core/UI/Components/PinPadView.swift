import SwiftUI

/// Accessible 4-digit numeric PIN entry pad with visual indicators and haptic feedback.
public struct PinPadView: View {
    @Binding public var pin: String
    public let title: String
    public let subtitle: String?
    public let errorMessage: String?
    public let onComplete: (String) -> Void

    public init(
        pin: Binding<String>,
        title: String = "Enter Parent PIN",
        subtitle: String? = "Enter your 4-digit PIN to continue",
        errorMessage: String? = nil,
        onComplete: @escaping (String) -> Void
    ) {
        self._pin = pin
        self.title = title
        self.subtitle = subtitle
        self.errorMessage = errorMessage
        self.onComplete = onComplete
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            VStack(spacing: MeritSpacing.xSmall) {
                Text(title)
                    .font(MeritTypography.title2)
                    .foregroundColor(MeritColor.label)

                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                }
            }

            // PIN Dots Indicator
            HStack(spacing: MeritSpacing.large) {
                ForEach(0..<4, id: \.self) { index in
                    Circle()
                        .fill(index < pin.count ? MeritColor.accent : Color.clear)
                        .frame(width: 18, height: 18)
                        .overlay(
                            Circle()
                                .stroke(index < pin.count ? MeritColor.accent : MeritColor.secondaryLabel, lineWidth: 2)
                        )
                }
            }
            .padding(.vertical, MeritSpacing.small)

            if let errorMessage = errorMessage {
                Text(errorMessage)
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.destructive)
                    .multilineTextAlignment(.center)
            }

            // Numeric Keypad
            VStack(spacing: MeritSpacing.medium) {
                ForEach([[1, 2, 3], [4, 5, 6], [7, 8, 9]], id: \.self) { row in
                    HStack(spacing: MeritSpacing.xLarge) {
                        ForEach(row, id: \.self) { digit in
                            keypadButton("\(digit)") {
                                appendDigit("\(digit)")
                            }
                        }
                    }
                }

                HStack(spacing: MeritSpacing.xLarge) {
                    // Empty placeholder
                    Color.clear
                        .frame(width: 72, height: 72)

                    keypadButton("0") {
                        appendDigit("0")
                    }

                    // Delete button
                    Button(action: deleteDigit) {
                        Image(systemName: "delete.left.fill")
                            .font(.system(size: 22))
                            .foregroundColor(MeritColor.label)
                            .frame(width: 72, height: 72)
                            .background(MeritColor.secondaryFill)
                            .clipShape(Circle())
                    }
                    .accessibilityLabel("Delete")
                }
            }
        }
        .responsiveContainer(maxWidth: 420)
        .padding(MeritSpacing.large)
    }

    private func keypadButton(_ text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 28, weight: .medium, design: .rounded))
                .foregroundColor(MeritColor.label)
                .frame(width: 72, height: 72)
                .background(MeritColor.secondaryFill)
                .clipShape(Circle())
        }
        .accessibilityLabel(text)
    }

    private func appendDigit(_ digit: String) {
        guard pin.count < 4 else { return }
        pin.append(digit)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if pin.count == 4 {
            onComplete(pin)
        }
    }

    private func deleteDigit() {
        guard !pin.isEmpty else { return }
        pin.removeLast()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
