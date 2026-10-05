import FirebaseCore
import Foundation

/// Configures Firebase once at process start. Call from `MeritScreenApp.init`.
enum FirebaseBootstrap {
    static func configureIfNeeded() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
    }
}
