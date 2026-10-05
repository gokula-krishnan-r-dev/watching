import UIKit
import UserNotifications
@preconcurrency import FirebaseMessaging

/// Custom application delegate managing APNs registration, Firebase Messaging delegation,
/// remote notification routing, and background tasks.
@MainActor
public final class AppDelegate: NSObject, UIApplicationDelegate {

    public func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 0. Initialize App Check in monitoring mode
        AppCheckManager.shared.configure(enforce: false)

        // 1. Register background task handlers with BGTaskScheduler
        BGTaskCoordinator.shared.registerBackgroundTasks()

        // 2. Set up push notification & messaging delegates
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        // 3. Request push authorization
        requestPushAuthorization(application: application)

        // 4. Start network recovery observer
        SyncRecoveryCoordinator.shared.startObserving()

        return true
    }

    // MARK: - Push Authorization & APNs

    private func requestPushAuthorization(application: UIApplication) {
        if ProcessInfo.processInfo.arguments.contains("-noNotificationPrompt") ||
           ProcessInfo.processInfo.arguments.contains("-testParentOnboarding") ||
           ProcessInfo.processInfo.arguments.contains("-testChildPairing") ||
           ProcessInfo.processInfo.arguments.contains("-testParentSignIn") ||
           ProcessInfo.processInfo.arguments.contains("-testRoleSelect") {
            return
        }
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, error in
            if let error = error {
                print("[AppDelegate] Notification authorization error: \(error.localizedDescription)")
                return
            }
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
    }

    public func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        print("[AppDelegate] Registered for remote notifications with APNs token.")
        Messaging.messaging().apnsToken = deviceToken
    }

    public func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[AppDelegate] Failed to register for remote notifications: \(error.localizedDescription)")
    }

    // MARK: - Remote Notification Handling

    public func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        let message = PushMessage.parse(from: userInfo)
        Task {
            let result = await PushNotificationRouter.shared.routePushMessage(message)
            completionHandler(result)
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension AppDelegate: UNUserNotificationCenterDelegate {
    nonisolated public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Allow banners and sounds even when app is foregrounded
        completionHandler([.banner, .badge, .sound])
    }

    nonisolated public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let message = PushMessage.parse(from: response.notification.request.content.userInfo)
        Task {
            _ = await PushNotificationRouter.shared.routePushMessage(message)
        }
        completionHandler()
    }
}

// MARK: - MessagingDelegate

extension AppDelegate: @preconcurrency MessagingDelegate {
    nonisolated public func messaging(
        _ messaging: Messaging,
        didReceiveRegistrationToken fcmToken: String?
    ) {
        guard let token = fcmToken, !token.isEmpty else { return }
        print("[AppDelegate] Received refreshed FCM token.")

        Task {
            _ = await DevicePushTokenRegistrar.shared.registerPushTokenIfNeeded(force: true)
            _ = await ParentPushTokenRegistrar.shared.registerParentTokenIfNeeded(force: true)
        }
    }
}
