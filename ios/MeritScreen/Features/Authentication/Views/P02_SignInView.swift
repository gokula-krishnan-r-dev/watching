import AuthenticationServices
import SwiftUI

/// P02: Parent Sign-In / Welcome Screen.
/// Mirrors `com.meritscreen.feature.authentication.ui.SignInScreen` in Android.
public struct P02_SignInView: View {
    public let onCodeSent: (String) -> Void
    public let onSignedIn: (ParentAuthResult) -> Void
    public let onBack: () -> Void

    @State private var viewModel = SignInViewModel()
    @FocusState private var isEmailFocused: Bool

    public init(
        onCodeSent: @escaping (String) -> Void,
        onSignedIn: @escaping (ParentAuthResult) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.onCodeSent = onCodeSent
        self.onSignedIn = onSignedIn
        self.onBack = onBack
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.xLarge) {
                    // Header
                    VStack(spacing: MeritSpacing.medium) {
                        Image("WatchingLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 72, height: 72)
                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                        Text("Parent Sign In")
                            .font(MeritTypography.title)
                            .foregroundColor(MeritColor.label)

                        Text("Enter your email to receive a secure 6-digit verification code.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, MeritSpacing.medium)
                    }
                    .padding(.top, MeritSpacing.large)

                    // Error Banner
                    if let error = viewModel.error {
                        StateErrorCard(
                            message: error.userMessage,
                            onRetry: nil
                        )
                    }

                    // Email Input Field
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        Text("Email Address")
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.secondaryLabel)

                        HStack(spacing: MeritSpacing.medium) {
                            Image(systemName: "envelope.fill")
                                .foregroundColor(MeritColor.secondaryLabel)
                                .frame(width: 24)

                            TextField("name@example.com", text: $viewModel.email)
                                .font(MeritTypography.body)
                                .keyboardType(.emailAddress)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled(true)
                                .focused($isEmailFocused)

                            if !viewModel.email.isEmpty {
                                Button {
                                    viewModel.email = ""
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }
                            }
                        }
                        .padding(MeritSpacing.medium)
                        .background(MeritColor.secondaryBackground)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                                .stroke(isEmailFocused ? MeritColor.accent : MeritColor.separator, lineWidth: 1)
                        )
                    }

                    // Send OTP CTA
                    MeritButton(
                        "Send Verification Code",
                        icon: "arrow.right",
                        style: .primary,
                        isLoading: viewModel.isLoading,
                        action: {
                            isEmailFocused = false
                            Task {
                                await viewModel.sendOtp(onSuccess: onCodeSent)
                            }
                        }
                    )
                    .disabled(viewModel.isLoading || viewModel.email.trimmingCharacters(in: .whitespaces).isEmpty)

                    // Divider
                    HStack {
                        Rectangle()
                            .fill(MeritColor.separator)
                            .frame(height: 1)
                        Text("or")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .padding(.horizontal, MeritSpacing.small)
                        Rectangle()
                            .fill(MeritColor.separator)
                            .frame(height: 1)
                    }
                    .padding(.vertical, MeritSpacing.small)

                    // Sign in with Apple Button
                    SignInWithAppleButton(
                        .signIn,
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                        },
                        onCompletion: { result in
                            Task {
                                await viewModel.handleAppleSignIn(result: result, onSuccess: onSignedIn)
                            }
                        }
                    )
                    .signInWithAppleButtonStyle(.whiteOutline)
                    .frame(height: 50)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    // Privacy Note
                    Text("By continuing, you agree to our Terms of Service and Privacy Policy. We never share your data.")
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.top, MeritSpacing.medium)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 480)
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
            }
        }
    }
}
