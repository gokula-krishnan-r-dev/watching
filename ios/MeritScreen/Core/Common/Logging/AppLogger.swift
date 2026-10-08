import Foundation
import os

/// Unified application logger with automated sanitization.
public final class AppLogger: Sendable {
    public static let shared = AppLogger()

    private let logger: Logger

    public init(subsystem: String = "com.watching.app", category: String = "App") {
        self.logger = Logger(subsystem: subsystem, category: category)
    }

    public func debug(_ message: String) {
        logger.debug("\(LogSanitizer.sanitize(message), privacy: .public)")
    }

    public func info(_ message: String) {
        logger.info("\(LogSanitizer.sanitize(message), privacy: .public)")
    }

    public func warning(_ message: String) {
        logger.warning("\(LogSanitizer.sanitize(message), privacy: .public)")
    }

    public func error(_ message: String) {
        logger.error("\(LogSanitizer.sanitize(message), privacy: .public)")
    }
}
