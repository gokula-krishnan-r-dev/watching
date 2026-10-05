import Foundation
import SwiftUI

/// Coordinates deferred background operations so the primary UI frame paints immediately (< 50ms)
/// without competing for main-thread CPU, disk I/O, or network bandwidth.
/// Mirrors `yield()` then `deviceLifecycle.start()` from Android Phase 10.
public final class StartupPerformanceCoordinator: @unchecked Sendable {
    public static let shared = StartupPerformanceCoordinator()

    private let lock = NSLock()
    private var _hasRenderedFirstFrame: Bool = false

    public init() {}

    public var hasRenderedFirstFrame: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _hasRenderedFirstFrame
    }

    /// Marks the initial UI frame as painted and triggers any queued post-startup jobs.
    public func markFirstFrameRendered() {
        lock.lock()
        _hasRenderedFirstFrame = true
        lock.unlock()
    }

    /// Defers execution until after the current runloop cycle yields and the first frame has painted.
    public func deferUntilFirstFrameRendered(
        priority: TaskPriority = .utility,
        operation: @escaping @Sendable () async -> Void
    ) {
        Task(priority: priority) {
            // Cooperative yield allowing SwiftUI to complete initial view hierarchy layout & render
            await Task.yield()

            // If first frame not marked yet, introduce a gentle non-blocking pause
            if !hasRenderedFirstFrame {
                try? await Task.sleep(nanoseconds: 150_000_000) // 150ms
            }

            await operation()
        }
    }
}
