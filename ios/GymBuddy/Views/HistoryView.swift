import SwiftData
import SwiftUI

struct HistoryView: View {
    @Query(
        filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil },
        sort: \Workout.startedAt,
        order: .reverse
    )
    private var workouts: [Workout]
    @Query private var exercises: [Exercise]

    var body: some View {
        NavigationStack {
            List(workouts) { workout in
                WorkoutCard(workout: workout, namesById: namesById)
            }
            .overlay {
                if workouts.isEmpty {
                    ContentUnavailableView(
                        "History",
                        systemImage: "clock",
                        description: Text("No workouts yet. Finished workouts show up here.")
                    )
                }
            }
            .navigationTitle("History")
        }
    }

    private var namesById: [String: Exercise] {
        Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }
}

private struct WorkoutCard: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    let workout: Workout
    let namesById: [String: Exercise]

    var body: some View {
        let items = workout.liveExercises
        let allSets = items.flatMap(\.liveSets).map(\.values)
        let duration = Int((workout.endedAt ?? workout.startedAt).timeIntervalSince(workout.startedAt))

        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: workout.name).font(.headline)
            Text(workout.startedAt, format: .dateTime.day().month().year().hour().minute())
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(verbatim: "\(formatElapsed(duration)) · \(weightUnit.format(volumeKg(allSets)))")
                .font(.caption)
                .foregroundStyle(.secondary)
            ForEach(items) { item in
                let sets = item.liveSets.map(\.values)
                HStack {
                    Text(verbatim: "\(sets.count) × \(namesById[item.exerciseId]?.displayName ?? item.exerciseId)")
                        .lineLimit(1)
                    Spacer()
                    if let index = bestSetIndex(sets) {
                        Text(verbatim: "\(weightUnit.format(sets[index].weightKg ?? 0)) × \(sets[index].reps ?? 0)")
                    }
                }
                .font(.subheadline)
            }
        }
        .padding(.vertical, 4)
    }
}
