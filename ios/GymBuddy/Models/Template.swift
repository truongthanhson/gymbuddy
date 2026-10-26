import Foundation
import SwiftData

@Model
final class TemplateFolder {
    @Attribute(.unique) var id: UUID
    var name: String
    var sortOrder: Int
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    init(id: UUID = UUID(), name: String, sortOrder: Int, now: Date = .now) {
        self.id = id
        self.name = name
        self.sortOrder = sortOrder
        self.createdAt = now
        self.updatedAt = now
    }
}

@Model
final class WorkoutTemplate {
    @Attribute(.unique) var id: UUID
    var folderId: UUID?
    var name: String
    var note: String?
    var sortOrder: Int
    var lastUsedAt: Date?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    @Relationship(deleteRule: .cascade, inverse: \TemplateExercise.template)
    var exercises: [TemplateExercise] = []

    init(id: UUID = UUID(), name: String, sortOrder: Int, now: Date = .now) {
        self.id = id
        self.name = name
        self.sortOrder = sortOrder
        self.createdAt = now
        self.updatedAt = now
    }

    var liveExercises: [TemplateExercise] {
        exercises.filter { $0.deletedAt == nil }.sorted { $0.sortOrder < $1.sortOrder }
    }
}

@Model
final class TemplateExercise {
    @Attribute(.unique) var id: UUID
    var exerciseId: String
    var sortOrder: Int
    var restSeconds: Int?
    var note: String?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var template: WorkoutTemplate?

    @Relationship(deleteRule: .cascade, inverse: \TemplateSet.templateExercise)
    var sets: [TemplateSet] = []

    init(id: UUID = UUID(), exerciseId: String, sortOrder: Int, now: Date = .now) {
        self.id = id
        self.exerciseId = exerciseId
        self.sortOrder = sortOrder
        self.createdAt = now
        self.updatedAt = now
    }

    var liveSets: [TemplateSet] {
        sets.filter { $0.deletedAt == nil }.sorted { $0.sortOrder < $1.sortOrder }
    }
}

@Model
final class TemplateSet {
    @Attribute(.unique) var id: UUID
    var sortOrder: Int
    var setTypeRaw: String
    var targetWeightKg: Double?
    var targetReps: Int?
    var targetDurationSec: Int?
    var targetDistanceM: Double?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var templateExercise: TemplateExercise?

    init(id: UUID = UUID(), sortOrder: Int, targetWeightKg: Double? = nil, targetReps: Int? = nil, now: Date = .now) {
        self.id = id
        self.sortOrder = sortOrder
        self.setTypeRaw = SetType.normal.rawValue
        self.targetWeightKg = targetWeightKg
        self.targetReps = targetReps
        self.createdAt = now
        self.updatedAt = now
    }
}
