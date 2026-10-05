import Observation
import SwiftUI

/// Observable store managing application-wide appearance preference.
/// Dynamically updates root `.preferredColorScheme` across all scenes without requiring app restart.
@Observable
public final class AppearanceStore: @unchecked Sendable {
    public static let shared = AppearanceStore()

    private let defaultsKey = "meritscreen_appearance_mode"
    private let defaults: UserDefaults

    public var mode: AppearanceMode {
        didSet {
            defaults.set(mode.rawValue, forKey: defaultsKey)
        }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let savedRaw = defaults.string(forKey: defaultsKey),
           let savedMode = AppearanceMode(rawValue: savedRaw) {
            self.mode = savedMode
        } else {
            self.mode = .system
        }
    }

    public var colorScheme: ColorScheme? {
        mode.colorScheme
    }
}
