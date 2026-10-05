import CommonCrypto
import Foundation

public protocol PinHasherProtocol: Sendable {
    func hash(pin: String) -> String
    func verify(pin: String, storedHash: String) -> Bool
}

/// Pure Swift PBKDF2-HMAC-SHA256 hasher.
/// 100% cross-platform compatible with Android's `com.meritscreen.core.security.pin.Pbkdf2PinHasher`.
/// Format: `pbkdf2$120000$<salt_base64_unpadded>$<hash_base64_unpadded>`
public final class Pbkdf2PinHasher: PinHasherProtocol {
    public static let shared = Pbkdf2PinHasher()

    private let prefix = "pbkdf2"
    private let separator = "$"
    private let iterations: UInt32 = 120_000
    private let saltBytesCount = 16
    private let keyBytesCount = 32 // 256 bits

    public init() {}

    public func hash(pin: String) -> String {
        var salt = [UInt8](repeating: 0, count: saltBytesCount)
        _ = SecRandomCopyBytes(kSecRandomDefault, saltBytesCount, &salt)

        let derived = derive(pin: pin, salt: salt, iterations: iterations)
        let saltBase64 = encodeBase64NoPadding(Data(salt))
        let hashBase64 = encodeBase64NoPadding(derived)

        return "\(prefix)\(separator)\(iterations)\(separator)\(saltBase64)\(separator)\(hashBase64)"
    }

    public func verify(pin: String, storedHash: String) -> Bool {
        let parts = storedHash.components(separatedBy: separator)
        guard parts.count == 4, parts[0] == prefix else { return false }
        guard let iter = UInt32(parts[1]),
              let salt = decodeBase64NoPadding(parts[2]),
              let expectedHash = decodeBase64NoPadding(parts[3]) else {
            return false
        }

        let actualHash = derive(pin: pin, salt: [UInt8](salt), iterations: iter)
        return constantTimeEquals(expectedHash, actualHash)
    }

    private func derive(pin: String, salt: [UInt8], iterations: UInt32) -> Data {
        var derivedKey = [UInt8](repeating: 0, count: keyBytesCount)
        let pinData = pin.data(using: .utf8) ?? Data()

        pinData.withUnsafeBytes { pinBytes in
            _ = CCKeyDerivationPBKDF(
                CCPBKDFAlgorithm(kCCPBKDF2),
                pinBytes.bindMemory(to: Int8.self).baseAddress,
                pinData.count,
                salt,
                salt.count,
                CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                iterations,
                &derivedKey,
                keyBytesCount
            )
        }

        return Data(derivedKey)
    }

    private func encodeBase64NoPadding(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "=", with: "")
    }

    private func decodeBase64NoPadding(_ string: String) -> Data? {
        var base64 = string
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: base64)
    }

    private func constantTimeEquals(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var result: UInt8 = 0
        for i in 0..<a.count {
            result |= a[i] ^ b[i]
        }
        return result == 0
    }
}
