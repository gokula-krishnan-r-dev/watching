import Foundation

/// Validates email and OTP input credentials mirroring Android `CredentialsValidator`.
public enum CredentialsValidator {
    private static let emailRegex = try? NSRegularExpression(
        pattern: "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        options: .caseInsensitive
    )

    /// Returns a localized error string if email is invalid, or nil if valid.
    public static func emailError(_ email: String) -> String? {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "Enter your email address."
        }
        let range = NSRange(location: 0, length: trimmed.utf16.count)
        guard let regex = emailRegex, regex.firstMatch(in: trimmed, options: [], range: range) != nil else {
            return "Enter a valid email address."
        }
        return nil
    }

    /// Returns a localized error string if OTP code is invalid, or nil if valid.
    public static func otpError(_ otp: String) -> String? {
        let digits = otp.filter { $0.isNumber }
        if digits.count != AppConfig.emailOtpLength {
            return "Enter the \(AppConfig.emailOtpLength)-digit verification code."
        }
        return nil
    }

    /// Normalizes email to lowercase trimmed format.
    public static func normalizeEmail(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}
