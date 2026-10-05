import Foundation

/// Handles child device pairing via 6-digit code or QR payload.
/// Mirrors `com.meritscreen.feature.authentication.domain.PairChildDeviceUseCase` in Android.
public final class PairChildDeviceUseCase: @unchecked Sendable {
    private let pairingClient: PairingClientProtocol
    private let authClient: AuthClientProtocol
    private let pairingStore: ChildPairingStore
    private let networkMonitor: NetworkMonitorProtocol

    public init(
        pairingClient: PairingClientProtocol = FirebasePairingClient.shared,
        authClient: AuthClientProtocol = FirebaseAuthClient.shared,
        pairingStore: ChildPairingStore = .shared,
        networkMonitor: NetworkMonitorProtocol = NetworkMonitor.shared
    ) {
        self.pairingClient = pairingClient
        self.authClient = authClient
        self.pairingStore = pairingStore
        self.networkMonitor = networkMonitor
    }

    /// Consumes the pairing token using 6-digit code and optional QR secret.
    public func execute(code: String, secret: String? = nil) async -> Outcome<ChildPairingCredential> {
        let digits = code.filter { $0.isNumber }
        guard digits.count == AppConfig.pairingCodeLength else {
            return .failure(.validation("Enter the \(AppConfig.pairingCodeLength)-digit code from the parent phone."))
        }

        guard networkMonitor.isCurrentlyOnline else {
            return .failure(.network("Internet connection required to pair device."))
        }

        do {
            let deviceId = pairingStore.getOrCreateDeviceId()
            let result = try await pairingClient.consumePairingToken(
                code: digits,
                deviceId: deviceId,
                secret: secret
            )

            // Child custom token — scoped Auth session for Firestore/FCM.
            _ = try await authClient.signInWithCustomToken(result.customToken)

            let credential = ChildPairingCredential(
                familyId: result.familyId,
                childId: result.childId,
                deviceId: result.deviceId,
                parentPinHash: result.parentPinHash,
                displayName: result.displayName,
                ageBand: result.ageBand
            )

            pairingStore.set(credential)
            return .success(credential)
        } catch {
            return .failure(AppErrorMapper.map(error))
        }
    }
}
