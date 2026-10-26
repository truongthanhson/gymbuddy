import SwiftUI

/// A remote for the workout running on the iPhone.
struct WorkoutRemoteView: View {
    private let link = PhoneLink.shared

    var body: some View {
        NavigationStack {
            if let workout = link.state?.workout {
                WorkoutView(workout: workout, link: link)
            } else {
                Text(link.state == nil ? "Open GymBuddy on your iPhone" : "No workout in progress. Start one on your iPhone.")
                    .multilineTextAlignment(.center)
                    .padding()
            }
        }
    }
}

private struct WorkoutView: View {
    let workout: WatchWorkout
    let link: PhoneLink

    var body: some View {
        // Re-evaluates every second so the rest row goes away when the countdown ends.
        TimelineView(.periodic(from: .now, by: 1)) { context in
            List {
                Section {
                    VStack(alignment: .leading) {
                        Text(verbatim: workout.name).font(.headline)
                        Text(workout.startDate, style: .timer)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let end = workout.restEndDate, end > context.date {
                        HStack {
                            Text("Rest")
                            Spacer()
                            Text(timerInterval: context.date...end, countsDown: true)
                                .monospacedDigit()
                                .foregroundStyle(.orange)
                        }
                        Button("Skip rest") { link.skipRest() }
                    }
                }
                if let next = workout.nextSet {
                    Section {
                        VStack(alignment: .leading) {
                            Text(verbatim: workout.exercises[next.exerciseIndex].name).font(.headline)
                            Text("Set \(next.set.label)") + Text(verbatim: " · \(summary(next.set))")
                        }
                        Button {
                            link.completeSet(next.set.id)
                        } label: {
                            Label("Done", systemImage: "checkmark")
                        }
                        .tint(.green)
                    }
                } else {
                    Text("All sets done. Finish the workout on your iPhone.")
                }
                Section {
                    ForEach(workout.exercises.indices, id: \.self) { index in
                        let exercise = workout.exercises[index]
                        VStack(alignment: .leading) {
                            Text(verbatim: exercise.name).lineLimit(1)
                            Text("\(exercise.sets.filter(\.done).count)/\(exercise.sets.count) sets")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    /// "60 kg × 8"; values not filled in yet show last session's in brackets, or a dash.
    private func summary(_ set: WatchSet) -> String {
        let weight = set.weight.isEmpty ? set.hintWeight.map { "(\($0))" } ?? "—" : set.weight
        let reps = set.reps.isEmpty ? set.hintReps.map { "(\($0))" } ?? "—" : set.reps
        return "\(weight) \(set.unit) × \(reps)"
    }
}
