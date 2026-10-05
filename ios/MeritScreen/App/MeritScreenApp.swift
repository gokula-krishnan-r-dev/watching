import SwiftData
import SwiftUI

@main
struct MeritScreenApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var appearanceStore = AppearanceStore.shared
    private let database = MeritScreenDatabase.shared

    init() {
        FirebaseBootstrap.configureIfNeeded()
    }

    var body: some Scene {
        WindowGroup {
            RoleGateView()
                .preferredColorScheme(appearanceStore.colorScheme)
                .environment(appearanceStore)
                .modelContainer(database.container)
        }
    }
}
