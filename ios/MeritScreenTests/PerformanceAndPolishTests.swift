import XCTest
import UIKit
@testable import MeritScreen

final class PerformanceAndPolishTests: XCTestCase {

    private let baseSnapshot = SessionSnapshot(
        phase: .inBlock,
        activePackageOrBundleId: "com.apple.mobilesafari",
        activeAppId: "safari",
        blockDurationMinutes: 15,
        minutesAccruedInBlock: 2.0,
        cooldownMinutes: 15,
        dayKey: "2026-10-01",
        minutesUsedToday: 10
    )

    // MARK: - SessionPersistPolicy Tests

    func testSessionPersistPolicyPersistsWhenNothingPreviouslyWritten() {
        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: nil,
            next: baseSnapshot,
            nowElapsedMs: 1_000,
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertTrue(shouldPersist, "Must persist when nothing previously written")
    }

    func testSessionPersistPolicySkipsUnchangedTickInsideInterval() {
        var tickingSnapshot = baseSnapshot
        tickingSnapshot.minutesAccruedInBlock = 2.05
        tickingSnapshot.lastTickEpochMs = 2_000

        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: baseSnapshot,
            next: tickingSnapshot,
            nowElapsedMs: 10_000, // 10s elapsed (< 45s)
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertFalse(shouldPersist, "Should skip writing to disk on 1 Hz tick within the 45s interval")
    }

    func testSessionPersistPolicyPersistsAfterIntervalElapsed() {
        var tickingSnapshot = baseSnapshot
        tickingSnapshot.minutesAccruedInBlock = 2.75
        tickingSnapshot.lastTickEpochMs = 46_000

        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: baseSnapshot,
            next: tickingSnapshot,
            nowElapsedMs: 46_000, // 46s elapsed (>= 45s)
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertTrue(shouldPersist, "Must flush to disk once interval threshold is reached")
    }

    func testSessionPersistPolicyAlwaysPersistsPhaseChange() {
        var phaseChangedSnapshot = baseSnapshot
        phaseChangedSnapshot.phase = .quizDue

        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: baseSnapshot,
            next: phaseChangedSnapshot,
            nowElapsedMs: 2_000, // only 2s elapsed
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertTrue(shouldPersist, "Phase changes must flush immediately")
    }

    func testSessionPersistPolicyAlwaysPersistsDailyMinuteBoundary() {
        var minuteChangedSnapshot = baseSnapshot
        minuteChangedSnapshot.minutesUsedToday = 11

        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: baseSnapshot,
            next: minuteChangedSnapshot,
            nowElapsedMs: 2_000,
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertTrue(shouldPersist, "Minute rollover must flush immediately")
    }

    func testSessionPersistPolicyAlwaysPersistsActiveAppChange() {
        var appChangedSnapshot = baseSnapshot
        appChangedSnapshot.activeAppId = "youtube_kids"
        appChangedSnapshot.activePackageOrBundleId = "com.google.ios.youtubekids"

        let shouldPersist = SessionPersistPolicy.shouldPersist(
            previousPersisted: baseSnapshot,
            next: appChangedSnapshot,
            nowElapsedMs: 1_000,
            lastPersistElapsedMs: 0,
            intervalSeconds: 45
        )
        XCTAssertTrue(shouldPersist, "App change must flush immediately")
    }

    // MARK: - StartupPerformanceCoordinator Tests

    func testStartupPerformanceCoordinatorDeferredExecution() async {
        let coordinator = StartupPerformanceCoordinator()
        XCTAssertFalse(coordinator.hasRenderedFirstFrame)

        let expectation = expectation(description: "Deferred task executed")

        coordinator.deferUntilFirstFrameRendered {
            expectation.fulfill()
        }

        coordinator.markFirstFrameRendered()
        XCTAssertTrue(coordinator.hasRenderedFirstFrame)

        await fulfillment(of: [expectation], timeout: 2.0)
    }

    // MARK: - AppIconCache Tests

    func testAppIconCacheLifecycleAndMemoryPurge() {
        let iconCache = AppIconCache(countLimit: 10)
        let testImage = UIImage()

        // Cache miss
        XCTAssertNil(iconCache.image(forKey: "test_key"))

        // Cache hit
        iconCache.setImage(testImage, forKey: "test_key")
        XCTAssertNotNil(iconCache.image(forKey: "test_key"))

        // Explicit removal
        iconCache.removeImage(forKey: "test_key")
        XCTAssertNil(iconCache.image(forKey: "test_key"))

        // System memory warning notification purge
        iconCache.setImage(testImage, forKey: "purge_key")
        XCTAssertNotNil(iconCache.image(forKey: "purge_key"))

        NotificationCenter.default.post(name: UIApplication.didReceiveMemoryWarningNotification, object: nil)
        XCTAssertNil(iconCache.image(forKey: "purge_key"), "Must purge cached icons on memory warning")
    }

    // MARK: - LowPowerModeMonitor Tests

    @MainActor
    func testLowPowerModeMonitorStatus() {
        let monitor = LowPowerModeMonitor.shared
        let isLowPower = LowPowerModeMonitor.currentIsLowPower
        XCTAssertEqual(monitor.isLowPowerModeEnabled, isLowPower)
    }

    // MARK: - Reduce Motion Compliance Tests

    func testMeritMotionReduceMotionRespect() {
        // When Reduce Motion is enabled: animations must return nil (instant cut)
        XCTAssertNil(MeritMotion.interactive(reduceMotion: true))
        XCTAssertNil(MeritMotion.gentle(reduceMotion: true))

        // When Reduce Motion is disabled: standard animations returned
        XCTAssertNotNil(MeritMotion.interactive(reduceMotion: false))
        XCTAssertNotNil(MeritMotion.gentle(reduceMotion: false))
    }
}
