import Foundation
import Combine

/// Reacts to network connectivity recovery by triggering expedited synchronization:
/// - Pulls fresh policy and rules
/// - Flushes dirty screen time minutes and pending quiz attempts
/// - Dispatches device heartbeat
public final class SyncRecoveryCoordinator: @unchecked Sendable {
    public static let shared = SyncRecoveryCoordinator()

    private let networkMonitor: NetworkMonitorProtocol
    private let policyPullCoordinator: ChildPolicyPullCoordinator
    private let usageCoordinator: UsageSyncCoordinator
    private let heartbeatCoordinator: DeviceHeartbeatCoordinator

    private var cancellables = Set<AnyCancellable>()
    private var lastRecoveryEpochMs: Int64 = 0
    private let cooldownIntervalMs: Int64 = 15_000 // 15s throttle between reconnect bursts

    public init(
        networkMonitor: NetworkMonitorProtocol = NetworkMonitor.shared,
        policyPullCoordinator: ChildPolicyPullCoordinator = .shared,
        usageCoordinator: UsageSyncCoordinator = .shared,
        heartbeatCoordinator: DeviceHeartbeatCoordinator = .shared
    ) {
        self.networkMonitor = networkMonitor
        self.policyPullCoordinator = policyPullCoordinator
        self.usageCoordinator = usageCoordinator
        self.heartbeatCoordinator = heartbeatCoordinator
    }

    public func startObserving() {
        NotificationCenter.default.publisher(for: .networkStatusChanged)
            .compactMap { $0.userInfo?["isOnline"] as? Bool }
            .removeDuplicates()
            .filter { $0 }
            .sink { [weak self] _ in
                self?.handleReconnection()
            }
            .store(in: &cancellables)
    }

    public func stopObserving() {
        cancellables.removeAll()
    }

    public func handleReconnection() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard now - lastRecoveryEpochMs >= cooldownIntervalMs else { return }
        lastRecoveryEpochMs = now

        print("[SyncRecoveryCoordinator] Network reconnected. Executing expedited synchronization.")
        Task {
            async let policySync = policyPullCoordinator.pullLatestPolicy()
            async let usageSync = usageCoordinator.syncUsageAndAttempts()
            async let heartbeat = heartbeatCoordinator.performHeartbeat()

            _ = await (policySync, usageSync, heartbeat)
        }
    }
}
