import Foundation

/// How to do a built-in exercise, step by step. Read straight from exercises.json rather than SwiftData:
/// instructions never change per user and don't need to sync.
enum ExerciseInstructions {
    private struct File: Decodable {
        struct Entry: Decodable {
            let id: String
            let instructions: [String]?
            let instructionsVi: [String]?
        }
        let exercises: [Entry]
    }

    private static let byId: [String: (steps: [String], stepsVi: [String])] = {
        guard let url = Bundle.main.url(forResource: "exercises", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data)
        else { return [:] }
        var result: [String: (steps: [String], stepsVi: [String])] = [:]
        for entry in file.exercises {
            guard let steps = entry.instructions, !steps.isEmpty else { continue }
            result[entry.id] = (steps, entry.instructionsVi ?? [])
        }
        return result
    }()

    /// Steps in the app's current language; empty for custom exercises.
    static func steps(for exerciseId: String) -> [String] {
        guard let entry = byId[exerciseId] else { return [] }
        if Bundle.main.preferredLocalizations.first == "vi", !entry.stepsVi.isEmpty { return entry.stepsVi }
        return entry.steps
    }
}
