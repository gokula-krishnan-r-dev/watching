import Foundation
import Observation

@MainActor
@Observable
public final class OtpVerificationViewModel {
    public let email: String
    public var code: String = ""
    public var isLoading: Bool = false
    public var error: AppError?
    public var resendCooldownSeconds: Int = AppConfig.emailOtpResendCooldownSeconds

    private let completeParentAuthUseCase: CompleteParentAuthUseCase
    @ObservationIgnored private var timerTask: Task<Void, Never>?

    public init(
        email: String,
        completeParentAuthUseCase: CompleteParentAuthUseCase? = nil
    ) {
        self.email = email
        self.completeParentAuthUseCase = completeParentAuthUseCase ?? CompleteParentAuthUseCase()
        startCooldownTimer()
    }

    deinit {
        timerTask?.cancel()
    }

    public var canResend: Bool {
        resendCooldownSeconds <= 0 && !isLoading
    }

    public var isCodeComplete: Bool {
        code.filter { $0.isNumber }.count == AppConfig.emailOtpLength
    }

    @MainActor
    public func onCodeChanged(_ newCode: String, onSuccess: @escaping (ParentAuthResult) -> Void) async {
        let digits = String(newCode.filter { $0.isNumber }.prefix(AppConfig.emailOtpLength))
        self.code = digits
        self.error = nil

        if digits.count == AppConfig.emailOtpLength && !isLoading {
            await verify(onSuccess: onSuccess)
        }
    }

    @MainActor
    public func verify(onSuccess: @escaping (ParentAuthResult) -> Void) async {
        guard !isLoading else { return }
        error = nil

        if let validationError = CredentialsValidator.otpError(code) {
            error = .validation(validationError)
            return
        }

        isLoading = true
        let outcome = await completeParentAuthUseCase.verifyEmailOtp(email: email, code: code)
        isLoading = false

        switch outcome {
        case .success(let result):
            onSuccess(result)
        case .failure(let appError):
            self.error = appError
        }
    }

    @MainActor
    public func resendCode() async {
        guard canResend else { return }
        isLoading = true
        error = nil

        let outcome = await completeParentAuthUseCase.sendEmailOtp(email: email)
        isLoading = false

        switch outcome {
        case .success:
            resendCooldownSeconds = AppConfig.emailOtpResendCooldownSeconds
            startCooldownTimer()
        case .failure(let appError):
            self.error = appError
        }
    }

    private func startCooldownTimer() {
        timerTask?.cancel()
        timerTask = Task { @MainActor [weak self] in
            while let self = self, self.resendCooldownSeconds > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { break }
                self.resendCooldownSeconds -= 1
            }
        }
    }
}
