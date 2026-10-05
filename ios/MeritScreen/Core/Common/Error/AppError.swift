import Foundation

/// User-facing errors. Never carry raw Firebase or system error messages into the UI layer.
/// Mirrors `com.meritscreen.core.common.error.AppError` in the Android codebase.
public enum AppError: LocalizedError, Equatable, Sendable {
    case network(userMessage: String)
    case auth(userMessage: String)
    case permission(userMessage: String)
    case notFound(userMessage: String)
    case validation(userMessage: String)
    case pairing(userMessage: String)
    case pinLockout(remainingMinutes: Int)
    case offlinePolicy(userMessage: String)
    case unknown(userMessage: String)

    public var userMessage: String {
        switch self {
        case .network(let msg): return msg
        case .auth(let msg): return msg
        case .permission(let msg): return msg
        case .notFound(let msg): return msg
        case .validation(let msg): return msg
        case .pairing(let msg): return msg
        case .pinLockout(let mins):
            return "Too many incorrect attempts. Please wait \(mins) minute\(mins == 1 ? "" : "s") before trying again."
        case .offlinePolicy(let msg): return msg
        case .unknown(let msg): return msg
        }
    }

    public var errorDescription: String? {
        userMessage
    }

    public var isRetryable: Bool {
        switch self {
        case .network, .unknown, .pairing, .notFound:
            return true
        case .auth, .permission, .validation, .pinLockout, .offlinePolicy:
            return false
        }
    }

    public static func validation(_ message: String) -> AppError {
        .validation(userMessage: message)
    }

    public static func network(_ message: String = "Check your connection and try again.") -> AppError {
        .network(userMessage: message)
    }

    public static func auth(_ message: String = "Please sign in again to continue.") -> AppError {
        .auth(userMessage: message)
    }

    public static func pairing(_ message: String = "Could not pair device. Please check the code and try again.") -> AppError {
        .pairing(userMessage: message)
    }

    public static func permission(_ message: String = "MeritScreen needs permission to continue.") -> AppError {
        .permission(userMessage: message)
    }

    public static func notFound(_ message: String = "We couldn't find that information.") -> AppError {
        .notFound(userMessage: message)
    }

    public static func offlinePolicy(_ message: String = "Using saved settings until the device is back online.") -> AppError {
        .offlinePolicy(userMessage: message)
    }

    public static func unknown(_ message: String = "Something went wrong. Please try again.") -> AppError {
        .unknown(userMessage: message)
    }
}
