import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            ComingSoonView(title: "History")
                .tabItem { Label("History", systemImage: "clock") }
            ComingSoonView(title: "Workout")
                .tabItem { Label("Workout", systemImage: "plus") }
            ExercisesView()
                .tabItem { Label("Exercises", systemImage: "dumbbell") }
        }
    }
}
