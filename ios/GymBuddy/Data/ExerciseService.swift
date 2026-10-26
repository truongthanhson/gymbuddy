import Foundation
import SwiftData

/// Custom exercises; built-in ones come from `ExerciseSeeder` and are read-only.
@MainActor
enum ExerciseService {
    @discardableResult
    static func createCustom(name: String, bodyPart: BodyPart, category: ExerciseCategory, in context: ModelContext) -> Exercise {
        let exercise = Exercise(
            id: UUID().uuidString.lowercased(),
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            category: category.rawValue,
            bodyPart: bodyPart.rawValue,
            isCustom: true
        )
        context.insert(exercise)
        save(context)
        return exercise
    }

    static func update(_ exercise: Exercise, name: String, bodyPart: BodyPart, category: ExerciseCategory, in context: ModelContext) {
        guard exercise.isCustom else { return }
        exercise.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        exercise.bodyPart = bodyPart.rawValue
        exercise.category = category.rawValue
        exercise.updatedAt = .now
        save(context)
    }

    /// Soft delete: past workouts still show the exercise.
    static func delete(_ exercise: Exercise, in context: ModelContext) {
        guard exercise.isCustom else { return }
        exercise.deletedAt = .now
        exercise.updatedAt = .now
        save(context)
    }

    private static func save(_ context: ModelContext) {
        do {
            try context.save()
        } catch {
            assertionFailure("Save failed: \(error)")
        }
    }
}
