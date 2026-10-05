import Foundation
import BackgroundTasks

/// Manages background task registration and scheduling via Apple's official `BGTaskScheduler`.
/// Provides reliable fallback synchronization when FCM push is delayed or in low power states.
public final class BGTaskCoordinator: @unchecked Sendable {
    public static let shared = BGTaskCoordinator()

    public static let heartbeatTaskId = "com.meritscreen.child.heartbeat"
    public static let policySyncTaskId = "com.meritscreen.child.policy_sync"
    public static let usageUploadTaskId = "com.meritscreen.child.usage_upload"

    private let heartbeatCoordinator: DeviceHeartbeatCoordinator
    private let policyPullCoordinator: ChildPolicyPullCoordinator
    private let usageCoordinator: UsageSyncCoordinator

    public init(
        heartbeatCoordinator: DeviceHeartbeatCoordinator = .shared,
        policyPullCoordinator: ChildPolicyPullCoordinator = .shared,
        usageCoordinator: UsageSyncCoordinator = .shared
    ) {
        self.heartbeatCoordinator = heartbeatCoordinator
        self.policyPullCoordinator = policyPullCoordinator
        self.usageCoordinator = usageCoordinator
    }

    /// Registers background task handlers. Must be called in `application(_:didFinishLaunchingWithOptions:)`.
    public func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.heartbeatTaskId,
            using: nil
        ) { task in
            guard let appRefreshTask = task as? BGAppRefreshTask else { return }
            self.handleHeartbeatTask(appRefreshTask)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.policySyncTaskId,
            using: nil
        ) { task in
            guard let appRefreshTask = task as? BGAppRefreshTask else { return }
            self.handlePolicySyncTask(appRefreshTask)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.usageUploadTaskId,
            using: nil
        ) { task in
            guard let appRefreshTask = task as? BGAppRefreshTask else { return }
            self.handleUsageUploadTask(appRefreshTask)
        }

        print("[BGTaskCoordinator] Registered all background task handlers.")
    }

    // MARK: - Task Scheduling

    /// Schedules the next heartbeat task (~30 minutes).
    public func scheduleNextHeartbeat(delaySeconds: TimeInterval = 1800) {
        let request = BGAppRefreshTaskRequest(identifier: Self.heartbeatTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: delaySeconds)

        do {
            try BGTaskScheduler.shared.submit(request)
            print("[BGTaskCoordinator] Scheduled next heartbeat in \(Int(delaySeconds))s")
        } catch {
            print("[BGTaskCoordinator] Could not schedule heartbeat: \(error.localizedDescription)")
        }
    }

    /// Schedules the next policy sync task (~6 hours).
    public func scheduleNextPolicySync(delaySeconds: TimeInterval = 21600) {
        let request = BGAppRefreshTaskRequest(identifier: Self.policySyncTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: delaySeconds)

        do {
            try BGTaskScheduler.shared.submit(request)
            print("[BGTaskCoordinator] Scheduled next policy sync in \(Int(delaySeconds))s")
        } catch {
            print("[BGTaskCoordinator] Could not schedule policy sync: \(error.localizedDescription)")
        }
    }

    /// Schedules the next usage upload task (~2 hours).
    public func scheduleNextUsageUpload(delaySeconds: TimeInterval = 7200) {
        let request = BGAppRefreshTaskRequest(identifier: Self.usageUploadTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: delaySeconds)

        do {
            try BGTaskScheduler.shared.submit(request)
            print("[BGTaskCoordinator] Scheduled next usage upload in \(Int(delaySeconds))s")
        } catch {
            print("[BGTaskCoordinator] Could not schedule usage upload: \(error.localizedDescription)")
        }
    }

    // MARK: - Task Handlers

    private func handleHeartbeatTask(_ task: BGAppRefreshTask) {
        scheduleNextHeartbeat()
        let box = TaskBox(task)

        let taskOperation = Task {
            let success = await heartbeatCoordinator.performHeartbeat()
            box.task.setTaskCompleted(success: success)
        }

        task.expirationHandler = {
            taskOperation.cancel()
        }
    }

    private func handlePolicySyncTask(_ task: BGAppRefreshTask) {
        scheduleNextPolicySync()
        let box = TaskBox(task)

        let taskOperation = Task {
            let success = await policyPullCoordinator.pullLatestPolicy()
            box.task.setTaskCompleted(success: success)
        }

        task.expirationHandler = {
            taskOperation.cancel()
        }
    }

    private func handleUsageUploadTask(_ task: BGAppRefreshTask) {
        scheduleNextUsageUpload()
        let box = TaskBox(task)

        let taskOperation = Task {
            let success = await usageCoordinator.syncUsageAndAttempts()
            box.task.setTaskCompleted(success: success)
        }

        task.expirationHandler = {
            taskOperation.cancel()
        }
    }
}

private final class TaskBox: @unchecked Sendable {
    let task: BGAppRefreshTask
    init(_ task: BGAppRefreshTask) {
        self.task = task
    }
}
