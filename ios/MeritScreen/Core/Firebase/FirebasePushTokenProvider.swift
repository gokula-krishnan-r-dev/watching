import Foundation
@preconcurrency import FirebaseMessaging

/// Production FCM token provider wrapping Firebase Messaging.
public final class FirebasePushTokenProvider: @unchecked Sendable, PushTokenProviderProtocol {
    public static let shared = FirebasePushTokenProvider()

    public init() {}

    public func currentToken() async throws -> String? {
        do {
            return try await Messaging.messaging().token()
        } catch {
            print("[FirebasePushTokenProvider] Failed to fetch FCM token: \(error.localizedDescription)")
            return nil
        }
    }
}
