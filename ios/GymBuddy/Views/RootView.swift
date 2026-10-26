import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            HistoryView()
                .tabItem { Label("History", systemImage: "clock") }
            WorkoutTabView()
                .tabItem { Label("Workout", systemImage: "plus") }
            ExercisesView()
                .tabItem { Label("Exercises", systemImage: "dumbbell") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
