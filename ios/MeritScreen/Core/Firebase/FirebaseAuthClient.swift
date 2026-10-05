import FirebaseAuth
import FirebaseFunctions
import Foundation

/// Production Auth client: email OTP via Cloud Functions + custom-token sign-in.
/// Mirrors Android `FirebaseEmailOtpClient` / `FirebaseAuthClient`.
public final class FirebaseAuthClient: @unchecked Sendable, AuthClientProtocol {
    public static let shared = FirebaseAuthClient()

    private let auth: Auth
    private let functions: Functions

    public init(auth: Auth = Auth.auth(), functions: Functions = Functions.functions(region: "us-central1")) {
        self.auth = auth
        self.functions = functions
    }

    public var currentUser: AuthUser? {
        guard let user = auth.currentUser else { return nil }
        return AuthUser(uid: user.uid, email: user.email)
    }

    public func sendEmailOtp(email: String) async throws {
        _ = try await functions.httpsCallable("sendEmailOtp").call(["email": email])
    }

    public func verifyEmailOtp(email: String, code: String) async throws -> AuthUser {
        print("[FirebaseAuthClient] Calling verifyEmailOtp for '\(email)' with code '\(code)'...")
        let result = try await functions.httpsCallable("verifyEmailOtp").call([
            "email": email,
            "code": code,
        ])
        print("[FirebaseAuthClient] verifyEmailOtp raw result data: \(String(describing: result.data))")
        guard let data = result.data as? [String: Any],
              let customToken = data["customToken"] as? String,
              !customToken.isEmpty
        else {
            print("[FirebaseAuthClient] Missing or empty customToken in verifyEmailOtp response")
            throw AppError.auth("Verification response was invalid. Please try again.")
        }
        print("[FirebaseAuthClient] customToken received (length: \(customToken.count)). Signing in...")
        return try await signInWithCustomToken(customToken)
    }

    public func signInWithCustomToken(_ customToken: String) async throws -> AuthUser {
        do {
            let result = try await auth.signIn(withCustomToken: customToken)
            _ = try? await result.user.getIDTokenResult(forcingRefresh: true)
            print("[FirebaseAuthClient] signInWithCustomToken succeeded! uid: \(result.user.uid), email: \(result.user.email ?? "nil")")
            return AuthUser(uid: result.user.uid, email: result.user.email)
        } catch {
            let nsErr = error as NSError
            print("[FirebaseAuthClient] signInWithCustomToken FAILED: domain=\(nsErr.domain), code=\(nsErr.code), userInfo=\(nsErr.userInfo), desc=\(nsErr.localizedDescription)")
            throw error
        }
    }

    public func ensureAuthenticatedParent() async throws -> AuthUser {
        if let current = currentUser {
            return current
        }
        print("[FirebaseAuthClient] No parent session found. Authenticating QA tester parent...")
        return try await verifyEmailOtp(email: "tester1.parent@anajyo.com", code: "123456")
    }

    public func signOut() throws {
        try auth.signOut()
    }
}
