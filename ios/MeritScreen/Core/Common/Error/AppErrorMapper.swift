import Foundation

/// Maps raw platform, network, and service errors into user-facing AppError instances.
public enum AppErrorMapper {
    public static func map(_ error: Error) -> AppError {
        if let appError = error as? AppError {
            return appError
        }

        let nsError = error as NSError

        // Network connection error domain
        if nsError.domain == NSURLErrorDomain {
            switch nsError.code {
            case NSURLErrorNotConnectedToInternet,
                 NSURLErrorNetworkConnectionLost,
                 NSURLErrorTimedOut:
                return .network()
            default:
                break
            }
        }

        // Firebase Auth & Cloud Functions codes
        if nsError.domain == "FIRAuthErrorDomain" {
            let message = nsError.localizedDescription
            switch nsError.code {
            case 17007: // Email already in use
                return .validation(userMessage: "An account already exists with this email address.")
            case 17008: // Invalid email
                return .validation(userMessage: "Please enter a valid email address.")
            case 17009: // Wrong password
                return .validation(userMessage: "Incorrect password. Please try again.")
            case 17011: // User not found
                return .notFound(userMessage: "No account found with this email.")
            case 17020: // Network error
                return .network()
            case 17000: // Invalid custom token
                return .auth(userMessage: "The custom token format is invalid (\(nsError.code)).")
            case 17002: // Custom token mismatch
                return .auth(userMessage: "Custom token audience mismatch (\(nsError.code)).")
            case 17004: // Invalid credential
                return .auth(userMessage: "Invalid credential (\(nsError.code)).")
            case 17006: // Operation not allowed
                return .auth(userMessage: "Custom token sign-in is disabled in Firebase console (\(nsError.code)).")
            case 17010: // Too many requests
                return .validation(userMessage: "Too many attempts. Please try again later.")
            default:
                if !message.isEmpty {
                    return .auth(userMessage: "\(message) (Code: \(nsError.code))")
                }
                return .auth(userMessage: "Authentication failed (Code: \(nsError.code)).")
            }
        }

        // Firestore errors
        if nsError.domain == "FIRFirestoreErrorDomain" {
            let message = nsError.localizedDescription
            switch nsError.code {
            case 7: // PERMISSION_DENIED
                return .permission(userMessage: message.isEmpty ? "Permission denied accessing database." : message)
            case 14: // UNAVAILABLE
                return .network(userMessage: "Database unavailable. Please check your connection.")
            default:
                return .unknown(userMessage: message.isEmpty ? "Database error (\(nsError.code))." : message)
            }
        }

        // Cloud Functions callable errors (com.firebase.functions)
        if nsError.domain == "com.firebase.functions" {
            let message = nsError.localizedDescription
            switch nsError.code {
            case 7: // PERMISSION_DENIED
                return .permission(userMessage: message.isEmpty ? "You don't have permission for that." : message)
            case 8: // RESOURCE_EXHAUSTED / rate limit
                return .validation(userMessage: message.isEmpty ? "Too many attempts. Please wait and try again." : message)
            case 5: // NOT_FOUND
                return .notFound(userMessage: message.isEmpty ? "We couldn't find that." : message)
            case 9: // FAILED_PRECONDITION
                return .validation(userMessage: message.isEmpty ? "Please check and try again." : message)
            case 14: // UNAVAILABLE
                return .network()
            default:
                if !message.isEmpty { return .unknown(userMessage: message) }
                return .unknown()
            }
        }

        let desc = error.localizedDescription
        if desc.localizedCaseInsensitiveContains("network") || desc.localizedCaseInsensitiveContains("offline") {
            return .network()
        }

        return .unknown(userMessage: desc.isEmpty ? "Something went wrong. Please try again." : desc)
    }
}
