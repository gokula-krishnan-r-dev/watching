import Foundation

/// Clears the parent session and local onboarding draft upon signing out.
/// Mirrors `com.meritscreen.feature.authentication.domain.SignOutParentUseCase` in Android.
public final class SignOutParentUseCase: @unchecked Sendable {
    private let authClient: AuthClientProtocol
    private let sessionRepository: ParentSessionRepository
    private let draftRepository: OnboardingDraftRepository

    public init(
        authClient: AuthClientProtocol = FirebaseAuthClient.shared,
        sessionRepository: ParentSessionRepository = .shared,
        draftRepository: OnboardingDraftRepository = .shared
    ) {
        self.authClient = authClient
        self.sessionRepository = sessionRepository
        self.draftRepository = draftRepository
    }

    public func execute() {
        try? authClient.signOut()
        sessionRepository.clear()
        draftRepository.clear()
    }
}
