import SwiftData
import SwiftUI
import UserNotifications

@main
struct GymBuddyApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(SettingsKey.theme) private var theme: ThemeMode = .system
    let container: ModelContainer
    private let notificationDelegate = NotificationDelegate()

    init() {
        do {
            container = try ModelContainer(
                for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self,
                TemplateFolder.self, WorkoutTemplate.self, TemplateExercise.self, TemplateSet.self,
                BodyMeasurement.self
            )
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
        ExerciseSeeder.seedIfNeeded(context: container.mainContext)
        SyncService.shared.context = container.mainContext
        WatchBridge.shared.activate(context: container.mainContext)
        UNUserNotificationCenter.current().delegate = notificationDelegate
        ProStore.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(theme.colorScheme)
        }
        .modelContainer(container)
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active { SyncService.shared.requestSync() }
            // Pulled or imported workouts change the widget too.
            if phase == .background { WidgetSnapshotWriter.update(in: container.mainContext) }
        }
    }
}
