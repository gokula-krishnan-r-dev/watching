import Foundation

/// Coordinates secure, complete wipe of local child data upon family deletion or device revocation.
/// Invariant (SECURITY.md): Wipes credentials, policy, snapshots, pending attempts, and active shields,
/// but strictly preserves the builtin offline quiz bank so the device is left clean and functional.
public final class ChildSessionWiper: @unchecked Sendable {
    public static let shared = ChildSessionWiper()

    private let pairingStore: ChildPairingStore
    private let localStore: ChildLocalStore
    private let sharedStore: ScreenTimeSharedStore
    private let pinGateStore: PinGateStore

    public init(
        pairingStore: ChildPairingStore = .shared,
        localStore: ChildLocalStore = .shared,
        sharedStore: ScreenTimeSharedStore = .shared,
        pinGateStore: PinGateStore = PinGateStore()
    ) {
        self.pairingStore = pairingStore
        self.localStore = localStore
        self.sharedStore = sharedStore
        self.pinGateStore = pinGateStore
    }

    /// Executes complete local wipe and unshields the device.
    @MainActor
    public func wipeAllChildData(
        pairingStore: ChildPairingStore? = nil,
        localStore: ChildLocalStore? = nil,
        sharedStore: ScreenTimeSharedStore? = nil
    ) {
        let pStore = pairingStore ?? self.pairingStore
        let lStore = localStore ?? self.localStore
        let sStore = sharedStore ?? self.sharedStore

        SecureLogger.info("[ChildSessionWiper] Initiating complete child local wipe...")

        // 1. Wipe credentials from Keychain
        pStore.clear()

        // 2. Wipe child local storage (policy, usage, snapshots, pending attempts, stickers)
        lStore.clearAll()

        // 3. Clear active shields and mark revoked in shared app-group store
        sStore.clearAll()
        sStore.setDeviceRevoked(true)

        // 4. Release all Screen Time managed settings shields
        ScreenTimeEnforcementController.shared.clearFailLock()

        // 5. Reset PIN lockout state
        pinGateStore.clear()

        // 6. Notify UI to reset role
        NotificationCenter.default.post(name: .childDeviceRevoked, object: nil)

        SecureLogger.info("[ChildSessionWiper] Child local wipe complete. Builtin quiz bank preserved.")
    }
}
