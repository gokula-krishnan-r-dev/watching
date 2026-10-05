import Foundation

/// Maps `ChildPolicy` ↔ Firestore `policy/current` field maps.
/// Mirrors Android `ChildPolicyMapper` (shared policy JSON with Android).
public enum ChildPolicyMapper {
    public static func toMap(_ policy: ChildPolicy) -> [String: Any] {
        var map: [String: Any] = [
            "quizMode": policy.quizMode.rawValue,
            "failLockScope": "all_non_emergency",
            "allowRetryDuringCooldown": policy.allowRetryDuringCooldown,
            "questionsPerQuiz": min(5, max(3, policy.questionsPerQuiz)),
            "passScorePercent": min(100, max(50, policy.passScorePercent)),
            "rewardsEnabled": policy.rewardsEnabled,
            "weekendBonusEnabled": policy.weekendBonusEnabled,
            "extraMinutesOnPass": min(60, max(0, policy.extraMinutesOnPass)),
            "aiQuizzesEnabled": policy.aiQuizzesEnabled,
            "adaptiveDifficultyEnabled": policy.adaptiveDifficultyEnabled,
            "showExplanations": policy.showExplanations,
            "defaultBlockMinutes": min(240, max(5, policy.defaultBlockMinutes)),
            "defaultCooldownMinutes": min(180, max(1, policy.defaultCooldownMinutes)),
            "paused": policy.paused,
            "bonusMinutesToday": policy.bonusMinutesToday,
            "gradeStandard": policy.gradeStandard,
            "region": policy.learningProfile.region.rawValue,
            "customPromptGuidelines": policy.customPromptGuidelines,
            "curriculumFocusIds": policy.curriculumFocusIds,
            "bedtimeEnabled": policy.bedtimeEnabled,
            "bedtimeStartLabel": policy.bedtimeStartLabel,
            "bedtimeEndLabel": policy.bedtimeEndLabel,
        ]
        if let ceiling = policy.dailyCeilingMinutes {
            map["dailyCeilingMinutes"] = ceiling
        }
        return map
    }

    public static func fromMap(_ data: [String: Any]) -> ChildPolicy {
        func intOr(_ key: String, _ defaultValue: Int) -> Int {
            if let number = data[key] as? NSNumber { return number.intValue }
            if let value = data[key] as? Int { return value }
            return defaultValue
        }
        func boolOr(_ key: String, _ defaultValue: Bool) -> Bool {
            data[key] as? Bool ?? defaultValue
        }
        func stringOr(_ key: String, _ defaultValue: String) -> String {
            (data[key] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? defaultValue
        }

        let regionRaw = stringOr("region", LearningRegion.IN.rawValue)
        let region = LearningRegion(rawValue: regionRaw) ?? .IN
        let focusIds = (data["curriculumFocusIds"] as? [Any])?
            .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty } ?? []

        let ceiling: Int? = {
            if data["dailyCeilingMinutes"] == nil { return nil }
            return intOr("dailyCeilingMinutes", AppConfig.defaultDailyCeilingMinutes)
        }()

        return ChildPolicy(
            paused: boolOr("paused", false),
            bonusMinutesToday: intOr("bonusMinutesToday", 0),
            quizMode: QuizMode.fromStorage(data["quizMode"] as? String),
            allowRetryDuringCooldown: boolOr("allowRetryDuringCooldown", true),
            dailyCeilingMinutes: ceiling,
            questionsPerQuiz: intOr("questionsPerQuiz", AppConfig.defaultQuestionsPerQuiz),
            passScorePercent: intOr("passScorePercent", AppConfig.defaultPassScorePercent),
            rewardsEnabled: boolOr("rewardsEnabled", true),
            weekendBonusEnabled: boolOr("weekendBonusEnabled", false),
            extraMinutesOnPass: intOr("extraMinutesOnPass", 0),
            aiQuizzesEnabled: boolOr("aiQuizzesEnabled", true),
            adaptiveDifficultyEnabled: boolOr("adaptiveDifficultyEnabled", true),
            showExplanations: boolOr("showExplanations", true),
            defaultBlockMinutes: intOr("defaultBlockMinutes", AppConfig.defaultBlockMinutes),
            defaultCooldownMinutes: intOr("defaultCooldownMinutes", AppConfig.defaultCooldownMinutes),
            bedtimeEnabled: boolOr("bedtimeEnabled", true),
            bedtimeStartLabel: stringOr("bedtimeStartLabel", AppConfig.defaultBedtimeLabel),
            bedtimeEndLabel: stringOr("bedtimeEndLabel", "7:00 AM"),
            curriculumFocusIds: focusIds,
            learningProfile: LearningProfile(
                gradeStandard: stringOr("gradeStandard", ""),
                region: region,
                customPromptGuidelines: stringOr("customPromptGuidelines", "")
            )
        )
    }
}
