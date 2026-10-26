import Foundation
import SwiftData
import UserNotifications

/// Buddy's local notifications: a nudge when the person falls behind their weekly goal,
/// and the Monday weekly report notice (Pro, Buddy AI on).
@MainActor
enum BuddyReminders {
    private static let trainId = "buddy.train"
    private static let weeklyId = "buddy.weekly"
    /// The due date of the last training reminder handed to the system, so each gap reminds once.
    private static let lastDueKey = "buddy.lastReminderDue"
    private static let hour = 9

    private static func buddy(_ value: String.LocalizationValue) -> String { String(localized: value, table: "Buddy") }

    /// Asks for permission when reminders are turned on; false when the person declines.
    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// The first 09:00 at or after `date`.
    static func nineOClock(onOrAfter date: Date, calendar: Calendar = .current) -> Date {
        var parts = calendar.dateComponents([.year, .month, .day], from: date)
        parts.hour = hour
        let sameDay = calendar.date(from: parts) ?? date
        return sameDay >= date ? sameDay : calendar.date(byAdding: .day, value: 1, to: sameDay) ?? date
    }

    /// Replaces Buddy's pending notifications to match the latest workouts and settings.
    static func reschedule(in modelContext: ModelContext, now: Date = .now) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [trainId, weeklyId])
        let settings = BuddySettings.current
        guard UserDefaults.standard.bool(forKey: BuddyKey.reminders) else { return }

        var latest = FetchDescriptor<Workout>(
            predicate: #Predicate { $0.endedAt != nil && $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
        )
        latest.fetchLimit = 1
        let last = (try? modelContext.fetch(latest))?.first?.startedAt
        if let due = reminderDueAt(lastWorkout: last, daysPerWeek: settings.daysPerWeek) {
            let defaults = UserDefaults.standard
            var fire = nineOClock(onOrAfter: due)
            let alreadyReminded = defaults.double(forKey: lastDueKey) == due.timeIntervalSince1970
            if fire <= now {
                fire = nineOClock(onOrAfter: now.addingTimeInterval(60))
            }
            if !(alreadyReminded && nineOClock(onOrAfter: due) <= now) {
                defaults.set(due.timeIntervalSince1970, forKey: lastDueKey)
                let content = UNMutableNotificationContent()
                content.title = buddy("Time to train?")
                content.body = buddy("Your goal is \(settings.daysPerWeek) workouts a week. Even a short session today keeps you on track.")
                content.sound = .default
                let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
                center.add(UNNotificationRequest(identifier: trainId, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)))
            }
        }

        let weekly = ProStore.shared.isPro && settings.aiConsent && BuddyAI.isAvailable && AuthService.shared.session != nil
        if weekly {
            let content = UNMutableNotificationContent()
            content.title = buddy("Your weekly report is ready")
            content.body = buddy("Buddy looked back at last week. Open GymBuddy to read it.")
            content.sound = .default
            var parts = DateComponents()
            parts.weekday = 2
            parts.hour = hour
            center.add(UNNotificationRequest(identifier: weeklyId, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: true)))
        }
    }
}
