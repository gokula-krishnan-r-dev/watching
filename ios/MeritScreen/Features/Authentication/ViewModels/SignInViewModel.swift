import AuthenticationServices
import Foundation
import Observation

@MainActor
@Observable
public final class SignInViewModel {
    public var email: String = ""
    public var isLoading: Bool = false
    public var error: AppError?

    private let completeParentAuthUseCase: CompleteParentAuthUseCase

    public init(completeParentAuthUseCase: CompleteParentAuthUseCase? = nil) {
        self.completeParentAuthUseCase = completeParentAuthUseCase ?? CompleteParentAuthUseCase()
    }

    public var isEmailValid: Bool {
        CredentialsValidator.emailError(email) == nil
    }

    @MainActor
    public func sendOtp(onSuccess: @escaping (String) -> Void) async {
        guard !isLoading else { return }
        error = nil

        if let validationError = CredentialsValidator.emailError(email) {
            error = .validation(validationError)
            return
        }

        isLoading = true
        let normalized = CredentialsValidator.normalizeEmail(email)
        let outcome = await completeParentAuthUseCase.sendEmailOtp(email: normalized)
        isLoading = false

        switch outcome {
        case .success:
            onSuccess(normalized)
        case .failure(let appError):
            self.error = appError
        }
    }

    @MainActor
    public func handleAppleSignIn(result: Result<ASAuthorization, Error>, onSuccess: @escaping (ParentAuthResult) -> Void) async {
        guard !isLoading else { return }
        error = nil

        switch result {
        case .success(let authorization):
            guard let appleCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                error = .auth("Unable to retrieve Apple ID credentials.")
                return
            }

            isLoading = true
            let user = AuthUser(
                uid: appleCredential.user,
                email: appleCredential.email
            )
            let outcome = await completeParentAuthUseCase.completeSignIn(user: user)
            isLoading = false

            switch outcome {
            case .success(let authResult):
                onSuccess(authResult)
            case .failure(let appError):
                self.error = appError
            }

        case .failure(let failure):
            let nsError = failure as NSError
            if nsError.code != ASAuthorizationError.canceled.rawValue {
                error = .auth(failure.localizedDescription)
            }
        }
    }
}
