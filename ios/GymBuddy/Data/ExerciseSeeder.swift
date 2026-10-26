import Foundation
import SwiftData

struct SeedFile: Decodable {
    let version: Int
    let exercises: [SeedExercise]
}

struct SeedExercise: Decodable {
    let id: String
    let name: String
    let nameVi: String?
    let category: String
    let bodyPart: String
    let primaryMuscle: String?
    let secondaryMuscles: [String]
    let defaultRestSeconds: Int?
}

/// Loads the built-in exercise library from exercises.json into SwiftData.
enum ExerciseSeeder {
    @MainActor
    static func seedIfNeeded(context: ModelContext, bundle: Bundle = .main) {
        guard let url = bundle.url(forResource: "exercises", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let seed = try? JSONDecoder().decode(SeedFile.self, from: data)
        else {
            assertionFailure("exercises.json missing or invalid")
            return
        }

        let builtIn = FetchDescriptor<Exercise>(predicate: #Predicate { !$0.isCustom })
        let existing = (try? context.fetchCount(builtIn)) ?? 0
        guard existing < seed.exercises.count else { return }

        // Inserting a model with an existing unique id updates that row.
        for item in seed.exercises {
            context.insert(Exercise(
                id: item.id,
                name: item.name,
                nameVi: item.nameVi,
                category: item.category,
                bodyPart: item.bodyPart,
                primaryMuscle: item.primaryMuscle,
                secondaryMuscles: item.secondaryMuscles,
                defaultRestSeconds: item.defaultRestSeconds,
                isCustom: false
            ))
        }
        try? context.save()
    }
}
