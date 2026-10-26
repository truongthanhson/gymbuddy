import SwiftData
import SwiftUI

/// What the "workout complete" sheet shows: the `number`th finished workout, with `records` record-setting sets.
struct WorkoutSummary: Identifiable {
    let id = UUID()
    let name: String
    let startedAt: Date
    let durationSec: Int
    let volume: Double
    let number: Int
    let records: Int
    let exercises: [ShareExercise]
    /// For Buddy's targets for next time.
    let exerciseIds: [String]
    /// For Buddy AI's note.
    var workoutId: UUID?

    @MainActor
    static func make(for workout: Workout, names: [String: Exercise], in context: ModelContext) -> WorkoutSummary {
        let finished = FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt != nil && $0.deletedAt == nil })
        // Warm-up sets never count as records, as on the sets while logging.
        let records = workout.liveExercises.reduce(0) { total, exercise in
            let history = WorkoutService.historySets(for: exercise.exerciseId, before: workout.startedAt, in: context)
            let sets = exercise.liveSets.map {
                SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed && $0.setType != .warmup)
            }
            return total + liveRecords(previous: history, current: sets).filter { !$0.isEmpty }.count
        }
        let completed = workout.liveExercises.flatMap(\.liveSets).filter(\.completed)
        return WorkoutSummary(
            name: workout.name,
            startedAt: workout.startedAt,
            durationSec: Int((workout.endedAt ?? workout.startedAt).timeIntervalSince(workout.startedAt)),
            volume: volumeKg(completed.map { SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: true) }),
            number: (try? context.fetchCount(finished)) ?? 0,
            records: records,
            exercises: shareExercises(of: workout, names: names),
            exerciseIds: workout.liveExercises.map(\.exerciseId),
            workoutId: workout.id
        )
    }

    /// The workout's completed sets per exercise, for sharing.
    static func shareExercises(of workout: Workout, names: [String: Exercise]) -> [ShareExercise] {
        workout.liveExercises.map { item in
            ShareExercise(
                name: names[item.exerciseId]?.displayName ?? item.exerciseId,
                category: names[item.exerciseId]?.category ?? "",
                sets: item.liveSets.filter(\.completed).map {
                    ShareSet(type: $0.setType.rawValue, weightKg: $0.weightKg, reps: $0.reps, durationSec: $0.durationSec, distanceM: $0.distanceM)
                }
            )
        }
    }

    static func shareText(name: String, startedAt: Date, exercises: [ShareExercise]) -> String {
        let unit = WeightUnit(rawValue: UserDefaults.standard.string(forKey: SettingsKey.weightUnit) ?? "") ?? .kg
        return workoutShareText(
            title: name,
            subtitle: startedAt.formatted(date: .complete, time: .omitted),
            exercises: exercises,
            unit: unit
        )
    }
}

/// Strong's "workout complete" screen: the workout's number, time, volume and records, with sharing.
struct WorkoutSummaryView: View {
    let summary: WorkoutSummary
    @Environment(\.dismiss) private var dismiss
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(spacing: 8) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 48))
                            .foregroundStyle(.yellow)
                        Text("Workout complete!").font(.title2.bold())
                        Text("Congratulations! That was your workout #\(summary.number).")
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: summary.name).font(.headline)
                        Text(summary.startedAt, format: .dateTime.weekday(.wide).day().month().year())
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        stat("Duration", formatElapsed(summary.durationSec))
                        Spacer()
                        stat("Volume", weightUnit.format(summary.volume))
                        Spacer()
                        stat("Records", String(summary.records))
                    }
                }
                Section {
                    ForEach(Array(summary.exercises.filter { !$0.sets.isEmpty }.enumerated()), id: \.offset) { _, exercise in
                        Text(verbatim: "\(exercise.sets.count) × \(exercise.name)")
                    }
                }
                if let workoutId = summary.workoutId { BuddyAiNoteSection(workoutId: workoutId) }
                BuddyNextTimeSection(exerciseIds: summary.exerciseIds)
            }
            .navigationTitle("Summary")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    ShareLink(item: WorkoutSummary.shareText(name: summary.name, startedAt: summary.startedAt, exercises: summary.exercises)) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func stat(_ label: LocalizedStringKey, _ value: String) -> some View {
        VStack(alignment: .leading) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(verbatim: value).font(.headline)
        }
    }
}
