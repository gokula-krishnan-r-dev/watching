import XCTest
@testable import MeritScreen

/// Validates cross-platform contract parity with the Android client and shared Firestore/Functions backend.
/// Mirrors `com.meritscreen.core.common.domain.ChildPolicyMapperTest` in Android.
final class CrossPlatformContractTests: XCTestCase {

    // MARK: - ChildPolicy Cross-Platform JSON Parity Tests

    func testChildPolicyMapperToMapParityWithAndroidSchema() {
        var policy = ChildPolicy()
        policy.quizMode = .appBlock
        policy.allowRetryDuringCooldown = true
        policy.questionsPerQuiz = 4
        policy.passScorePercent = 75
        policy.rewardsEnabled = true
        policy.weekendBonusEnabled = true
        policy.extraMinutesOnPass = 10
        policy.aiQuizzesEnabled = true
        policy.adaptiveDifficultyEnabled = true
        policy.showExplanations = true
        policy.defaultBlockMinutes = 25
        policy.defaultCooldownMinutes = 20
        policy.paused = false
        policy.bonusMinutesToday = 15
        policy.gradeStandard = "Grade 4"
        policy.learningProfile.region = .US
        policy.customPromptGuidelines = "Emphasize fractions and algebra."
        policy.curriculumFocusIds = ["math_fractions", "science_ecosystems"]
        policy.bedtimeEnabled = true
        policy.bedtimeStartLabel = "8:30 PM"
        policy.bedtimeEndLabel = "7:00 AM"
        policy.dailyCeilingMinutes = 120

        let map = ChildPolicyMapper.toMap(policy)

        // Strict cross-platform schema invariants
        XCTAssertEqual(map["quizMode"] as? String, "app_block")
        XCTAssertEqual(map["failLockScope"] as? String, "all_non_emergency")
        XCTAssertEqual(map["allowRetryDuringCooldown"] as? Bool, true)
        XCTAssertEqual(map["questionsPerQuiz"] as? Int, 4)
        XCTAssertEqual(map["passScorePercent"] as? Int, 75)
        XCTAssertEqual(map["rewardsEnabled"] as? Bool, true)
        XCTAssertEqual(map["weekendBonusEnabled"] as? Bool, true)
        XCTAssertEqual(map["extraMinutesOnPass"] as? Int, 10)
        XCTAssertEqual(map["aiQuizzesEnabled"] as? Bool, true)
        XCTAssertEqual(map["adaptiveDifficultyEnabled"] as? Bool, true)
        XCTAssertEqual(map["showExplanations"] as? Bool, true)
        XCTAssertEqual(map["defaultBlockMinutes"] as? Int, 25)
        XCTAssertEqual(map["defaultCooldownMinutes"] as? Int, 20)
        XCTAssertEqual(map["paused"] as? Bool, false)
        XCTAssertEqual(map["bonusMinutesToday"] as? Int, 15)
        XCTAssertEqual(map["gradeStandard"] as? String, "Grade 4")
        XCTAssertEqual(map["region"] as? String, "US")
        XCTAssertEqual(map["customPromptGuidelines"] as? String, "Emphasize fractions and algebra.")
        XCTAssertEqual(map["curriculumFocusIds"] as? [String], ["math_fractions", "science_ecosystems"])
        XCTAssertEqual(map["bedtimeEnabled"] as? Bool, true)
        XCTAssertEqual(map["bedtimeStartLabel"] as? String, "8:30 PM")
        XCTAssertEqual(map["bedtimeEndLabel"] as? String, "7:00 AM")
        XCTAssertEqual(map["dailyCeilingMinutes"] as? Int, 120)
    }

