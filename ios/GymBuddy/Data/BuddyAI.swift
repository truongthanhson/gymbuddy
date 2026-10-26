import Foundation
import SwiftData

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

    /// All exercises Buddy may use when designing a routine.
    static func library(in modelContext: ModelContext) -> [[String: Any]] {
        exerciseLibrary((try? modelContext.fetch(FetchDescriptor<Exercise>())) ?? [], vietnamese: vietnamese)
    }
}
