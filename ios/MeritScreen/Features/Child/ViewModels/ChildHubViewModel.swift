import SwiftUI
import Combine

/// View model driving the C05 Child Hub dashboard.
/// Fully offline-first; reads from and writes to `ChildLocalStore`.
@Observable
@MainActor
public final class ChildHubViewModel {

    // MARK: - State Properties

    public var childName: String = "Leo"
    public var childAvatar: String = "🐰"
    public var ageBand: AgeBand = .band_7_9
    public var snapshot: SessionSnapshot = SessionSnapshot()
    public var policy: ChildPolicy = ChildPolicy()
    public var appRules: [AppRule] = []
    public var unlockedStickers: [ChildSticker] = []
    public var totalXp: Int = 0
    public var explorerLevel: Int = 1

    public var showingQuizInterrupt: Bool = false
    public var quizInterruptRule: AppRule?
    public var showingFailLock: Bool = false
    public var showingBedtimeLock: Bool = false
    public var showingDailyLimitLock: Bool = false
    public var showingParentPinSheet: Bool = false
    public var showingSettingsMenu: Bool = false
    public var showingStickerBook: Bool = false
    public var isSyncingPolicy: Bool = false

    private let localStore: ChildLocalStore
    private let pairingStore: ChildPairingStore
    private let pullCoordinator: ChildPolicyPullCoordinator
    @ObservationIgnored private var clockTask: Task<Void, Never>?
    @ObservationIgnored private var lastPersistedSnapshot: SessionSnapshot?
    @ObservationIgnored private var lastPersistEpochMs: Int64 = 0

    public init(
        localStore: ChildLocalStore = .shared,
        pairingStore: ChildPairingStore = .shared,
        pullCoordinator: ChildPolicyPullCoordinator = .shared
    ) {
        self.localStore = localStore
        self.pairingStore = pairingStore
        self.pullCoordinator = pullCoordinator
        loadLocalData()
    }

    deinit {
        clockTask?.cancel()
    }

    // MARK: - Data Loading

    public func loadLocalData() {
        if let cred = pairingStore.current() {
            childName = cred.displayName.isEmpty ? "Leo" : cred.displayName
            ageBand = AgeBand.fromStorage(cred.ageBand)
        }

        policy = localStore.getCachedPolicy()
        appRules = localStore.getCachedAppRules()
        snapshot = localStore.getSessionSnapshot()
        lastPersistedSnapshot = snapshot
        lastPersistEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        unlockedStickers = localStore.getUnlockedStickers()
        totalXp = localStore.getTotalXp()
        explorerLevel = localStore.getExplorerLevel()

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isUnderGrace = snapshot.quizGraceUntilEpochMs != nil && now < (snapshot.quizGraceUntilEpochMs ?? 0)
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        if snapshot.minutesUsedToday >= ceiling && !isUnderGrace {
            snapshot.phase = .dailyLimitLock
            showingDailyLimitLock = true
            ScreenTimeEnforcementController.shared.applyDailyLimitLock(
                minutesUsed: snapshot.minutesUsedToday,
                ceilingMinutes: ceiling
            )
        } else if snapshot.phase == .shielded {
            showingFailLock = true
        } else if snapshot.phase == .bedtimeLock {
            showingBedtimeLock = true
        } else if snapshot.phase == .quizDue {
            showingQuizInterrupt = true
        } else if snapshot.phase == .idle && !policy.paused {
            // Automatically start the active screen time block timer
            startActiveBlockIfNeeded()
        }
    }

    /// Automatically begins the active screen time block countdown when child enters Hub.
    public func startActiveBlockIfNeeded() {
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isUnderGrace = snapshot.quizGraceUntilEpochMs != nil && now < (snapshot.quizGraceUntilEpochMs ?? 0)
        guard (snapshot.minutesUsedToday < ceiling || isUnderGrace), !policy.paused else { return }
        guard snapshot.phase == .idle else { return }

        let blockMinutes = policy.defaultBlockMinutes > 0 ? policy.defaultBlockMinutes : 15
        snapshot.phase = .inBlock
        snapshot.blockDurationMinutes = blockMinutes
        snapshot.minutesAccruedInBlock = 0.0
        snapshot.blockStartedEpochMs = now
        snapshot.lastTickEpochMs = now
        localStore.saveSessionSnapshot(snapshot)
        lastPersistedSnapshot = snapshot
        lastPersistEpochMs = now
    }

