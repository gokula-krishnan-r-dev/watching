import Foundation

/// Uploads offline screen time minutes and pending quiz attempts from `ChildLocalStore` to Firestore.
/// Mirrors `com.meritscreen.feature.child.data.UsageSyncWorker` in Android.
public final class UsageSyncCoordinator: @unchecked Sendable {
    public static let shared = UsageSyncCoordinator()

    private let pairingStore: ChildPairingStore
    private let localStore: ChildLocalStore
    private let uploadClient: UsageUploadClientProtocol
    private let revocationGuard: DeviceRevocationGuard

    private let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = TimeZone.current
        return df
    }()

    public init(
        pairingStore: ChildPairingStore = .shared,
        localStore: ChildLocalStore = .shared,
        uploadClient: UsageUploadClientProtocol = FirestoreUsageUploadClient.shared,
        revocationGuard: DeviceRevocationGuard = .shared
    ) {
        self.pairingStore = pairingStore
        self.localStore = localStore
        self.uploadClient = uploadClient
        self.revocationGuard = revocationGuard
    }

    /// Uploads today's accumulated screen time usage and any pending completed quiz attempts.
    /// Idempotent: Only clears pending queue upon successful upload.
    @discardableResult
    public func syncUsageAndAttempts() async -> Bool {
        if revocationGuard.isRevoked {
            SecureLogger.warning("[UsageSyncCoordinator] Device is revoked. Aborting usage upload.")
            return false
        }

        guard let credential = pairingStore.current() else {
            return false
        }

        let snapshot = localStore.getSessionSnapshot()
        let rules = localStore.getCachedAppRules()
        let todayStr = dateFormatter.string(from: Date())

        // Build per-app rollups from the active session:
        // Each allowed app gets credit for minutes actually used in its last block.
        // The active app gets the majority of today's session minutes.
        var appMinutes: [String: Int] = [:]
        let totalMinutesUsed = snapshot.minutesUsedToday

        if let activeId = snapshot.activeAppId {
            // Active app gets all accumulated block minutes
            appMinutes[activeId] = Int(snapshot.minutesAccruedInBlock)
        }

        // Distribute remaining minutes proportionally across other allowed apps
        let activeRules = rules.filter { $0.allowed && !$0.isEmergency }
        for rule in activeRules where rule.appId != (snapshot.activeAppId ?? "") {
            // Approximate: attribute a block's worth of usage if the rule has been accessed
            // (In production, DeviceActivity usage reports would give exact per-app minutes)
            let estimated = min(rule.blockMinutes, max(0, totalMinutesUsed - (appMinutes.values.reduce(0, +))))
            if estimated > 0 {
                appMinutes[rule.appId] = estimated
            }
        }


        do {
            // 1. Upload daily screen time summary
            try await uploadClient.uploadUsage(
                familyId: credential.familyId,
                childId: credential.childId,
                dateString: todayStr,
                minutesUsed: snapshot.minutesUsedToday,
                appMinutes: appMinutes
            )

            // 2. Upload any pending quiz attempts
            let pendingAttempts = localStore.getPendingQuizAttempts()
            for attempt in pendingAttempts {
                try await uploadClient.uploadQuizAttempt(
                    familyId: credential.familyId,
                    childId: credential.childId,
                    attempt: attempt
                )
            }

            // Successfully uploaded, clear pending attempts queue
            localStore.clearPendingQuizAttempts()
            return true
        } catch {
            SecureLogger.error("[UsageSyncCoordinator] Failed to upload usage/attempts: \(error.localizedDescription)")
            return false
        }
    }
}
