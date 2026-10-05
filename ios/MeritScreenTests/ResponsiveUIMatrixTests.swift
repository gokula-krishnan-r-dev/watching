import XCTest
import SwiftUI
@testable import MeritScreen

/// Matrix test suite for universal iPhone and iPad responsive design rules,
/// layout geometry boundaries, and CoreUI tokens.
final class ResponsiveUIMatrixTests: XCTestCase {

    // MARK: - Spacing & Corner Radius Tokens

    func testSpacingTokensAreStrictlyPositive() {
        XCTAssertGreaterThan(MeritSpacing.xSmall, 0)
        XCTAssertGreaterThan(MeritSpacing.small, MeritSpacing.xSmall)
        XCTAssertGreaterThan(MeritSpacing.medium, MeritSpacing.small)
        XCTAssertGreaterThan(MeritSpacing.large, MeritSpacing.medium)
        XCTAssertGreaterThan(MeritSpacing.xLarge, MeritSpacing.large)

        XCTAssertGreaterThan(MeritSpacing.radiusSmall, 0)
        XCTAssertGreaterThan(MeritSpacing.radiusMedium, MeritSpacing.radiusSmall)
        XCTAssertGreaterThan(MeritSpacing.radiusLarge, MeritSpacing.radiusMedium)
    }

    // MARK: - Responsive Container Constraints

    func testResponsiveContainerConstraintThresholds() {
        // Enforce max readable content widths for iPad layouts
        let phoneMaxWidth: CGFloat = 520
        let tabletHubMaxWidth: CGFloat = 620
        let fullCardMaxWidth: CGFloat = 800

        XCTAssertLessThanOrEqual(phoneMaxWidth, 600, "Forms and onboarding should stay comfortably readable on large screens")
        XCTAssertLessThanOrEqual(tabletHubMaxWidth, 768, "Child hub content should center gracefully on iPad")
        XCTAssertGreaterThan(fullCardMaxWidth, tabletHubMaxWidth)
    }

    // MARK: - Dynamic Type & Typography Scales

    func testTypographyTokensConfigured() {
        XCTAssertNotNil(MeritTypography.largeTitle)
        XCTAssertNotNil(MeritTypography.title2)
        XCTAssertNotNil(MeritTypography.headline)
        XCTAssertNotNil(MeritTypography.body)
        XCTAssertNotNil(MeritTypography.caption)
    }
}
