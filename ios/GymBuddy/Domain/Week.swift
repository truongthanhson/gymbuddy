import Foundation

/// Finished workouts per week for the Profile chart, oldest week first; the last entry is the week of `now`.
/// Weeks start on Monday. Must match shared/test-vectors/workouts_per_week.json (and Android).
func workoutsPerWeek(_ starts: [Date], now: Date, weeks: Int, calendar: Calendar = .current) -> [Int] {
    let current = WidgetSnapshot.startOfWeek(now, calendar: calendar)
    var counts = Array(repeating: 0, count: weeks)
    for start in starts {
        let days = calendar.dateComponents([.day], from: WidgetSnapshot.startOfWeek(start, calendar: calendar), to: current).day ?? -1
        // Whole weeks back; floor so a later week (negative days) never lands on 0.
        let ago = Int((Double(days) / 7).rounded(.down))
        if (0..<weeks).contains(ago) { counts[weeks - 1 - ago] += 1 }
    }
    return counts
}
