import SwiftData
import SwiftUI

@main
struct GymBuddyApp: App {
    let container: ModelContainer

    init() {
        do {
            container = try ModelContainer(for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self)
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
        ExerciseSeeder.seedIfNeeded(context: container.mainContext)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }
}
