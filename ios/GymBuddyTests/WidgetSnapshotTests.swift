import Foundation
import XCTest
@testable import GymBuddy

final class WidgetSnapshotTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")!
        return calendar
    }

    private func date(_ day: Int, _ hour: Int) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour))!
    }

    func testWeekStartsOnMonday() {
        // 2026-10-26 is a Monday.
        XCTAssertEqual(WidgetSnapshot.startOfWeek(date(26, 7), calendar: calendar), date(26, 0))
        XCTAssertEqual(WidgetSnapshot.startOfWeek(date(25, 12), calendar: calendar), date(19, 0))
    }

    func testCountsOnlyThisWeek() {
        let snapshot = WidgetSnapshot(recentStarts: [date(24, 9), date(26, 7), date(27, 18)], lastName: "Legs", lastStartedAt: date(27, 18))
        XCTAssertEqual(snapshot.workoutsThisWeek(now: date(28, 8), calendar: calendar), 2)
    }
}
