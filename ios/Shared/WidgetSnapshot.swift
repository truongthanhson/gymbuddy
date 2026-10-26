import Foundation

/// What the home screen widget shows, written by the app into the shared app group.
/// It keeps recent start dates rather than a count, so "this week" stays right after Monday comes around.
struct WidgetSnapshot: Codable, Equatable, Sendable {
    static let appGroup = "group.com.gymbuddy.app"
    static let key = "widgetSnapshot"

    /// Start dates of finished workouts from the last eight days.
    var recentStarts: [Date]
    var lastName: String?
    var lastStartedAt: Date?

    /// Finished workouts since Monday 00:00 of the week containing `now`.
    func workoutsThisWeek(now: Date = .now, calendar: Calendar = .current) -> Int {
        let weekStart = WidgetSnapshot.startOfWeek(now, calendar: calendar)
        return recentStarts.filter { $0 >= weekStart }.count
    }

    /// Monday 00:00 of the week containing `date`.
    static func startOfWeek(_ date: Date, calendar: Calendar = .current) -> Date {
        var monday = calendar
        monday.firstWeekday = 2
        return monday.dateInterval(of: .weekOfYear, for: date)?.start ?? calendar.startOfDay(for: date)
    }

    static func load() -> WidgetSnapshot? {
        guard let data = UserDefaults(suiteName: appGroup)?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: WidgetSnapshot.appGroup)?.set(data, forKey: WidgetSnapshot.key)
    }
}
