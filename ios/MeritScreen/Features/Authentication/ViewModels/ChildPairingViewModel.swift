import Foundation
import Observation

@MainActor
@Observable
public final class ChildPairingViewModel {
    public var code: String = ""
    public var secret: String?
    public var isSubmitting: Bool = false
    public var isScanning: Bool = true
    public var pairedCredential: ChildPairingCredential?
    public var error: AppError?

    private let pairChildDeviceUseCase: PairChildDeviceUseCase

    public init(pairChildDeviceUseCase: PairChildDeviceUseCase? = nil) {
        self.pairChildDeviceUseCase = pairChildDeviceUseCase ?? PairChildDeviceUseCase()
    }

    @MainActor
    public func onCodeChanged(_ newCode: String, onPaired: @escaping (ChildPairingCredential) -> Void) async {
        let digits = String(newCode.filter { $0.isNumber }.prefix(AppConfig.pairingCodeLength))
        self.code = digits
        self.error = nil

        // If user is editing code manually, drop any stale QR secret
        self.secret = nil

        if digits.count == AppConfig.pairingCodeLength && !isSubmitting {
            await submit(onPaired: onPaired)
        }
    }

    @MainActor
    public func onQrScanned(_ parsed: ParsedPairingQr, onPaired: @escaping (ChildPairingCredential) -> Void) async {
        guard !isSubmitting, pairedCredential == nil else { return }
        self.code = parsed.code
        self.secret = parsed.secret
        self.isScanning = false
        self.error = nil

        await submit(onPaired: onPaired)
    }

    @MainActor
    public func submit(onPaired: @escaping (ChildPairingCredential) -> Void) async {
        guard !isSubmitting, pairedCredential == nil else { return }
        error = nil

        let digits = code.filter { $0.isNumber }
        if digits.count != AppConfig.pairingCodeLength {
            error = .validation("Enter the \(AppConfig.pairingCodeLength)-digit code from the parent phone.")
            return
        }

        isSubmitting = true
        let outcome = await pairChildDeviceUseCase.execute(code: digits, secret: secret)
        isSubmitting = false

        switch outcome {
        case .success(let credential):
            self.pairedCredential = credential
            onPaired(credential)
        case .failure(let appError):
            self.error = appError
        }
    }
}
