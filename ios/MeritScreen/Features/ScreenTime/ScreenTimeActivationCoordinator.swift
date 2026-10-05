import SwiftUI
import FamilyControls
import ManagedSettings
import DeviceActivity

/// Orchestrates the complete Screen Time activation flow:
/// 1. Request FamilyControls authorization
/// 2. Let child/parent pick apps via `FamilyActivityPicker`
/// 3. Start `DeviceActivityCenter` monitoring with real policy values
///
/// Acts as the single source of truth for whether Screen Time is properly activated.
/// Call `activateWithPolicy(policy:rules:childName:)` after the child device pairs
/// and the first policy pull succeeds.
@Observable
@MainActor
public final class ScreenTimeActivationCoordinator {
    public static let shared = ScreenTimeActivationCoordinator()

    // MARK: - State

    public var authorizationStatus: AuthorizationStatus = .notDetermined
    public var isActivated: Bool = false
    public var isLoading: Bool = false
    public var errorMessage: String?

    /// The current FamilyActivitySelection (persisted in App Group).
    public var activitySelection: FamilyActivitySelection = FamilyActivitySelection()

    private let authManager: ScreenTimeAuthorizationManager
    private let enforcement: ScreenTimeEnforcementController
    private let sharedStore: ScreenTimeSharedStore

    public init(
        authManager: ScreenTimeAuthorizationManager = .shared,
        enforcement: ScreenTimeEnforcementController = .shared,
        sharedStore: ScreenTimeSharedStore = .shared
    ) {
        self.authManager = authManager
        self.enforcement = enforcement
        self.sharedStore = sharedStore
        refreshState()
    }

    // MARK: - Public Interface

    /// Refreshes authorization status and determines if monitoring is fully active.
    public func refreshState() {
        authManager.refreshStatus()
        authorizationStatus = authManager.status
        activitySelection = sharedStore.getActivitySelection()
        // Monitoring is active if authorized AND we have a saved non-empty selection
        isActivated = (authorizationStatus == .approved) &&
            (!activitySelection.applicationTokens.isEmpty ||
             !activitySelection.categoryTokens.isEmpty)
    }

    /// Full activation: request authorization, then start monitoring with policy values.
    /// Call this after initial child device pairing + first policy pull.
    /// - Parameters:
    ///   - selection: Apps/categories selected via FamilyActivityPicker (or pre-built from policy).
    ///   - policy: Child's active policy (for block/cooldown minutes, bedtime).
    ///   - childName: Child display name for shield UI.
    @discardableResult
    public func activate(
        selection: FamilyActivitySelection,
        blockMinutes: Int,
        cooldownMinutes: Int,
        childName: String,
        bedtime: BedtimeWindow? = nil
    ) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // Step 1: Request FamilyControls authorization (shows system prompt if needed)
        if authorizationStatus != .approved {
            let granted = await authManager.requestAuthorization(for: .individual)
            authorizationStatus = authManager.status
            guard granted else {
                errorMessage = "Screen Time authorization is required for MeritScreen to work. Please allow it in Settings > Screen Time."
                return false
            }
        }

        // Step 2: Start monitoring with the provided selection
        enforcement.startMonitoring(
            selection: selection,
            blockMinutes: blockMinutes,
            cooldownMinutes: cooldownMinutes,
            childName: childName,
            bedtime: bedtime
        )

        activitySelection = selection
        isActivated = !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty
        print("[ScreenTimeActivation] Activated: \(selection.applicationTokens.count) apps monitored")
        return isActivated
    }

    /// Re-applies monitoring from saved selection (e.g., after app update or reboot).
    /// Safe to call even if monitoring is already running.
    public func reactivateFromSavedPolicy(
        blockMinutes: Int,
        cooldownMinutes: Int,
        childName: String,
        bedtime: BedtimeWindow? = nil
    ) {
        let saved = sharedStore.getActivitySelection()
        guard !saved.applicationTokens.isEmpty || !saved.categoryTokens.isEmpty else {
            print("[ScreenTimeActivation] No saved selection — skipping reactivation")
            return
        }
        enforcement.startMonitoring(
            selection: saved,
            blockMinutes: blockMinutes,
            cooldownMinutes: cooldownMinutes,
            childName: childName,
            bedtime: bedtime
        )
        activitySelection = saved
        isActivated = true
    }

    /// Updates the activity selection and restarts monitoring.
    /// Call after user changes the FamilyActivityPicker selection.
    public func updateSelection(
        _ selection: FamilyActivitySelection,
        blockMinutes: Int,
        cooldownMinutes: Int,
        childName: String
    ) async -> Bool {
        return await activate(
            selection: selection,
            blockMinutes: blockMinutes,
            cooldownMinutes: cooldownMinutes,
            childName: childName
        )
    }
}
