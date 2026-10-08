import SwiftUI
import UIKit

/// P08 / P09: Parent Device Pairing Screen.
/// Displays QR code and 6-digit fallback pairing code and listens for child device handshake.
/// Mirrors `com.meritscreen.feature.authentication.ui.ParentPairingScreen` in Android.
public struct P08_ParentPairingView: View {
    public let childId: String
    public let onFinished: () -> Void
    public let onBack: () -> Void

    @State private var viewModel: ParentPairingViewModel
    @State private var copiedToClipboard = false

    public init(
        childId: String,
        onFinished: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.childId = childId
        self.onFinished = onFinished
        self.onBack = onBack
        self._viewModel = State(initialValue: ParentPairingViewModel(childId: childId))
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.xLarge) {
                    if viewModel.isLoading {
                        StateLoadingView("Preparing pairing code...")
                            .frame(minHeight: 320)
                    } else if let error = viewModel.error {
                        StateErrorCard(
                            message: error.userMessage,
                            onRetry: {
                                Task { await viewModel.refresh() }
                            }
                        )
                        .padding(.top, MeritSpacing.large)
                    } else if viewModel.devicePaired {
                        pairedSuccessView
                    } else {
                        pairingActiveView
                    }
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 520)
            }
            .background(MeritColor.background.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onBack) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .foregroundColor(MeritColor.accent)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await viewModel.refresh() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(MeritColor.accent)
                    }
                }
            }
            .task {
                await viewModel.start()
            }
        }
    }

    // Active Pairing State (QR + 6-digit Code)
    private var pairingActiveView: some View {
        VStack(spacing: MeritSpacing.large) {
            // Child Header Badge
            if let child = viewModel.child {
                HStack(spacing: MeritSpacing.medium) {
                    Text(child.avatar.emoji)
                        .font(.system(size: 36))
                        .frame(width: 52, height: 52)
                        .background(MeritColor.secondaryBackground)
                        .clipShape(Circle())

                    VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                        Text("Pair \(child.displayName)'s Device")
                            .font(MeritTypography.title3)
                            .foregroundColor(MeritColor.label)

                        Text("Ages \(child.ageBand.displayLabel)")
                            .font(MeritTypography.footnote)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    Spacer()
                }
                .padding(MeritSpacing.medium)
                .background(MeritColor.secondaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
            }

            // QR Code Card
            if let offer = viewModel.offer,
               let qrImage = QRCodeGenerator.generateImage(from: offer.qrPayload, targetSize: 220) {
                VStack(spacing: MeritSpacing.medium) {
                    Image(uiImage: qrImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                        .padding(MeritSpacing.medium)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 4)

                    Text("Scan with Watching on child's phone")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
            }

            // Fallback 6-digit Code
            if let offer = viewModel.offer {
                VStack(spacing: MeritSpacing.small) {
                    Text("or enter code manually:")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)

                    HStack(spacing: MeritSpacing.small) {
                        Text(offer.code)
                            .font(.system(size: 32, weight: .heavy, design: .monospaced))
                            .tracking(6)
                            .foregroundColor(MeritColor.label)

                        Button {
                            UIPasteboard.general.string = offer.code
                            copiedToClipboard = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                copiedToClipboard = false
                            }
                        } label: {
                            Image(systemName: copiedToClipboard ? "checkmark.circle.fill" : "doc.on.doc")
                                .font(.system(size: 20))
                                .foregroundColor(copiedToClipboard ? MeritColor.pass : MeritColor.accent)
                        }
                    }
                    .padding(.horizontal, MeritSpacing.large)
                    .padding(.vertical, MeritSpacing.small)
                    .background(MeritColor.secondaryBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                }
            }

            // Expiry & Countdown
            HStack(spacing: MeritSpacing.xSmall) {
                Image(systemName: "clock")
                    .foregroundColor(MeritColor.secondaryLabel)
                Text("Code expires in \(formatSeconds(viewModel.remainingSeconds))")
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.secondaryLabel)

                if viewModel.remainingSeconds <= 60 {
                    Button("Refresh") {
                        Task { await viewModel.refresh() }
                    }
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.accent)
                }
            }

            // Step instructions
            VStack(alignment: .leading, spacing: MeritSpacing.small) {
                stepRow(number: "1", text: "Install Watching on the child's iPhone or iPad")
                stepRow(number: "2", text: "Select 'Set Up Child Device' on their device")
                stepRow(number: "3", text: "Scan this QR code or enter the 6-digit code")
            }
            .padding(MeritSpacing.medium)
            .background(MeritColor.secondaryBackground.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
        }
        .padding(.vertical, MeritSpacing.medium)
    }

    // Success State
    private var pairedSuccessView: some View {
        VStack(spacing: MeritSpacing.large) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(MeritColor.pass)
                .padding(.top, MeritSpacing.xLarge)

            Text("Device Paired!")
                .font(MeritTypography.largeTitle)
                .foregroundColor(MeritColor.label)

            if let device = viewModel.pairedDevice {
                Text("\(device.displayName) is now connected and protected.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            } else {
                Text("Child device has successfully connected.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            MeritButton(
                "Go to Dashboard",
                icon: "arrow.right",
                style: .primary,
                action: onFinished
            )
            .padding(.top, MeritSpacing.medium)
        }
    }

    private func stepRow(number: String, text: String) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.small) {
            Text(number)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(MeritColor.accent)
                .frame(width: 20, height: 20)
                .background(MeritColor.accent.opacity(0.15))
                .clipShape(Circle())

            Text(text)
                .font(MeritTypography.footnote)
                .foregroundColor(MeritColor.secondaryLabel)
        }
    }

    private func formatSeconds(_ seconds: Int) -> String {
        let mins = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
