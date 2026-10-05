import Foundation

/// Coordinates parent authentication, email OTP, and onboarding draft commit to Firestore.
/// Mirrors `com.meritscreen.feature.authentication.domain.CompleteParentAuthUseCase` in Android.
public final class CompleteParentAuthUseCase: @unchecked Sendable {
    private let authClient: AuthClientProtocol
    private let familyStore: FamilyStoreProtocol
    private let draftRepository: OnboardingDraftRepository
    private let sessionRepository: ParentSessionRepository
    private let networkMonitor: NetworkMonitorProtocol

    public init(
        authClient: AuthClientProtocol = FirebaseAuthClient.shared,
        familyStore: FamilyStoreProtocol = FirestoreFamilyStore.shared,
        draftRepository: OnboardingDraftRepository = .shared,
        sessionRepository: ParentSessionRepository = .shared,
        networkMonitor: NetworkMonitorProtocol = NetworkMonitor.shared
    ) {
        self.authClient = authClient
        self.familyStore = familyStore
        self.draftRepository = draftRepository
        self.sessionRepository = sessionRepository
        self.networkMonitor = networkMonitor
    }

    /// Sends a 6-digit verification code to the specified email address.
    public func sendEmailOtp(email: String) async -> Outcome<Void> {
        guard networkMonitor.isCurrentlyOnline else {
            return .failure(.network("Internet connection required to send verification code."))
        }
        if let error = CredentialsValidator.emailError(email) {
            return .failure(.validation(error))
        }

        do {
            try await authClient.sendEmailOtp(email: CredentialsValidator.normalizeEmail(email))
            return .success(())
        } catch {
            return .failure(AppErrorMapper.map(error))
        }
    }

    /// Verifies the 6-digit OTP code, completes Firebase sign-in, and reconciles/commits family data.
    public func verifyEmailOtp(email: String, code: String) async -> Outcome<ParentAuthResult> {
        guard networkMonitor.isCurrentlyOnline else {
            return .failure(.network("Internet connection required to verify code."))
        }
        if let error = CredentialsValidator.otpError(code) {
            return .failure(.validation(error))
        }

        do {
            let normalizedEmail = CredentialsValidator.normalizeEmail(email)
            print("[CompleteParentAuthUseCase] Starting verifyEmailOtp for \(normalizedEmail)...")
            let user = try await authClient.verifyEmailOtp(email: normalizedEmail, code: code)
            print("[CompleteParentAuthUseCase] authClient.verifyEmailOtp succeeded for user uid: \(user.uid). Finishing session...")
            let result = try await finishAuthenticatedSession(user: user)
            print("[CompleteParentAuthUseCase] finishAuthenticatedSession succeeded! Result: \(result)")
            return .success(result)
        } catch {
            let nsErr = error as NSError
            print("[CompleteParentAuthUseCase] verifyEmailOtp failed: domain=\(nsErr.domain), code=\(nsErr.code), desc=\(nsErr.localizedDescription), userInfo=\(nsErr.userInfo)")
            return .failure(AppErrorMapper.map(error))
        }
    }

    /// Completes sign-in with Sign in with Apple or custom auth token.
    public func completeSignIn(user: AuthUser) async -> Outcome<ParentAuthResult> {
        guard networkMonitor.isCurrentlyOnline else {
            return .failure(.network("Internet connection required to sign in."))
        }

        do {
            let result = try await finishAuthenticatedSession(user: user)
            return .success(result)
        } catch {
            return .failure(AppErrorMapper.map(error))
        }
    }

    /// Reconciles existing session on app cold-start without requesting credentials again.
    public func resumeIfAlreadySignedIn() async -> Outcome<ParentAuthResult>? {
        guard let user = authClient.currentUser else {
            return nil
        }
        do {
            let result = try await finishAuthenticatedSession(user: user)
            return .success(result)
        } catch {
            return .failure(AppErrorMapper.map(error))
        }
    }

    private func finishAuthenticatedSession(user: AuthUser) async throws -> ParentAuthResult {
        // 1. Check if user already belongs to an existing family
        if let familyId = try? await familyStore.getUserFamilyId(uid: user.uid), !familyId.isEmpty {
            do {
                let children = try await familyStore.listChildren(familyId: familyId)
                let draft = draftRepository.current()

                if let pinHash = draft.parentPinHash {
                    try? await familyStore.updateParentPinHash(familyId: familyId, pinHash: pinHash)
                }

                let session = ParentSession(
                    uid: user.uid,
                    familyId: familyId,
                    childIds: children.map { $0.childId }
                )
                sessionRepository.set(session)
                draftRepository.clear()

                return ParentAuthResult(
                    familyId: familyId,
                    childIds: session.childIds,
                    isNewFamily: false,
                    needsOnboarding: children.isEmpty
                )
            } catch {
                print("[CompleteParentAuthUseCase] Warning: failed to list children for family '\(familyId)': \(error.localizedDescription)")
                // Continue to check draft below if family is empty or inaccessible
            }
        }

        // 2. No existing family: check if local draft is ready to commit
        let draft = draftRepository.current()
        guard draft.isReadyToCommit else {
            return ParentAuthResult(
                familyId: nil,
                childIds: [],
                isNewFamily: false,
                needsOnboarding: true
            )
        }

        guard let pinHash = draft.parentPinHash else {
            throw AppError.validation("Set a Parent PIN before creating the family.")
        }

        let familyDraft = FamilyDraft(
            familyName: draft.familyName.isEmpty ? "My Family" : draft.familyName,
            parentPinHash: pinHash,
            children: draft.children.map { child in
                FamilyDraftChild(
                    localId: child.localId,
                    name: child.name,
                    ageBand: child.ageBand,
                    avatar: child.avatar,
                    language: child.language.isEmpty ? "en" : child.language
                )
            }
        )

        let created = try await familyStore.createFamilyFromDraft(
            uid: user.uid,
            email: user.email,
            draft: familyDraft
        )

        let session = ParentSession(
            uid: user.uid,
            familyId: created.familyId,
            childIds: created.children.map { $0.childId }
        )
        sessionRepository.set(session)
        draftRepository.clear()

        return ParentAuthResult(
            familyId: created.familyId,
            childIds: session.childIds,
            isNewFamily: true,
            needsOnboarding: false
        )
    }
}
