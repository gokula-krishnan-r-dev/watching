import SwiftUI

/// C01: Child Device Pairing Screen.
/// Provides QR code camera scanner and manual 6-digit keypad fallback.
/// Mirrors `com.meritscreen.feature.authentication.ui.ChildPairingScreen` in Android.
public struct C01_ChildPairingView: View {
    public let onPaired: (ChildPairingCredential) -> Void
    public let onBack: () -> Void

    @State private var viewModel = ChildPairingViewModel()
    @State private var selectedTab: PairingInputMode = .camera
    @FocusState private var isCodeFocused: Bool

    public enum PairingInputMode: String, CaseIterable {
        case camera = "Scan QR"
        case manual = "Enter Code"
    }

    public init(
        onPaired: @escaping (ChildPairingCredential) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.onPaired = onPaired
        self.onBack = onBack
        #if targetEnvironment(simulator)
        self._selectedTab = State(initialValue: .manual)
        #else
        self._selectedTab = State(initialValue: .camera)
        #endif

        if let idx = ProcessInfo.processInfo.arguments.firstIndex(of: "-pairingCode"),
           idx + 1 < ProcessInfo.processInfo.arguments.count {
            let initialCode = ProcessInfo.processInfo.arguments[idx + 1]
            let vm = ChildPairingViewModel()
            vm.code = initialCode
            self._viewModel = State(initialValue: vm)
            self._selectedTab = State(initialValue: .manual)
        }
    }

    public var body: some View {
        NavigationStack {
            Group {
                if let credential = viewModel.pairedCredential {
                    C02_PairingSuccessView(credential: credential) {
                        onPaired(credential)
                    }
                } else {
                    mainPairingView
                }
            }
            .background(MeritColor.background.ignoresSafeArea())
            .toolbar {
                if viewModel.pairedCredential == nil {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(action: onBack) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                Text("Back")
                            }
                            .foregroundColor(MeritColor.accent)
                        }
                    }
                }
            }
        }
    }

    private var mainPairingView: some View {
        VStack(spacing: MeritSpacing.large) {
            // Header
            VStack(spacing: MeritSpacing.small) {
                Text("Set Up Child Device")
                    .font(MeritTypography.title)
                    .foregroundColor(MeritColor.label)

                Text("Pair this iPhone or iPad with the parent app to activate protection.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, MeritSpacing.medium)

            // Segmented Picker (Camera vs Manual)
            Picker("Mode", selection: $selectedTab) {
                ForEach(PairingInputMode.allCases, id: \.self) { mode in
                    Text(mode.rawValue).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, MeritSpacing.small)

            // Error Banner
            if let error = viewModel.error {
                StateErrorCard(
                    message: error.userMessage,
                    onRetry: nil
                )
            }

            // Tab Content
            if selectedTab == .camera {
                cameraScannerSection
            } else {
                manualCodeSection
            }

            Spacer()

            if viewModel.isSubmitting {
                StateLoadingView("Connecting device...")
                    .frame(height: 60)
            }
        }
        .padding(.horizontal, MeritSpacing.large)
        .responsiveContainer(maxWidth: 480)
    }

    private var cameraScannerSection: some View {
        VStack(spacing: MeritSpacing.medium) {
            CameraQrScannerView { parsed in
                Task {
                    await viewModel.onQrScanned(parsed) { _ in }
                }
            }
            .frame(height: 300)

            Text("Position the QR code from the parent's phone within the frame.")
                .font(MeritTypography.footnote)
                .foregroundColor(MeritColor.secondaryLabel)
                .multilineTextAlignment(.center)
        }
    }

    private var manualCodeSection: some View {
        VStack(spacing: MeritSpacing.xLarge) {
            Text("Enter the 6-digit code shown on the parent's device")
                .font(MeritTypography.subheadline)
                .foregroundColor(MeritColor.secondaryLabel)
                .multilineTextAlignment(.center)

            // 6-digit Code Display
            HStack(spacing: MeritSpacing.small) {
                ForEach(0..<6, id: \.self) { index in
                    let digit = getDigit(at: index)
                    Text(digit)
                        .font(.system(size: 28, weight: .bold, design: .monospaced))
                        .foregroundColor(MeritColor.label)
                        .frame(width: 44, height: 56)
                        .background(MeritColor.secondaryBackground)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous)
                                .stroke(
                                    isCurrentDigit(at: index) ? MeritColor.accent : MeritColor.separator,
                                    lineWidth: isCurrentDigit(at: index) ? 2 : 1
                                )
                        )
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                isCodeFocused = true
            }

            // Hidden text field for number pad input
            TextField("", text: Binding(
                get: { viewModel.code },
                set: { newValue in
                    Task {
                        await viewModel.onCodeChanged(newValue) { _ in }
                    }
                }
            ))
            .keyboardType(.numberPad)
            .focused($isCodeFocused)
            .frame(width: 1, height: 1)
            .opacity(0.01)

            MeritButton(
                "Pair Device",
                icon: "link",
                style: .primary,
                isLoading: viewModel.isSubmitting,
                action: {
                    isCodeFocused = false
                    Task {
                        await viewModel.submit { _ in }
                    }
                }
            )
            .disabled(viewModel.isSubmitting || viewModel.code.count != AppConfig.pairingCodeLength)
        }
        .padding(.top, MeritSpacing.large)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                isCodeFocused = true
                if viewModel.code.count == AppConfig.pairingCodeLength && !viewModel.isSubmitting && viewModel.pairedCredential == nil {
                    Task {
                        await viewModel.submit { _ in }
                    }
                }
            }
        }
    }

    private func getDigit(at index: Int) -> String {
        guard index < viewModel.code.count else { return "" }
        let charIndex = viewModel.code.index(viewModel.code.startIndex, offsetBy: index)
        return String(viewModel.code[charIndex])
    }

    private func isCurrentDigit(at index: Int) -> Bool {
        isCodeFocused && (index == viewModel.code.count || (index == 5 && viewModel.code.count == 6))
    }
}
