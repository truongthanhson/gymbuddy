import SwiftData
import SwiftUI
import UserNotifications

@main
struct GymBuddyApp: App {
    let container: ModelContainer
    private let notificationDelegate = NotificationDelegate()

    init() {
        do {
            container = try ModelContainer(for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self)
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
        ExerciseSeeder.seedIfNeeded(context: container.mainContext)
        UNUserNotificationCenter.current().delegate = notificationDelegate
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }
}
