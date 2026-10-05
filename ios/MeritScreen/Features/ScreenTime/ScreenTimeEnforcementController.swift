import SwiftUI
import DeviceActivity
import ManagedSettings
import FamilyControls

public extension DeviceActivityName {
    static var childDailyActivity: DeviceActivityName {
        DeviceActivityName("com.meritscreen.child.activity")
    }
    static var childBedtimeActivity: DeviceActivityName {
        DeviceActivityName("com.meritscreen.child.bedtime")
    }
}

public extension DeviceActivityEvent.Name {
    static var blockThresholdEvent: DeviceActivityEvent.Name {
        DeviceActivityEvent.Name("com.meritscreen.child.block_threshold")
    }
}

/// Main actor controller managing native iOS Screen Time enforcement.
/// Uses `DeviceActivityCenter` for block timers and `ManagedSettingsStore` for system shields.
/// All shield state is persisted in `ScreenTimeSharedStore` (App Group) so extensions and
/// the main app share exactly one source of truth — and shields survive device reboots.
@Observable
@MainActor
public final class ScreenTimeEnforcementController {
    public static let shared = ScreenTimeEnforcementController()

    public private(set) var isMonitoring: Bool = false
    public private(set) var activeShieldState: ScreenTimeShieldState = .none

    private let deviceActivityCenter: DeviceActivityCenter
    private let managedSettingsStore: ManagedSettingsStore
    private let sharedStore: ScreenTimeSharedStore

    public init(
        deviceActivityCenter: DeviceActivityCenter = DeviceActivityCenter(),
        managedSettingsStore: ManagedSettingsStore = ManagedSettingsStore(),
        sharedStore: ScreenTimeSharedStore = .shared
    ) {
        self.deviceActivityCenter = deviceActivityCenter
        self.managedSettingsStore = managedSettingsStore
        self.sharedStore = sharedStore
        reconcileShieldsOnLaunch()
    }

    // MARK: - Monitoring Lifecycle

