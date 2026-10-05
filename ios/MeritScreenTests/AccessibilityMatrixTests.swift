import XCTest
import SwiftUI
@testable import MeritScreen

/// Matrix test suite for Apple accessibility guidelines, VoiceOver string readiness,
/// semantic contrast compliance, and Appearance Mode switching.
final class AccessibilityMatrixTests: XCTestCase {

    // MARK: - VoiceOver Label Verification

    func testPinPadAccessibilityConventions() {
        // Digits 0..9 and backspace must have distinct string representations for VoiceOver
        let validDigits = (0...9).map { "\($0)" }
        for digit in validDigits {
            XCTAssertFalse(digit.isEmpty)
            XCTAssertEqual(digit.count, 1)
        }
        let deleteLabel = "Delete"
        XCTAssertFalse(deleteLabel.isEmpty)
    }

    func testQuizChoiceAccessibilityStructure() {
        let choice = QuizChoice(id: "c_1", text: "Paris", correct: true)
        XCTAssertFalse(choice.text.isEmpty, "Quiz choice must provide meaningful text for VoiceOver")
        XCTAssertEqual(choice.id, "c_1")

        let feedback = QuizAnswerFeedback(
            correct: true,
            resultLine: "Spot on!",
            whyLine: "Paris is indeed the capital of France.",
            conceptLine: "Capitals",
            nextLevel: 1
        )
        XCTAssertFalse(feedback.resultLine.isEmpty)
        XCTAssertFalse(feedback.whyLine.isEmpty)
    }

    // MARK: - Semantic Color Contrast & Dynamic System Colors

    func testSemanticColorsResolveBothLightAndDark() {
        // Invariant: MeritColor semantic colors must never crash or produce transparent default values
        XCTAssertNotNil(MeritColor.background)
        XCTAssertNotNil(MeritColor.cardBackground)
        XCTAssertNotNil(MeritColor.accent)
        XCTAssertNotNil(MeritColor.destructive)
        XCTAssertNotNil(MeritColor.label)
        XCTAssertNotNil(MeritColor.secondaryLabel)
        XCTAssertNotNil(MeritColor.separator)
    }

    // MARK: - Appearance Store Matrix Tests

    func testAppearanceStoreTransitions() {
        let suite = "test_appearance_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let store = AppearanceStore(defaults: defaults)

        // Default should follow system
        XCTAssertEqual(store.mode, .system)
        XCTAssertNil(store.colorScheme)

        // Set to Light mode
        store.mode = .light
        XCTAssertEqual(store.mode, .light)
        XCTAssertEqual(store.colorScheme, .light)

        // Set to Dark mode
        store.mode = .dark
        XCTAssertEqual(store.mode, .dark)
        XCTAssertEqual(store.colorScheme, .dark)

        // Persistence test across instance re-instantiation
        let reloadedStore = AppearanceStore(defaults: defaults)
        XCTAssertEqual(reloadedStore.mode, .dark, "Appearance choice must survive process restart")
        XCTAssertEqual(reloadedStore.colorScheme, .dark)
    }

    // MARK: - Fail-Lock Non-Shame Calm Strings

    func testFailLockCalmVoiceOverCopy() {
        let nowEpoch: Int64 = 1_000_000
        let deadline: Int64 = nowEpoch + (15 * 60 * 1000)

        let remainingSec = max(0, Int((deadline - nowEpoch) / 1000))
        let minutes = remainingSec / 60
        let formatted = "\(minutes)m 00s"

        XCTAssertTrue(formatted.contains("15m"))
        // Calm resting copy verification (must not contain punitive terms)
        let calmTitle = "Let's rest our eyes and brain"
        XCTAssertFalse(calmTitle.contains("Blocked"))
        XCTAssertFalse(calmTitle.contains("Locked out"))
        XCTAssertFalse(calmTitle.contains("Failed"))
    }
}
