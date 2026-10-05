import Foundation
import os

/// Unified secure logging wrapper that ensures all log outputs pass through `LogSanitizer`.
/// Strictly prevents accidental leaking of PINs, tokens, credentials, or child PII.
public enum SecureLogger {
    private static let logger = Logger(subsystem: "com.meritscreen.app", category: "App")

    public static func debug(_ message: String, category: String? = nil) {
        let sanitized = LogSanitizer.sanitize(message)
        if let category {
            Logger(subsystem: "com.meritscreen.app", category: category).debug("\(sanitized, privacy: .public)")
        } else {
            logger.debug("\(sanitized, privacy: .public)")
        }
    }

    public static func info(_ message: String, category: String? = nil) {
        let sanitized = LogSanitizer.sanitize(message)
        if let category {
            Logger(subsystem: "com.meritscreen.app", category: category).info("\(sanitized, privacy: .public)")
        } else {
            logger.info("\(sanitized, privacy: .public)")
        }
    }

    public static func warning(_ message: String, category: String? = nil) {
        let sanitized = LogSanitizer.sanitize(message)
        if let category {
            Logger(subsystem: "com.meritscreen.app", category: category).warning("\(sanitized, privacy: .public)")
        } else {
            logger.warning("\(sanitized, privacy: .public)")
        }
    }

    public static func error(_ message: String, category: String? = nil) {
        let sanitized = LogSanitizer.sanitize(message)
        if let category {
            Logger(subsystem: "com.meritscreen.app", category: category).error("\(sanitized, privacy: .public)")
        } else {
            logger.error("\(sanitized, privacy: .public)")
        }
    }
}
