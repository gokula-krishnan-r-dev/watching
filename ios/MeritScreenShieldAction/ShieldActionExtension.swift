import Foundation
import ManagedSettings
import os

/// Handles user actions triggered from the system shield.
/// Clicking "Take Quiz" or "Retry Quiz Now" routes the child back to MeritScreen to attempt the quiz.
final class ShieldActionExtension: ShieldActionDelegate {
    private let logger = Logger(subsystem: "com.meritscreen.app.shieldaction", category: "Action")
    private let sharedStore = ScreenTimeSharedStore.shared

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        logger.info("ShieldAction handled for application: \(action == .primaryButtonPressed ? "primary" : "secondary")")

        switch action {
        case .primaryButtonPressed:
            let state = sharedStore.getShieldState()
            let isRetry = state.isFailLock
            sharedStore.setPendingQuizRequest(PendingQuizRequest(appName: "Apps", isRetry: isRetry))
            completionHandler(.close)
        case .secondaryButtonPressed:
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        completionHandler(.close)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        completionHandler(.close)
    }
}
