import SwiftData
import SwiftUI

struct RootView: View {
    private enum Tab: Hashable { case profile, history, workout, exercises, measure }

    @Query(filter: #Predicate<Workout> { $0.endedAt == nil && $0.deletedAt == nil })
    private var activeWorkouts: [Workout]
    @State private var tab = Tab.workout
    @Environment(\.modelContext) private var context
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    /// A CSV file opened or shared from another app, like Strong's export, until the user answers.
    @State private var incomingFile: URL?
    @State private var importMessage: String?

    var body: some View {
        TabView(selection: $tab) {
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person") }
                .tag(Tab.profile)
            HistoryView()
                .tabItem { Label("History", systemImage: "clock") }
                .tag(Tab.history)
            WorkoutTabView()
                .tabItem { Label("Workout", systemImage: "plus") }
                .tag(Tab.workout)
            ExercisesView()
                .tabItem { Label("Exercises", systemImage: "dumbbell") }
                .tag(Tab.exercises)
            MeasurementsView()
                .tabItem { Label("Measure", systemImage: "ruler") }
                .tag(Tab.measure)
        }
        // A workout started from another tab ("Perform again" in history) opens the Workout tab.
        .onChange(of: activeWorkouts.first?.id) { old, new in
            if old == nil, new != nil { tab = .workout }
        }
        .onOpenURL { url in
            if url.isFileURL { incomingFile = url }
        }
        .alert("Import workouts?", isPresented: Binding(get: { incomingFile != nil }, set: { if !$0 { incomingFile = nil } })) {
            Button("Import") {
                if let url = incomingFile { importMessage = CsvService.importFile(at: url, defaultUnit: weightUnit, in: context) }
                incomingFile = nil
            }
            Button("Cancel", role: .cancel) { incomingFile = nil }
        } message: {
            Text("GymBuddy will add the workouts in this file, such as a Strong export, to your history. Workouts already here are skipped.")
        }
        .alert(importMessage ?? "", isPresented: Binding(get: { importMessage != nil }, set: { if !$0 { importMessage = nil } })) {
            Button("OK") { importMessage = nil }
        }
    }
}
