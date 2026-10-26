import SwiftData
import SwiftUI

struct RootView: View {
    private enum Tab: Hashable { case profile, history, workout, exercises, measure }

    @Query(filter: #Predicate<Workout> { $0.endedAt == nil && $0.deletedAt == nil })
    private var activeWorkouts: [Workout]
    @State private var tab = Tab.workout

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
    }
}
