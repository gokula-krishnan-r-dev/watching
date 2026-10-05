import SwiftUI
import UIKit

/// P03c: Device Handshake / Pairing Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.OnboardingDeviceHandshakeScreen` in Android.
public struct P03c_DeviceHandshakeView: View {
    public let childName: String
    public let pairingCode: String
    public let customQrPayload: String?
    public let isRefreshing: Bool
    public let onRefresh: (() -> Void)?
    public let onPaired: () -> Void
    public let onSkip: () -> Void
    public let onBack: () -> Void

    @State private var isPaired: Bool = false
    @State private var copiedToClipboard: Bool = false
    @State private var secondsRemaining: Int = 899 // 15 minutes
    @State private var timerActive: Bool = true

    private var formattedCode: String {
        if pairingCode.count == 6 {
            let first = pairingCode.prefix(3)
            let last = pairingCode.suffix(3)
            return "\(first) \(last)"
        }
        return pairingCode
    }

    private var qrPayload: String {
        if let custom = customQrPayload, !custom.isEmpty {
            return custom
        }
        return "meritscreen://pair?code=\(pairingCode)&name=\(childName)"
    }

    public init(
        childName: String = "your child",
        pairingCode: String = String(format: "%06d", Int.random(in: 100000...999999)),
        qrPayload: String? = nil,
        isRefreshing: Bool = false,
        onRefresh: (() -> Void)? = nil,
        onPaired: @escaping () -> Void,
        onSkip: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.childName = childName
        self.pairingCode = pairingCode
        self.customQrPayload = qrPayload
        self.isRefreshing = isRefreshing
        self.onRefresh = onRefresh
        self.onPaired = onPaired
        self.onSkip = onSkip
        self.onBack = onBack
    }

    public var body: some View {
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

                    Text("Device Pairing")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Spacer()

                    Button("Skip", action: onSkip)
                        .font(MeritTypography.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.top, MeritSpacing.small)

                // Step Pill
                HStack(spacing: 6) {
                    Image(systemName: "wave.3.forward.circle.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(MeritColor.accent)
                    Text("Step 3 of 5 • Device Handshake")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())

                // Title & Subtitle
                VStack(spacing: MeritSpacing.xSmall) {
                    Text("Show this QR to \(childName)'s device")
                        .font(MeritTypography.title2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("On the child tablet or phone, open MeritScreen and tap Scan Parent QR — or type the 6-digit code below.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.small)
                }

                // Paired Banner (when connected)
                if isPaired {
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 26))
                            .foregroundColor(MeritColor.success)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Device Paired Successfully")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)
                            Text("Connected to \(childName)'s device — continuing...")
                                .font(MeritTypography.footnote)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                        Spacer()
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.success.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                    .transition(.scale.combined(with: .opacity))
                }

                // Primary QR Code Card
                VStack(spacing: MeritSpacing.medium) {
                    // Generated QR Image
                    if let qrImage = QRCodeGenerator.generateImage(from: qrPayload, targetSize: 220) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 200, height: 200)
                            .padding(14)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)
                    }

                    // 6-digit Code Display
                    VStack(spacing: 4) {
                        Text("6-DIGIT PAIRING CODE")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(MeritColor.secondaryLabel)
                            .tracking(1.5)

                        HStack(spacing: 12) {
                            Text(formattedCode)
                                .font(.system(size: 34, weight: .heavy, design: .monospaced))
                                .foregroundColor(MeritColor.accent)

                            Button(action: {
                                UIPasteboard.general.string = pairingCode
                                copiedToClipboard = true
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                    copiedToClipboard = false
                                }
                            }) {
                                Image(systemName: copiedToClipboard ? "checkmark" : "doc.on.doc")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(copiedToClipboard ? MeritColor.success : MeritColor.accent)
                                    .frame(width: 36, height: 36)
                                    .background(MeritColor.accent.opacity(0.1))
                                    .clipShape(Circle())
                            }
                        }

                        if copiedToClipboard {
                            Text("Code copied to clipboard!")
                                .font(.system(size: 11))
                                .foregroundColor(MeritColor.success)
                        }
                    }

                    // Countdown timer & Refresh
                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Image(systemName: "clock")
                                .font(.system(size: 12))
                                .foregroundColor(MeritColor.secondaryLabel)
                            Text("Expires in \(secondsRemaining / 60):\(String(format: "%02d", secondsRemaining % 60))")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }

                        if let onRefresh = onRefresh {
                            Text("•")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)

                            Button(action: onRefresh) {
                                HStack(spacing: 4) {
                                    Image(systemName: isRefreshing ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                                        .font(.system(size: 11))
                                    Text(isRefreshing ? "Refreshing…" : "Refresh code")
                                        .font(MeritTypography.caption)
                                        .fontWeight(.semibold)
                                }
                                .foregroundColor(MeritColor.accent)
                            }
                            .disabled(isRefreshing)
                        }
                    }
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity)
                .background(MeritColor.secondaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
                )

                // Pairing Action CTA
                VStack(spacing: MeritSpacing.small) {
                    if isPaired {
                        MeritButton(
                            "Continue to App Rules",
                            icon: "arrow.right",
                            style: .primary,
                            action: onPaired
                        )
                    }

                    Button("Skip pairing for now", action: onSkip)
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.top, 4)
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.background.ignoresSafeArea())
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            if secondsRemaining > 0 {
                secondsRemaining -= 1
            }
        }
    }
}