    func testChildPolicyMapperFromAndroidMap() {
        // Simulates raw Firestore JSON written by an Android Parent app
        let androidMap: [String: Any] = [
            "quizMode": "app_block",
            "failLockScope": "all_non_emergency",
            "allowRetryDuringCooldown": true,
            "questionsPerQuiz": 3,
            "passScorePercent": 70,
            "rewardsEnabled": true,
            "weekendBonusEnabled": false,
            "extraMinutesOnPass": 0,
            "aiQuizzesEnabled": true,
            "adaptiveDifficultyEnabled": true,
            "showExplanations": true,
            "defaultBlockMinutes": 15,
            "defaultCooldownMinutes": 15,
            "paused": true,
            "bonusMinutesToday": 5,
            "gradeStandard": "Class 3",
            "region": "IN",
            "customPromptGuidelines": "Simple multiplication",
            "curriculumFocusIds": ["math_basic"],
            "bedtimeEnabled": false,
            "bedtimeStartLabel": "8:00 PM",
            "bedtimeEndLabel": "6:30 AM",
            "dailyCeilingMinutes": 90
        ]

        let parsedPolicy = ChildPolicyMapper.fromMap(androidMap)

        XCTAssertEqual(parsedPolicy.quizMode, .appBlock)
        XCTAssertTrue(parsedPolicy.allowRetryDuringCooldown)
        XCTAssertEqual(parsedPolicy.questionsPerQuiz, 3)
        XCTAssertEqual(parsedPolicy.passScorePercent, 70)
        XCTAssertTrue(parsedPolicy.paused)
        XCTAssertEqual(parsedPolicy.bonusMinutesToday, 5)
        XCTAssertEqual(parsedPolicy.gradeStandard, "Class 3")
        XCTAssertEqual(parsedPolicy.learningProfile.region, .IN)
        XCTAssertEqual(parsedPolicy.customPromptGuidelines, "Simple multiplication")
        XCTAssertEqual(parsedPolicy.curriculumFocusIds, ["math_basic"])
        XCTAssertFalse(parsedPolicy.bedtimeEnabled)
        XCTAssertEqual(parsedPolicy.dailyCeilingMinutes, 90)
    }

    // MARK: - AppRule Cross-Platform Parity Tests

    func testAppRuleSerializationRoundTrip() throws {
        let rule = AppRule(
            appId: "com.google.ios.youtube",
            packageOrBundleId: "com.google.ios.youtube",
            displayName: "YouTube",
            allowed: true,
            blockMinutes: 20,
            cooldownMinutes: 15
        )

        let data = try JSONEncoder().encode(rule)
        let decoded = try JSONDecoder().decode(AppRule.self, from: data)

        XCTAssertEqual(decoded.appId, "com.google.ios.youtube")
        XCTAssertEqual(decoded.packageOrBundleId, "com.google.ios.youtube")
        XCTAssertEqual(decoded.displayName, "YouTube")
        XCTAssertTrue(decoded.allowed)
        XCTAssertEqual(decoded.blockMinutes, 20)
        XCTAssertEqual(decoded.cooldownMinutes, 15)
    }

    // MARK: - DeviceHeartbeatInfo Cross-Platform Contract

    func testDeviceHeartbeatInfoSchemaParity() {
        let info = DeviceHeartbeatInfo(
            launcherDefault: false,
            model: "iPhone 16 Pro",
            batteryPercent: 88,
            osVersion: "18.0",
            appVersion: "1.0.0"
        )

        XCTAssertEqual(info.launcherDefault, false)
        XCTAssertEqual(info.model, "iPhone 16 Pro")
        XCTAssertEqual(info.batteryPercent, 88)
        XCTAssertEqual(info.osVersion, "18.0")
        XCTAssertEqual(info.appVersion, "1.0.0")
    }

    // MARK: - RemoteQuizAttempt Contract Parity

    func testRemoteQuizAttemptSchemaParity() throws {
        let attempt = RemoteQuizAttempt(
            attemptId: "att_cross_1",
            appId: "com.apple.mobilesafari",
            passed: true,
            scorePercent: 100,
            questionsAnswered: 3,
            ageBand: "band_7_9"
        )

        let data = try JSONEncoder().encode(attempt)
        let decoded = try JSONDecoder().decode(RemoteQuizAttempt.self, from: data)

        XCTAssertEqual(decoded.attemptId, "att_cross_1")
        XCTAssertEqual(decoded.appId, "com.apple.mobilesafari")
        XCTAssertTrue(decoded.passed)
        XCTAssertEqual(decoded.scorePercent, 100)
        XCTAssertEqual(decoded.questionsAnswered, 3)
        XCTAssertEqual(decoded.ageBand, "band_7_9")
    }
}
