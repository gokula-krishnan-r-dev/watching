import Foundation
import Network
import Observation

public protocol NetworkMonitorProtocol: Sendable {
    var isConnected: Bool { get }
    var isCurrentlyOnline: Bool { get }
}

public extension NetworkMonitorProtocol {
    var isCurrentlyOnline: Bool { isConnected }
}

public extension Notification.Name {
    static let networkStatusChanged = Notification.Name("com.watching.network_status_changed")
}

/// Real-time network reachability monitor using Apple's Network framework (NWPathMonitor).
@Observable
public final class NetworkMonitor: NetworkMonitorProtocol, @unchecked Sendable {
    public static let shared = NetworkMonitor()

    public private(set) var isConnected: Bool = true

    private let monitor: NWPathMonitor
    private let queue = DispatchQueue(label: "com.watching.networkmonitor", qos: .utility)

    public init() {
        self.monitor = NWPathMonitor()
        self.monitor.pathUpdateHandler = { [weak self] path in
            let online = (path.status == .satisfied)
            Task { @MainActor in
                self?.isConnected = online
                NotificationCenter.default.post(
                    name: .networkStatusChanged,
                    object: nil,
                    userInfo: ["isOnline": online]
                )
            }
        }
        self.monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }
}
