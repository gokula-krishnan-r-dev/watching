import XCTest
@testable import MeritScreen

final class EventCollector: @unchecked Sendable {
    private var events: [AnalyticsEvent] = []
    private let lock = NSLock()

    func append(_ event: AnalyticsEvent) {
        lock.lock()
        defer { lock.unlock() }
        events.append(event)
    }

    var allEvents: [AnalyticsEvent] {
        lock.lock()
        defer { lock.unlock() }
        return events
    }

    var isEmpty: Bool {
        lock.lock()
        defer { lock.unlock() }
        return events.isEmpty
    }

    var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return events.count
    }
}

final class ChildSafeAnalyticsTests: XCTestCase {

    func testChildDeviceDropsParentRestrictedEvents() {
        let tracker = ChildSafeAnalyticsTracker()
        tracker.setDeviceRole("child")

        let collector = EventCollector()
        tracker.onEventTracked = { event in
            collector.append(event)
        }

        // Parent-restricted events should be dropped
        tracker.track(.reportsViewed)
        tracker.track(.parentSignedIn)
        tracker.track(.parentSignedOut)
        tracker.track(.roleSelected)

        XCTAssertTrue(collector.isEmpty, "Child device must drop parent-restricted events")

        // Child-allowed events should pass through
        tracker.track(.appOpen)
        tracker.track(.quizCompleted)
        tracker.track(.policySyncCompleted)
        tracker.track(.childDevicePaired)
        tracker.track(.childDeviceUnpaired)

        XCTAssertEqual(collector.count, 5)
        XCTAssertEqual(collector.allEvents, [
            .appOpen,
            .quizCompleted,
            .policySyncCompleted,
            .childDevicePaired,
            .childDeviceUnpaired
        ])
    }

    func testParentDeviceTracksAllEvents() {
        let tracker = ChildSafeAnalyticsTracker()
        tracker.setDeviceRole("parent")

        let collector = EventCollector()
        tracker.onEventTracked = { event in
            collector.append(event)
        }

        tracker.track(.reportsViewed)
        tracker.track(.parentSignedIn)
        tracker.track(.quizCompleted)

        XCTAssertEqual(collector.count, 3)
        XCTAssertTrue(collector.allEvents.contains(.reportsViewed))
        XCTAssertTrue(collector.allEvents.contains(.parentSignedIn))
        XCTAssertTrue(collector.allEvents.contains(.quizCompleted))
    }

    func testDynamicRoleSwitching() {
        let tracker = ChildSafeAnalyticsTracker()
        let collector = EventCollector()
        tracker.onEventTracked = { event in
            collector.append(event)
        }

        // Initially child
        tracker.setDeviceRole("child")
        tracker.track(.reportsViewed)
        XCTAssertTrue(collector.isEmpty)

        // Switch to parent
        tracker.setDeviceRole("parent")
        tracker.track(.reportsViewed)
        XCTAssertEqual(collector.count, 1)
        XCTAssertEqual(collector.allEvents.first, .reportsViewed)
    }
}
