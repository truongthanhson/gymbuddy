import Foundation
import SwiftData

enum SetType: String, Codable, CaseIterable, Sendable {
    case warmup, normal, drop, failure
}

@Model
final class Workout {
    @Attribute(.unique) var id: UUID
    var name: String
    var note: String?
    var templateId: UUID?
    var startedAt: Date
    var endedAt: Date?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    @Relationship(deleteRule: .cascade, inverse: \WorkoutExercise.workout)
    var exercises: [WorkoutExercise] = []

    init(id: UUID = UUID(), name: String, startedAt: Date = .now) {
        self.id = id
        self.name = name
        self.startedAt = startedAt
        self.createdAt = startedAt
        self.updatedAt = startedAt
    }

    /// Live exercises in display order (soft-deleted rows are kept for sync).
    var liveExercises: [WorkoutExercise] {
        exercises.filter { $0.deletedAt == nil }.sorted { $0.sortOrder < $1.sortOrder }
    }
}

@Model
final class WorkoutExercise {
    @Attribute(.unique) var id: UUID
    var exerciseId: String
    var sortOrder: Int
    var restSeconds: Int?
    var note: String?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var workout: Workout?

    @Relationship(deleteRule: .cascade, inverse: \WorkoutSet.workoutExercise)
    var sets: [WorkoutSet] = []

    init(id: UUID = UUID(), exerciseId: String, sortOrder: Int, now: Date = .now) {
        self.id = id
        self.exerciseId = exerciseId
        self.sortOrder = sortOrder
        self.createdAt = now
        self.updatedAt = now
    }

    var liveSets: [WorkoutSet] {
        sets.filter { $0.deletedAt == nil }.sorted { $0.sortOrder < $1.sortOrder }
    }
}

@Model
final class WorkoutSet {
    @Attribute(.unique) var id: UUID
    var sortOrder: Int
    var setTypeRaw: String
    var weightKg: Double?
    var reps: Int?
    var durationSec: Int?
    var distanceM: Double?
    var rpe: Double?
    var completed: Bool
    var completedAt: Date?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var workoutExercise: WorkoutExercise?

    init(
        id: UUID = UUID(),
        sortOrder: Int,
        weightKg: Double? = nil,
        reps: Int? = nil,
        durationSec: Int? = nil,
        distanceM: Double? = nil,
        now: Date = .now
    ) {
        self.id = id
        self.sortOrder = sortOrder
        self.setTypeRaw = SetType.normal.rawValue
        self.weightKg = weightKg
        self.reps = reps
        self.durationSec = durationSec
        self.distanceM = distanceM
        self.completed = false
        self.createdAt = now
        self.updatedAt = now
    }

    var setType: SetType {
        get { SetType(rawValue: setTypeRaw) ?? .normal }
        set { setTypeRaw = newValue.rawValue }
    }

    var values: SetValues { SetValues(weightKg: weightKg, reps: reps, completed: completed) }
}
