import Foundation

/// A month of the history calendar: `leading` empty cells, then days 1 to `days`.
struct MonthGrid: Equatable {
    let leading: Int
    let days: Int
    var weeks: Int { (leading + days + 6) / 7 }
}

/// `firstDayOfWeek` is an ISO weekday, 1 = Monday to 7 = Sunday.
func monthGrid(year: Int, month: Int, firstDayOfWeek: Int) -> MonthGrid {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    let first = calendar.date(from: DateComponents(year: year, month: month, day: 1))!
    // Calendar weekdays run 1 = Sunday to 7 = Saturday.
    let isoWeekday = (calendar.component(.weekday, from: first) + 5) % 7 + 1
    let days = calendar.range(of: .day, in: .month, for: first)!.count
    return MonthGrid(leading: (isoWeekday - firstDayOfWeek + 7) % 7, days: days)
}

/// The user's first day of the week as an ISO weekday.
func isoFirstWeekday(_ calendar: Calendar = .current) -> Int {
    (calendar.firstWeekday + 5) % 7 + 1
}
