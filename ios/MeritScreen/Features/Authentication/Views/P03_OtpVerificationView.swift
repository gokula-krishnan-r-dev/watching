import SwiftUI

/// P03: Email OTP Verification Screen.
/// Mirrors `com.meritscreen.feature.authentication.ui.OtpVerificationScreen` in Android.
public struct P03_OtpVerificationView: View {
    public let email: String
    public let onVerified: (ParentAuthResult) -> Void
    public let onChangeEmail: () -> Void

    @State private var viewModel: OtpVerificationViewModel
    @FocusState private var isCodeFocused: Bool

    public init(
        email: String,
        onVerified: @escaping (ParentAuthResult) -> Void,
        onChangeEmail: @escaping () -> Void
    ) {
        self.email = email
        self.onVerified = onVerified
        self.onChangeEmail = onChangeEmail
        self._viewModel = State(initialValue: OtpVerificationViewModel(email: email))
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.xLarge) {
                    // Header
                    VStack(spacing: MeritSpacing.small) {
                        Image(systemName: "envelope.badge.shield.half.filled")
                            .font(.system(size: 54))
                            .foregroundColor(MeritColor.accent)
                            .padding(.bottom, MeritSpacing.small)

                        Text("Check Your Email")
                            .font(MeritTypography.title)
                            .foregroundColor(MeritColor.label)

                        Text("We sent a 6-digit verification code to:")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)

                        Text(email)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                            .padding(.top, MeritSpacing.xxxSmall)
                    }
                    .padding(.top, MeritSpacing.large)

                    // Error Banner
                    if let error = viewModel.error {
                        StateErrorCard(
                            message: error.userMessage,
                            onRetry: nil
                        )
                    }

                    // 6-digit Code Input
                    VStack(spacing: MeritSpacing.medium) {
                        HStack(spacing: MeritSpacing.small) {
                            ForEach(0..<6, id: \.self) { index in
                                let digit = getDigit(at: index)
                                Text(digit)
                                    .font(.system(size: 26, weight: .bold, design: .rounded))
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

                        // Invisible actual TextField for native keyboard & autofill
                        TextField("", text: Binding(
                            get: { viewModel.code },
                            set: { newValue in
                                Task {
                                    await viewModel.onCodeChanged(newValue, onSuccess: onVerified)
                                }
                            }
                        ))
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .focused($isCodeFocused)
                        .frame(width: 1, height: 1)
                        .opacity(0.01)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        isCodeFocused = true
                    }

                    // Verify CTA
                    MeritButton(
                        "Verify Code",
                        icon: "checkmark",
                        style: .primary,
                        isLoading: viewModel.isLoading,
                        action: {
                            isCodeFocused = false
                            Task {
                                await viewModel.verify(onSuccess: onVerified)
                            }
                        }
                    )
                    .disabled(viewModel.isLoading || !viewModel.isCodeComplete)

                    // Resend & Cooldown
                    VStack(spacing: MeritSpacing.small) {
                        if viewModel.resendCooldownSeconds > 0 {
                            HStack(spacing: MeritSpacing.xxxSmall) {
                                Image(systemName: "clock")
                                    .font(MeritTypography.caption)
                                Text("Resend code in \(viewModel.resendCooldownSeconds)s")
                                    .font(MeritTypography.footnote)
                            }
                            .foregroundColor(MeritColor.secondaryLabel)
                        } else {
                            Button {
                                Task {
                                    await viewModel.resendCode()
                                }
                            } label: {
                                Text("Resend Verification Code")
                                    .font(MeritTypography.subheadline)
                                    .foregroundColor(MeritColor.accent)
                            }
                            .disabled(viewModel.isLoading)
                        }

                        Button(action: onChangeEmail) {
                            Text("Use a different email")
                                .font(MeritTypography.footnote)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                        .padding(.top, MeritSpacing.xSmall)
                    }
                    .padding(.top, MeritSpacing.small)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 460)
            }
            .background(MeritColor.background.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onChangeEmail) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Email")
                        }
                        .foregroundColor(MeritColor.accent)
                    }
                }
            }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    isCodeFocused = true
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
