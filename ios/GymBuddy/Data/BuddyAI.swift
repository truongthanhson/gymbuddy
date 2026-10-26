import Foundation
import SwiftData

/// Monday to Sunday in local time; `to` is the next Monday's start.
struct ReportWeek: Sendable {
    let from: Date
    let to: Date

    /// "2026-10-19", the report's key on the server, as on Android.
    var mondayKey: String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: from)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
}

struct WeeklyReport: Sendable {
    let week: ReportWeek
    let text: String
}

/// The last full week before the one `now` is in.
func lastFullWeek(_ now: Date, calendar base: Calendar = .current) -> ReportWeek {
    var calendar = base
    calendar.firstWeekday = 2
    let thisMonday = calendar.dateInterval(of: .weekOfYear, for: now)?.start ?? calendar.startOfDay(for: now)
    let monday = calendar.date(byAdding: .day, value: -7, to: thisMonday) ?? thisMonday
    return ReportWeek(from: monday, to: thisMonday)
}

/// Buddy AI: builds the training summary and asks the coach function.
@MainActor
enum BuddyAI {
    static var isAvailable: Bool { CoachAPI.isAvailable }

    static var vietnamese: Bool { Bundle.main.preferredLocalizations.first?.hasPrefix("vi") == true }

    static var locale: String { vietnamese ? "vi" : "en" }

    static var plan: CoachPlan {
        let pro = ProStore.shared
        if !pro.isPro { return .free }
        return pro.isLifetimeOnly ? .lifetime : .pro
    }

    static var weightUnit: WeightUnit {
        WeightUnit(rawValue: UserDefaults.standard.string(forKey: SettingsKey.weightUnit) ?? "") ?? .kg
    }

    /// The summary Buddy reads, plus whatever the task adds.
    static func context(
        in modelContext: ModelContext,
        now: Date = .now,
        extra: (CoachContext, inout [String: Any]) -> Void = { _, _ in }
    ) -> [String: Any] {
        let workouts = (try? modelContext.fetch(FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt != nil && $0.deletedAt == nil }))) ?? []
        let exercises = (try? modelContext.fetch(FetchDescriptor<Exercise>())) ?? []
        let byId = Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let measurements = (try? modelContext.fetch(FetchDescriptor<BodyMeasurement>())) ?? []
        let settings = BuddySettings.current
        let snapshot = CoachService.snapshot(workouts: workouts, exercises: byId, goalPerWeek: settings.daysPerWeek, now: now)
        let builder = CoachContext(workouts: workouts, exercises: byId, snapshot: snapshot, unit: weightUnit, vietnamese: vietnamese)
        var out = builder.build(profile: settings.profile, bodyWeights: measurements, now: now)
        extra(builder, &out)
        return out
    }

    static func chat(_ message: String, in modelContext: ModelContext, onText: (String) -> Void) async throws {
        let ctx = context(in: modelContext)
        try await CoachAPI.chat(plan: plan, settings: .current, locale: locale, context: ctx, message: message, onText: onText)
    }

    /// Buddy's note on a finished workout; cached on the server per workout.
    static func workoutNote(_ workout: Workout, in modelContext: ModelContext) async throws -> String {
        let ctx = context(in: modelContext) { builder, out in out["workout"] = builder.workout(workout) }
        let result = try await CoachAPI.report(
            kind: "summary", plan: plan, settings: .current, locale: locale, context: ctx, ref: workout.id.uuidString.lowercased()
        )
        return result["text"] as? String ?? ""
    }

    /// Buddy's report on the last full week (Monday to Sunday); cached on the server per week.
    static func weeklyReport(in modelContext: ModelContext, now: Date = .now) async throws -> WeeklyReport {
        let week = lastFullWeek(now)
        let ctx = context(in: modelContext, now: now) { builder, out in out["week"] = builder.week(from: week.from, to: week.to) }
        let result = try await CoachAPI.report(
            kind: "weekly", plan: plan, settings: .current, locale: locale, context: ctx, ref: week.mondayKey
        )
        return WeeklyReport(week: week, text: result["text"] as? String ?? "")
    }

    /// All exercises Buddy may use when designing a routine.
    static func library(in modelContext: ModelContext) -> [[String: Any]] {
        exerciseLibrary((try? modelContext.fetch(FetchDescriptor<Exercise>())) ?? [], vietnamese: vietnamese)
    }
}