    /// Starts DeviceActivity monitoring for the given selection.
    /// Also persists policy values so the background extension can read them.
    ///
    /// - Parameters:
    ///   - selection: Apps/categories/domains to monitor. Must be non-empty.
    ///   - blockMinutes: Per-session block duration before quiz is required.
    ///   - cooldownMinutes: Fail-lock resting window after quiz failure.
    ///   - childName: Child's display name (shown in shield UI).
    ///   - bedtime: Optional bedtime hours to enforce a nightly lock.
    public func startMonitoring(
        selection: FamilyActivitySelection,
        blockMinutes: Int,
        cooldownMinutes: Int = 15,
        childName: String = "Child",
        bedtime: BedtimeWindow? = nil
    ) {
        // Persist policy so background extension can read it
        sharedStore.saveActivitySelection(selection)
        sharedStore.saveBlockMinutes(max(1, blockMinutes))
        sharedStore.saveCooldownMinutes(max(1, cooldownMinutes))
        sharedStore.saveChildName(childName)

        if let bt = bedtime {
            sharedStore.saveBedtimePolicy(
                enabled: bt.enabled,
                startHour: bt.startHour, startMin: bt.startMin,
                endHour: bt.endHour, endMin: bt.endMin
            )
        }

        guard !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty else {
            print("[ScreenTimeEnforcement] Selection is empty — stopping monitoring")
            stopMonitoring()
            return
        }

        // Daily schedule: midnight to 23:59:59
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59, second: 59),
            repeats: true
        )

        let safeMinutes = max(1, blockMinutes)
        let event = DeviceActivityEvent(
            applications: selection.applicationTokens,
            categories: selection.categoryTokens,
            webDomains: selection.webDomainTokens,
            threshold: DateComponents(minute: safeMinutes)
        )

        do {
            deviceActivityCenter.stopMonitoring([.childDailyActivity])
            try deviceActivityCenter.startMonitoring(
                .childDailyActivity,
                during: schedule,
                events: [.blockThresholdEvent: event]
            )
            self.isMonitoring = true
            print("[ScreenTimeEnforcement] Monitoring started: \(selection.applicationTokens.count) apps, \(safeMinutes)min block")
        } catch {
            print("[ScreenTimeEnforcement] Failed to start DeviceActivity monitoring: \(error.localizedDescription)")
            self.isMonitoring = false
        }

        // Apply bedtime shield immediately if currently in bedtime window
        if sharedStore.isCurrentlyBedtime() {
            applyBedtimeLock()
        }
    }

    public func stopMonitoring() {
        deviceActivityCenter.stopMonitoring([.childDailyActivity, .childBedtimeActivity])
        self.isMonitoring = false
        print("[ScreenTimeEnforcement] Monitoring stopped")
    }

    // MARK: - Shield Management

    /// Applies a device-wide fail lock across ALL non-emergency applications in the selection.
    /// Invariant: Fail lock is ALWAYS device-wide, never per-app-only.
    public func applyDeviceWideFailLock(cooldownMinutes: Int) {
        let selection = sharedStore.getActivitySelection()
        let emergencyTokens = sharedStore.getEmergencyTokens()

        let nonEmergencyTokens = selection.applicationTokens.subtracting(emergencyTokens)

        managedSettingsStore.shield.applications = nonEmergencyTokens.isEmpty ? nil : nonEmergencyTokens
        if !selection.categoryTokens.isEmpty {
            managedSettingsStore.shield.applicationCategories = .specific(selection.categoryTokens)
        }
        if !selection.webDomainTokens.isEmpty {
            managedSettingsStore.shield.webDomains = selection.webDomainTokens
        }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let deadline = now + Int64(max(1, cooldownMinutes) * 60_000)
        let newState = ScreenTimeShieldState.failLock(deadlineEpochMs: deadline, cooldownMinutes: cooldownMinutes)

        sharedStore.saveShieldState(newState)
        self.activeShieldState = newState
        print("[ScreenTimeEnforcement] Fail lock applied: \(cooldownMinutes)min cooldown")
    }

    /// Clears the fail lock shields.
    /// Called when cooldown finishes, retry quiz is passed, or parent PIN is entered.
    public func clearFailLock() {
        managedSettingsStore.shield.applications = nil
        managedSettingsStore.shield.applicationCategories = nil
        managedSettingsStore.shield.webDomains = nil

        sharedStore.saveShieldState(.none)
        self.activeShieldState = .none
        print("[ScreenTimeEnforcement] Fail lock cleared")
    }

    /// Applies an interim shield to all non-emergency apps when a block expires (quiz due).
    public func applyQuizDueInterimShield(
        for applicationToken: ApplicationToken,
        appName: String,
        blockMinutes: Int
    ) {
        managedSettingsStore.shield.applications = [applicationToken]

        let newState = ScreenTimeShieldState.quizDue(appName: appName, blockMinutes: blockMinutes)
        sharedStore.saveShieldState(newState)
        self.activeShieldState = newState
    }

    /// Clears any active interim shield after a quiz is passed.
    public func clearInterimShield() {
        managedSettingsStore.shield.applications = nil
        sharedStore.saveShieldState(.none)
        self.activeShieldState = .none
    }

    // MARK: - Bedtime Enforcement

    public func applyBedtimeLock() {
        let selection = sharedStore.getActivitySelection()
        let emergencyTokens = sharedStore.getEmergencyTokens()
        let nonEmergencyApps = selection.applicationTokens.subtracting(emergencyTokens)

        if !nonEmergencyApps.isEmpty {
            managedSettingsStore.shield.applications = nonEmergencyApps
        }
        if !selection.categoryTokens.isEmpty {
            managedSettingsStore.shield.applicationCategories = .specific(selection.categoryTokens)
        }

        let endLabel = sharedStore.bedtimeEndLabel()
        let newState = ScreenTimeShieldState.bedtimeLock(endLabel: endLabel)
        sharedStore.saveShieldState(newState)
        self.activeShieldState = newState
        print("[ScreenTimeEnforcement] Bedtime lock applied (ends \(endLabel))")
    }

    public func clearBedtimeLock() {
        managedSettingsStore.shield.applications = nil
        managedSettingsStore.shield.applicationCategories = nil
        sharedStore.saveShieldState(.none)
        self.activeShieldState = .none
    }

    // MARK: - Daily Limit Enforcement

    public func applyDailyLimitLock(minutesUsed: Int, ceilingMinutes: Int) {
        let selection = sharedStore.getActivitySelection()
        let emergencyTokens = sharedStore.getEmergencyTokens()
        let nonEmergencyApps = selection.applicationTokens.subtracting(emergencyTokens)

        if !nonEmergencyApps.isEmpty {
            managedSettingsStore.shield.applications = nonEmergencyApps
        }
        if !selection.categoryTokens.isEmpty {
            managedSettingsStore.shield.applicationCategories = .specific(selection.categoryTokens)
        } else {
            managedSettingsStore.shield.applicationCategories = .all()
        }
        if !selection.webDomainTokens.isEmpty {
            managedSettingsStore.shield.webDomains = selection.webDomainTokens
        }

        let newState = ScreenTimeShieldState.dailyLimitLock(minutesUsed: minutesUsed, ceilingMinutes: ceilingMinutes)
        sharedStore.saveShieldState(newState)
        self.activeShieldState = newState
        print("[ScreenTimeEnforcement] Daily limit lock applied (\(minutesUsed)/\(ceilingMinutes)m)")
    }

    public func clearDailyLimitLock() {
        managedSettingsStore.shield.applications = nil
        managedSettingsStore.shield.applicationCategories = nil
        managedSettingsStore.shield.webDomains = nil
        sharedStore.saveShieldState(.none)
        self.activeShieldState = .none
        print("[ScreenTimeEnforcement] Daily limit lock cleared")
    }

    // MARK: - Boot & Foreground Reconciliation

    /// Reconciles shield state on app launch/foreground to survive device restarts.
    /// Must be called on every `.active` scene phase change.
    public func reconcileShieldsOnLaunch() {
        let state = sharedStore.getShieldState()
        self.activeShieldState = state

        switch state {
        case .failLock(let deadline, _):
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            if now < deadline {
                // Cooldown still active — restore shields from selection
                let selection = sharedStore.getActivitySelection()
                let emergencyTokens = sharedStore.getEmergencyTokens()
                let shielded = selection.applicationTokens.subtracting(emergencyTokens)
                managedSettingsStore.shield.applications = shielded.isEmpty ? nil : shielded
                print("[ScreenTimeEnforcement] Restored fail-lock shield on launch")
            } else {
                // Cooldown expired while device was off — clear
                clearFailLock()
                print("[ScreenTimeEnforcement] Expired fail-lock cleared on launch")
            }

        case .bedtimeLock:
            // Check if we're still in the bedtime window
            if sharedStore.isCurrentlyBedtime() {
                let selection = sharedStore.getActivitySelection()
                let emergencyTokens = sharedStore.getEmergencyTokens()
                managedSettingsStore.shield.applications = selection.applicationTokens.subtracting(emergencyTokens)
            } else {
                // Bedtime window ended while device was rebooting
                clearBedtimeLock()
                print("[ScreenTimeEnforcement] Expired bedtime lock cleared on launch")
            }

        case .dailyLimitLock(let minutesUsed, let ceilingMinutes):
            let selection = sharedStore.getActivitySelection()
            let emergencyTokens = sharedStore.getEmergencyTokens()
            let shielded = selection.applicationTokens.subtracting(emergencyTokens)
            if !shielded.isEmpty {
                managedSettingsStore.shield.applications = shielded
            }
            if !selection.categoryTokens.isEmpty {
                managedSettingsStore.shield.applicationCategories = .specific(selection.categoryTokens)
            } else {
                managedSettingsStore.shield.applicationCategories = .all()
            }
            if !selection.webDomainTokens.isEmpty {
                managedSettingsStore.shield.webDomains = selection.webDomainTokens
            }
            print("[ScreenTimeEnforcement] Restored daily-limit lock shield on launch (\(minutesUsed)/\(ceilingMinutes)m)")

        case .quizDue:
            // Quiz-due shield was active — keep it (DeviceActivity extension set it)
            let selection = sharedStore.getActivitySelection()
            let emergencyTokens = sharedStore.getEmergencyTokens()
            managedSettingsStore.shield.applications = selection.applicationTokens.subtracting(emergencyTokens)

        case .none:
            managedSettingsStore.shield.applications = nil
            managedSettingsStore.shield.applicationCategories = nil
            managedSettingsStore.shield.webDomains = nil
        }
    }
}

// MARK: - Supporting Types

/// Describes a nightly bedtime window during which non-emergency apps are shielded.
public struct BedtimeWindow: Equatable, Sendable {
    public let enabled: Bool
    public let startHour: Int
    public let startMin: Int
    public let endHour: Int
    public let endMin: Int

    public init(enabled: Bool, startHour: Int, startMin: Int, endHour: Int, endMin: Int) {
        self.enabled = enabled
        self.startHour = startHour
        self.startMin = startMin
        self.endHour = endHour
        self.endMin = endMin
    }
}
