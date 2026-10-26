import SwiftData
import SwiftUI

struct WorkoutTabView: View {
    @Environment(\.modelContext) private var context
    @Query(
        filter: #Predicate<Workout> { $0.endedAt == nil && $0.deletedAt == nil },
        sort: \Workout.startedAt,
        order: .reverse
    )
    private var activeWorkouts: [Workout]

    var body: some View {
        NavigationStack {
            if let workout = activeWorkouts.first {
                ActiveWorkoutView(workout: workout)
            } else {
                List {
                    Section("Quick start") {
                        Button("Start an empty workout") {
                            RestTimer.requestAuthorization()
                            WorkoutService.startEmptyWorkout(
                                name: String(localized: "Workout"),
                                in: context
                            )
                        }
                    }
                }
                .navigationTitle("Workout")
            }
        }
    }
}
