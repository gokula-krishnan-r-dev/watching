import Foundation

/// Thread-safe local persistence store for child session state, offline cached policy,
/// app rules, skill mastery, and stickers.
///
/// Invariant: Child Hub and Quiz flows read exclusively from local store.
/// Zero network or Firestore dependencies on the hot path.
public final class ChildLocalStore: @unchecked Sendable {
    public static let shared = ChildLocalStore()

    private let lock = NSLock()
    private let defaults: UserDefaults

    private let sessionKey = "meritscreen.child_session_snapshot"
    private let policyKey = "meritscreen.child_cached_policy"
    private let rulesKey = "meritscreen.child_cached_rules"
    private let skillsKey = "meritscreen.child_topic_skills"
    private let stickersKey = "meritscreen.child_unlocked_stickers"
    private let recentQuestionsKey = "meritscreen.child_recent_question_ids"
    private let xpKey = "meritscreen.child_total_xp"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: - Session Snapshot

    public func getSessionSnapshot() -> SessionSnapshot {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: sessionKey),
              let snapshot = try? JSONDecoder().decode(SessionSnapshot.self, from: data) else {
            return SessionSnapshot()
        }
        return snapshot
    }

    public func saveSessionSnapshot(_ snapshot: SessionSnapshot) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: sessionKey)
        }
    }

    // MARK: - Cached Child Policy

    public func getCachedPolicy() -> ChildPolicy {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: policyKey),
              let policy = try? JSONDecoder().decode(ChildPolicy.self, from: data) else {
            return ChildPolicy()
        }
        return policy
    }

    public func saveCachedPolicy(_ policy: ChildPolicy) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(policy) {
            defaults.set(data, forKey: policyKey)
        }
    }

    // MARK: - Cached App Rules

    public func getCachedAppRules() -> [AppRule] {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: rulesKey),
              let rules = try? JSONDecoder().decode([AppRule].self, from: data) else {
            return defaultSampleRules()
        }
        return rules
    }

    public func saveCachedAppRules(_ rules: [AppRule]) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(rules) {
            defaults.set(data, forKey: rulesKey)
        }
    }

    // MARK: - Topic Skills

    public func getTopicSkills() -> [String: TopicSkill] {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: skillsKey),
              let skills = try? JSONDecoder().decode([String: TopicSkill].self, from: data) else {
            return [:]
        }
        return skills
    }

    public func saveTopicSkills(_ skills: [String: TopicSkill]) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(skills) {
            defaults.set(data, forKey: skillsKey)
        }
    }

    public func updateTopicSkill(_ skill: TopicSkill) {
        lock.lock()
        defer { lock.unlock() }
        var current = (try? JSONDecoder().decode([String: TopicSkill].self, from: defaults.data(forKey: skillsKey) ?? Data())) ?? [:]
        current[skill.topic] = skill
        if let data = try? JSONEncoder().encode(current) {
            defaults.set(data, forKey: skillsKey)
        }
    }

    // MARK: - Recent Question History

    public func getRecentQuestionIds() -> Set<String> {
        lock.lock()
        defer { lock.unlock() }
        let list = defaults.stringArray(forKey: recentQuestionsKey) ?? []
        return Set(list)
    }

    public func recordQuestionAnswered(questionId: String, prompt: String) {
        lock.lock()
        defer { lock.unlock() }
        var list = defaults.stringArray(forKey: recentQuestionsKey) ?? []
        list.append(questionId)
        list.append(AdaptiveQuizEngine.promptHistoryKey(prompt))
        // Keep up to 50 recent items
        if list.count > 50 {
            list = Array(list.suffix(50))
        }
        defaults.set(list, forKey: recentQuestionsKey)
    }

    // MARK: - Stickers & XP

    public func getUnlockedStickers() -> [ChildSticker] {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: stickersKey),
              let stickers = try? JSONDecoder().decode([ChildSticker].self, from: data) else {
            return defaultStartingStickers()
        }
        return stickers
    }

    public func addSticker(_ sticker: ChildSticker) {
        lock.lock()
        defer { lock.unlock() }
        var stickers = (try? JSONDecoder().decode([ChildSticker].self, from: defaults.data(forKey: stickersKey) ?? Data())) ?? defaultStartingStickers()
        if !stickers.contains(where: { $0.id == sticker.id }) {
            stickers.append(sticker)
            if let data = try? JSONEncoder().encode(stickers) {
                defaults.set(data, forKey: stickersKey)
            }
        }
    }

    public func getTotalXp() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return defaults.integer(forKey: xpKey)
    }

    public func addXp(_ points: Int) -> Int {
        lock.lock()
        defer { lock.unlock() }
        let newTotal = max(0, defaults.integer(forKey: xpKey) + points)
        defaults.set(newTotal, forKey: xpKey)
        return newTotal
    }

    public func getExplorerLevel() -> Int {
        let xp = getTotalXp()
        let thresholds = AppConfig.explorerLevelXpThresholds
        for level in stride(from: thresholds.count - 1, through: 1, by: -1) {
            if xp >= thresholds[level] {
                return min(level, AppConfig.explorerLevelMax)
            }
        }
        return 1
    }

    // MARK: - Clear / Reset

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        defaults.removeObject(forKey: sessionKey)
        defaults.removeObject(forKey: policyKey)
        defaults.removeObject(forKey: rulesKey)
        defaults.removeObject(forKey: skillsKey)
        defaults.removeObject(forKey: stickersKey)
        defaults.removeObject(forKey: recentQuestionsKey)
        defaults.removeObject(forKey: xpKey)
        defaults.removeObject(forKey: pendingAttemptsKey)
    }

    // MARK: - Pending Quiz Attempts Queue

    private let pendingAttemptsKey = "meritscreen.child_pending_quiz_attempts"

    public func getPendingQuizAttempts() -> [RemoteQuizAttempt] {
        lock.lock()
        defer { lock.unlock() }
        guard let data = defaults.data(forKey: pendingAttemptsKey),
              let attempts = try? JSONDecoder().decode([RemoteQuizAttempt].self, from: data) else {
            return []
        }
        return attempts
    }

    public func addPendingQuizAttempt(_ attempt: RemoteQuizAttempt) {
        lock.lock()
        defer { lock.unlock() }
        var list = getPendingQuizAttemptsInternal()
        list.append(attempt)
        if let data = try? JSONEncoder().encode(list) {
            defaults.set(data, forKey: pendingAttemptsKey)
        }
    }

    public func clearPendingQuizAttempts() {
        lock.lock()
        defer { lock.unlock() }
        defaults.removeObject(forKey: pendingAttemptsKey)
    }

    private func getPendingQuizAttemptsInternal() -> [RemoteQuizAttempt] {
        guard let data = defaults.data(forKey: pendingAttemptsKey),
              let attempts = try? JSONDecoder().decode([RemoteQuizAttempt].self, from: data) else {
            return []
        }
        return attempts
    }

    // MARK: - Defaults

    private func defaultSampleRules() -> [AppRule] {
        return [
            AppRule(
                appId: "com.duolingo.DuolingoMobile",
                packageOrBundleId: "com.duolingo.DuolingoMobile",
                displayName: "Duolingo ABC",
                allowed: true,
                blockMinutes: 20,
                grantOnPassMinutes: 20,
                cooldownMinutes: 15
            ),
            AppRule(
                appId: "org.khancademy.KhanKids",
                packageOrBundleId: "org.khancademy.KhanKids",
                displayName: "Khan Academy Kids",
                allowed: true,
                blockMinutes: 25,
                grantOnPassMinutes: 25,
                cooldownMinutes: 15
            ),
            AppRule(
                appId: "com.apple.calculator",
                packageOrBundleId: "com.apple.calculator",
                displayName: "Calculator",
                allowed: true,
                blockMinutes: 30,
                grantOnPassMinutes: 30,
                cooldownMinutes: 0
            ),
            AppRule(
                appId: "com.apple.mobilesafari",
                packageOrBundleId: "com.apple.mobilesafari",
                displayName: "Safari",
                allowed: true,
                blockMinutes: 15,
                grantOnPassMinutes: 15,
                cooldownMinutes: 15
            ),
            AppRule(
                appId: "com.google.ios.youtube",
                packageOrBundleId: "com.google.ios.youtube",
                displayName: "YouTube Kids",
                allowed: true,
                blockMinutes: 20,
                grantOnPassMinutes: 20,
                cooldownMinutes: 15
            ),
            AppRule(
                appId: "com.apple.mobilephone",
                packageOrBundleId: "com.apple.mobilephone",
                displayName: "Phone",
                allowed: true,
                blockMinutes: 120,
                grantOnPassMinutes: 120,
                cooldownMinutes: 0,
                isEmergency: true
            )
        ]
    }

    private func defaultStartingStickers() -> [ChildSticker] {
        return [
            ChildSticker(id: "welcome_explorer", title: "Little Explorer", emoji: "🧭", stage: "Bronze", xpAtUnlock: 0),
            ChildSticker(id: "curious_fox", title: "Curious Fox", emoji: "🦊", stage: "Bronze", xpAtUnlock: 10),
            ChildSticker(id: "star_math", title: "Number Star", emoji: "⭐", stage: "Silver", xpAtUnlock: 30)
        ]
    }
}
