import DeviceActivity
import Foundation
import ManagedSettings
import FamilyControls
import os

/// Background extension invoked by iOS when DeviceActivity schedules or event thresholds fire.
/// Keep execution minimal: write state flags to shared App Group and adjust ManagedSettingsStore shields.
/// IMPORTANT: This process runs in a separate address space — no Firebase, no URLSession.
/// Communication happens ONLY via ScreenTimeSharedStore (App Group UserDefaults).
final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let logger = Logger(subsystem: "com.meritscreen.app.deviceactivity", category: "Monitor")
    private let sharedStore = ScreenTimeSharedStore.shared
    private let managedStore = ManagedSettingsStore()

    // MARK: - Interval Lifecycle

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        logger.info("DeviceActivity intervalDidStart: \(activity.rawValue, privacy: .public)")

        // At the start of each daily interval, clear any leftover quiz-due shields
        // so a new day starts fresh (unless there is a fail lock still active).
        let state = sharedStore.getShieldState()
        if case .failLock(let deadline, _) = state {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            if now >= deadline {
                // Expired fail-lock from previous day — clear it
                managedStore.shield.applications = nil
                managedStore.shield.applicationCategories = nil
                managedStore.shield.webDomains = nil
                sharedStore.saveShieldState(.none)
                logger.info("Cleared expired fail-lock at interval start")
            }
            // else: still within cooldown — leave shields in place
        } else if case .quizDue = state {
            // Clear quiz-due state at start of new day so child gets a fresh day
            managedStore.shield.applications = nil
            sharedStore.saveShieldState(.none)
            logger.info("Cleared stale quiz-due at new interval start")
        }

        // Check bedtime enforcement at interval start
        applyBedtimeIfNeeded()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        logger.info("DeviceActivity intervalDidEnd: \(activity.rawValue, privacy: .public)")
    }

    // MARK: - Event Threshold — Block Timer Expired

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)
        logger.info("DeviceActivity threshold reached for event: \(event.rawValue, privacy: .public)")

        // Read actual policy values from shared store (set by main app after policy pull)
        let blockMinutes = sharedStore.getBlockMinutes(default: 30)
        let childName = sharedStore.getChildName()

        // 1. Write quiz-due shield state
        sharedStore.saveShieldState(.quizDue(appName: childName, blockMinutes: blockMinutes))

        // 2. Queue quiz request so ChildHubView picks it up on next foreground
        sharedStore.setPendingQuizRequest(PendingQuizRequest(appName: childName, isRetry: false))

        // 3. Apply interim shield to all monitored (non-emergency) apps
        let selection = sharedStore.getActivitySelection()
        let emergencyTokens = sharedStore.getEmergencyTokens()
        let shieldedApps = selection.applicationTokens.subtracting(emergencyTokens)

        if !shieldedApps.isEmpty {
            managedStore.shield.applications = shieldedApps
        }
        if !selection.categoryTokens.isEmpty {
            managedStore.shield.applicationCategories = .specific(selection.categoryTokens)
        }

        logger.info("Applied quiz-due shield to \(shieldedApps.count) apps, block=\(blockMinutes)min")
    }

    // MARK: - Warning Hooks

    override func intervalWillStartWarning(for activity: DeviceActivityName) {
        super.intervalWillStartWarning(for: activity)
    }

    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        super.intervalWillEndWarning(for: activity)
        // Apply bedtime shield proactively when the day's interval is about to end
        applyBedtimeIfNeeded()
    }

    override func eventWillReachThresholdWarning(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventWillReachThresholdWarning(event, activity: activity)
        // Could notify the child "5 minutes remaining" here via UserNotifications
        // For now just log
        logger.info("Block threshold warning for: \(event.rawValue, privacy: .public)")
    }

    // MARK: - Bedtime Enforcement

    private func applyBedtimeIfNeeded() {
        guard sharedStore.isCurrentlyBedtime() else { return }

        // Only apply bedtime shield if not already in a fail-lock
        let currentState = sharedStore.getShieldState()
        guard case .none = currentState else { return }

        let selection = sharedStore.getActivitySelection()
        let emergencyTokens = sharedStore.getEmergencyTokens()
        let shieldedApps = selection.applicationTokens.subtracting(emergencyTokens)

        if !shieldedApps.isEmpty {
            managedStore.shield.applications = shieldedApps
        }
        if !selection.categoryTokens.isEmpty {
            managedStore.shield.applicationCategories = .specific(selection.categoryTokens)
        }

        let endLabel = sharedStore.bedtimeEndLabel()
        sharedStore.saveShieldState(.bedtimeLock(endLabel: endLabel))
        logger.info("Applied bedtime lock (ends: \(endLabel, privacy: .public))")
    }
}
