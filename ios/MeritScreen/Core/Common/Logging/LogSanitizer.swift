import Foundation

/// Sanitizes log payloads to prevent accidental leaking of PINs, tokens, credentials, or child PII.
/// Mirrors `com.meritscreen.core.common.logging.LogSanitizer`.
public enum LogSanitizer {
    private static let sensitiveKeys: Set<String> = [
        "email", "password", "token", "idtoken", "pin", "secret", "authorization",
        "pairing", "refresh", "apikey", "customtoken", "pairingcode", "pinhash",
        "fcmtoken", "parentpin"
    ]

    public static func sanitize(_ value: Any?) -> String {
        guard let value = value else { return "nil" }
        let raw = String(describing: value)
        if looksSensitive(raw) {
            return "<redacted>"
        }
        return String(raw.prefix(120))
    }

    public static func sanitize(key: String, value: Any?) -> String {
        let lowerKey = key.lowercased()
        if sensitiveKeys.contains(where: { lowerKey.contains($0) }) {
            return "<redacted>"
        }
        return sanitize(value)
    }

    private static func looksSensitive(_ raw: String) -> Bool {
        // Email pattern
        if raw.contains("@") && raw.contains(".") { return true }
        // JWT tokens
        if raw.hasPrefix("eyJ") { return true }
        // Pairing deep links
        if raw.hasPrefix("meritscreen://pair") { return true }
        // Excessively long opaque strings (hashes/keys)
        if raw.count > 80 { return true }
        return false
    }
}