    // MARK: - Clock & Ticking

    public func startClock() {
        clockTask?.cancel()
        clockTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                self.tickSecond()
            }
        }
    }

    public func stopClock() {
        clockTask?.cancel()
        clockTask = nil
        flushPendingSessionState()
    }

    /// Flushes any pending in-memory session changes directly to storage.
    public func flushPendingSessionState() {
        localStore.saveSessionSnapshot(snapshot)
        lastPersistedSnapshot = snapshot
        lastPersistEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
    }

    private func tickSecond() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isAppActive = (snapshot.phase == .inBlock)
        let updated = SessionEngine.tick(
            snapshot: snapshot,
            nowEpochMs: now,
            policy: policy,
            isAppActive: isAppActive
        )

        let previousPhase = snapshot.phase
        snapshot = updated

        // Throttled persistence via SessionPersistPolicy (mirrors Android Phase 10)
        if SessionPersistPolicy.shouldPersist(
            previousPersisted: lastPersistedSnapshot,
            next: updated,
            nowElapsedMs: now,
            lastPersistElapsedMs: lastPersistEpochMs,
            intervalSeconds: AppConfig.sessionPersistIntervalSeconds
        ) {
            localStore.saveSessionSnapshot(updated)
            lastPersistedSnapshot = updated
            lastPersistEpochMs = now
        }

        if updated.phase == .dailyLimitLock && previousPhase != .dailyLimitLock {
            showingDailyLimitLock = true
            ScreenTimeEnforcementController.shared.applyDailyLimitLock(
                minutesUsed: updated.minutesUsedToday,
                ceilingMinutes: policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
            )
        } else if updated.phase == .quizDue && previousPhase != .quizDue {
            showingQuizInterrupt = true
        } else if updated.phase == .shielded && previousPhase != .shielded {
            showingFailLock = true
        } else if updated.phase == .idle && previousPhase == .shielded {
            showingFailLock = false
        }
    }

    // MARK: - Actions

    public func requestOpenApp(rule: AppRule) {
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        if !rule.isEmergency && snapshot.minutesUsedToday >= ceiling {
            showingDailyLimitLock = true
            return
        }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let updated = SessionEngine.openApp(
            snapshot: snapshot,
            nowEpochMs: now,
            rule: rule,
            policy: policy
        )
        snapshot = updated
        localStore.saveSessionSnapshot(updated)
        lastPersistedSnapshot = updated
        lastPersistEpochMs = now

        if updated.phase == .dailyLimitLock {
            showingDailyLimitLock = true
            ScreenTimeEnforcementController.shared.applyDailyLimitLock(
                minutesUsed: updated.minutesUsedToday,
                ceilingMinutes: ceiling
            )
            return
        }

        if updated.phase == .quizDue {
            quizInterruptRule = rule
            showingQuizInterrupt = true
            return
        }

        // Open native iOS application via URL scheme
        launchNativeApp(rule: rule)
    }

    private func launchNativeApp(rule: AppRule) {
        let id = (rule.packageOrBundleId + " " + rule.displayName).lowercased()
        let schemeString: String?
        if id.contains("calculator") {
            schemeString = "calculator://"
        } else if id.contains("duolingo") {
            schemeString = "duolingo://"
        } else if id.contains("khan") {
            schemeString = "khanacademykids://"
        } else if id.contains("message") || id.contains("sms") {
            schemeString = "sms:"
        } else if id.contains("phone") {
            schemeString = "tel:"
        } else if id.contains("youtube") {
            schemeString = "youtubekids://"
        } else if id.contains("safari") || id.contains("browser") {
            schemeString = "https://www.apple.com"
        } else {
            schemeString = nil
        }

        if let schemeString, let url = URL(string: schemeString), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }

    public func grantDailyBonusWithParentPin(minutes: Int = 15) {
        let updated = SessionEngine.endDailyLimitLock(snapshot: snapshot, bonusMinutes: minutes)
        snapshot = updated
        localStore.saveSessionSnapshot(updated)
        lastPersistedSnapshot = updated
        lastPersistEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        ScreenTimeEnforcementController.shared.clearDailyLimitLock()
        showingDailyLimitLock = false
        showingParentPinSheet = false
    }

    public func endFailLockWithParentPin() {
        let updated = SessionEngine.endFailLock(snapshot: snapshot)
        snapshot = updated
        localStore.saveSessionSnapshot(updated)
        lastPersistedSnapshot = updated
        lastPersistEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        showingFailLock = false
        showingParentPinSheet = false
    }

    public func onFailLockCooldownFinished() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let updated = SessionEngine.expireShieldIfNeeded(snapshot: snapshot, nowEpochMs: now)
        snapshot = updated
        localStore.saveSessionSnapshot(updated)
        lastPersistedSnapshot = updated
        lastPersistEpochMs = now
        showingFailLock = false
    }

    public func refreshPolicy() async {
        isSyncingPolicy = true
        defer { isSyncingPolicy = false }
        _ = await pullCoordinator.pullLatestPolicy()
        loadLocalData()
    }

    // MARK: - Computed Properties

    public var isFailLocked: Bool {
        snapshot.phase == .shielded
    }

    public var isDailyLimitLocked: Bool {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isUnderGrace = snapshot.quizGraceUntilEpochMs != nil && now < (snapshot.quizGraceUntilEpochMs ?? 0)
        if isUnderGrace {
            return false
        }
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        return snapshot.phase == .dailyLimitLock || (snapshot.minutesUsedToday >= ceiling && ceiling > 0)
    }

    public var remainingBlockFormatted: String {
        let seconds = snapshot.remainingBlockSeconds()
        let mins = seconds / 60
        let secs = seconds % 60
        if mins > 0 {
            return String(format: "%dm %02ds", mins, secs)
        } else {
            return String(format: "%ds", secs)
        }
    }

    public var remainingBlockProgress: Double {
        let totalSec = Double(snapshot.blockDurationMinutes * 60)
        guard totalSec > 0 else { return 0 }
        let elapsedSec = Double(snapshot.minutesAccruedInBlock * 60.0)
        return min(max(elapsedSec / totalSec, 0.0), 1.0)
    }

    public var dailyRemainingFormatted: String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let isUnderGrace = snapshot.quizGraceUntilEpochMs != nil && now < (snapshot.quizGraceUntilEpochMs ?? 0)
        if isUnderGrace, let graceUntil = snapshot.quizGraceUntilEpochMs {
            let remainingSec = max(0, Int((graceUntil - now) / 1000))
            let mins = (remainingSec + 59) / 60
            return "+\(mins)m Bonus Active"
        }
        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        if isDailyLimitLocked || snapshot.minutesUsedToday >= ceiling {
            return "\(snapshot.minutesUsedToday)/\(ceiling)m Used (Limit Reached)"
        }
        let remainingMins = snapshot.dailyRemainingMinutes(policy: policy)
        let hrs = remainingMins / 60
        let mins = remainingMins % 60
        if hrs > 0 {
            return "\(hrs)h \(mins)m left today"
        } else {
            return "\(mins)m left today"
        }
    }

    public var dailyCeilingProgress: Double {
        let ceiling = Double(policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes)
        guard ceiling > 0 else { return 0 }
        let used = Double(snapshot.minutesUsedToday)
        return min(max(used / ceiling, 0.0), 1.0)
    }

    public var activeAppName: String {
        if let activeId = snapshot.activeAppId,
           let rule = appRules.first(where: { $0.appId == activeId || $0.packageOrBundleId == activeId }) {
            return rule.displayName
        }
        return "Allowed Apps"
    }
}
