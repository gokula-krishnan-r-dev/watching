import Foundation

/// Coordinates pulling latest family policy and app rules from the backend into `ChildLocalStore`.
///
/// Invariant: Writes ONLY into local storage. Child Hub and Quiz screens read strictly
/// from local store. A network failure never halts or crashes the child experience.
public final class ChildPolicyPullCoordinator: Sendable {
    public static let shared = ChildPolicyPullCoordinator()

    private let localStore: ChildLocalStore
    private let pairingStore: ChildPairingStore
    private let controlStore: ParentControlStoreProtocol

    public init(
        localStore: ChildLocalStore = .shared,
        pairingStore: ChildPairingStore = .shared,
        controlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared
    ) {
        self.localStore = localStore
        self.pairingStore = pairingStore
        self.controlStore = controlStore
    }

    /// Pulls latest policy and rules for the paired child device.
    /// Returns true on successful pull and persist, or false if offline/failed.
    @discardableResult
    public func pullLatestPolicy() async -> Bool {
        guard let credential = pairingStore.current() else {
            return false
        }

        do {
            async let policyFetch = controlStore.getPolicy(
                familyId: credential.familyId,
                childId: credential.childId
            )
            async let rulesFetch = controlStore.listAppRules(
                familyId: credential.familyId,
                childId: credential.childId
            )

            let (policy, rules) = try await (policyFetch, rulesFetch)

            localStore.saveCachedPolicy(policy)
            if !rules.isEmpty {
                localStore.saveCachedAppRules(rules)
            }

            // Sync policy values into shared App Group store so DeviceActivity extension
            // can read correct block/cooldown minutes without a network call.
            let sharedStore = ScreenTimeSharedStore.shared
            sharedStore.saveBlockMinutes(policy.defaultBlockMinutes > 0 ? policy.defaultBlockMinutes : 30)
            sharedStore.saveCooldownMinutes(policy.defaultCooldownMinutes > 0 ? policy.defaultCooldownMinutes : 15)
            sharedStore.saveChildName(credential.displayName)

            print("[ChildPolicyPullCoordinator] Policy synced: block=\(policy.defaultBlockMinutes)min, cooldown=\(policy.defaultCooldownMinutes)min")
            return true
        } catch {
            print("[ChildPolicyPullCoordinator] Offline or failed to pull policy: \(error.localizedDescription)")
            return false
        }
    }
}
