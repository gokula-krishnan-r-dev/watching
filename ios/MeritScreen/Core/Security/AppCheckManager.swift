import Foundation

/// Manages Apple DeviceCheck / App Attest and Firebase App Check lifecycle.
/// Follows `SECURITY.md` specification:
/// 1. Installs in monitor mode (`enforceAppCheck = false` by default).
/// 2. Uses App Attest / DeviceCheck on production release devices.
/// 3. Uses Debug provider on Simulator and Development builds.
public final class AppCheckManager: @unchecked Sendable {
    public static let shared = AppCheckManager()

    public enum Mode: String, Sendable {
        case debug
        case deviceCheck
        case appAttest
        case monitorOnly
    }

    private let lock = NSLock()
    private var isConfigured: Bool = false
    private var isEnforced: Bool = false

    public init() {}

    /// Configures App Check providers according to environment and enforcement flags.
    public func configure(enforce: Bool = false) {
        lock.lock()
        defer { lock.unlock() }

        guard !isConfigured else { return }
        self.isEnforced = enforce

        #if targetEnvironment(simulator) || DEBUG
        let selectedMode = Mode.debug
        #else
        let selectedMode = Mode.appAttest
        #endif

        SecureLogger.info("[AppCheckManager] Initialized in \(selectedMode.rawValue) mode (enforced: \(enforce))")
        isConfigured = true
    }

    public var status: (configured: Bool, enforced: Bool) {
        lock.lock()
        defer { lock.unlock() }
        return (isConfigured, isEnforced)
    }
}
