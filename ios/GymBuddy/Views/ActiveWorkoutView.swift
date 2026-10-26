import SwiftData
import SwiftUI

struct ActiveWorkoutView: View {
    @Environment(\.modelContext) private var context
    @Query private var exercises: [Exercise]
    let workout: Workout

    @State private var showPicker = false
    @State private var showFinish = false
    @State private var showCancel = false
    @State private var showRename = false
    @State private var newName = ""

    private var namesById: [String: Exercise] {
        Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private var anyCompleted: Bool {
        workout.liveExercises.contains { $0.liveSets.contains(where: \.completed) }
    }

    var body: some View {
        List {
            Section {
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    Text(verbatim: formatElapsed(Int(timeline.date.timeIntervalSince(workout.startedAt))))
                        .font(.title3.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            ForEach(workout.liveExercises) { item in
                ExerciseSection(item: item, exercise: namesById[item.exerciseId])
            }
            Section {
                Button("Add exercises") { showPicker = true }
                Button("Cancel workout", role: .destructive) { showCancel = true }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let state = RestTimer.shared.state {
                RestTimerBar(state: state)
            }
        }
        .navigationTitle(workout.name)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    newName = workout.name
                    showRename = true
                } label: {
                    Image(systemName: "pencil")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Finish") { showFinish = true }
                    .buttonStyle(.borderedProminent)
            }
        }
        .sheet(isPresented: $showPicker) {
            ExercisePickerView { ids in
                WorkoutService.addExercises(ids, to: workout, in: context)
            }
        }
        .alert("Finish workout?", isPresented: $showFinish) {
            Button("Finish") {
                RestTimer.shared.skip()
                WorkoutService.finish(workout, in: context)
            }
            Button("Keep going", role: .cancel) {}
        } message: {
            Text(anyCompleted
                 ? "Sets that are not ticked will be removed."
                 : "No sets are ticked yet, so this workout will be discarded.")
        }
        .alert("Cancel workout?", isPresented: $showCancel) {
            Button("Cancel workout", role: .destructive) {
                RestTimer.shared.skip()
                WorkoutService.discard(workout, in: context)
            }
            Button("Keep going", role: .cancel) {}
        } message: {
            Text("All logged sets in this workout will be lost.")
        }
        .alert("Workout name", isPresented: $showRename) {
            TextField("Workout name", text: $newName)
            Button("OK") {
                let name = newName.trimmingCharacters(in: .whitespaces)
                if !name.isEmpty { WorkoutService.rename(workout, to: name, in: context) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

private struct ExerciseSection: View {
    @Environment(\.modelContext) private var context
    let item: WorkoutExercise
    let exercise: Exercise?

    private var title: String { exercise?.displayName ?? item.exerciseId }

    private var restSeconds: Int {
        resolveRestSeconds(workoutExerciseRest: item.restSeconds, exerciseDefault: exercise?.defaultRestSeconds)
    }

    var body: some View {
        let previous = WorkoutService.previousSets(for: item.exerciseId, in: context)
        Section {
            SetHeader()
            ForEach(Array(item.liveSets.enumerated()), id: \.element.id) { index, set in
                let previousSet = previous.indices.contains(index) ? previous[index] : nil
                SetRow(index: index, set: set, previous: previousSet) {
                    let wasCompleted = set.completed
                    WorkoutService.toggle(set, previous: previousSet, in: context)
                    if !wasCompleted {
                        RestTimer.shared.start(seconds: restSeconds, exerciseName: title)
                    }
                }
                    .swipeActions(edge: .trailing) {
                        Button("Delete set", role: .destructive) {
                            WorkoutService.delete(set, in: context)
                        }
                    }
            }
            Button("Add set") { WorkoutService.addSet(to: item, in: context) }
        } header: {
            HStack {
                Text(verbatim: title)
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
                    .textCase(nil)
                Spacer()
                Menu {
                    Menu {
                        ForEach(restTimeOptions, id: \.self) { option in
                            Button {
                                WorkoutService.setRestSeconds(option, for: item, in: context)
                            } label: {
                                if option == restSeconds {
                                    Label(restLabel(option), systemImage: "checkmark")
                                } else {
                                    Text(verbatim: restLabel(option))
                                }
                            }
                        }
                    } label: {
                        Label("Rest timer", systemImage: "timer")
                    }
                    Button("Remove exercise", role: .destructive) {
                        WorkoutService.remove(item, in: context)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
    }
}

private enum Column {
    static let set: CGFloat = 32
    static let value: CGFloat = 64
    static let check: CGFloat = 36
}

private struct SetHeader: View {
    var body: some View {
        HStack {
            Text("Set").frame(width: Column.set)
            Text("Previous").frame(maxWidth: .infinity)
            Text("kg").frame(width: Column.value)
            Text("Reps").frame(width: Column.value)
            Image(systemName: "checkmark").frame(width: Column.check)
        }
        .font(.caption.bold())
        .foregroundStyle(.secondary)
    }
}

private struct SetRow: View {
    let index: Int
    @Bindable var set: WorkoutSet
    let previous: WorkoutSet?
    let onToggle: () -> Void

    var body: some View {
        HStack {
            Text(verbatim: "\(index + 1)").frame(width: Column.set)
            Text(verbatim: previous.map(previousLabel) ?? "—")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
            TextField(
                previous?.weightKg.map(formatWeight) ?? "",
                value: $set.weightKg,
                format: .number
            )
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.center)
            .textFieldStyle(.roundedBorder)
            .frame(width: Column.value)
            TextField(
                previous?.reps.map { String($0) } ?? "",
                value: $set.reps,
                format: .number
            )
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .textFieldStyle(.roundedBorder)
            .frame(width: Column.value)
            Button(action: onToggle) {
                Image(systemName: set.completed ? "checkmark.square.fill" : "square")
                    .font(.title3)
            }
            .buttonStyle(.plain)
            .foregroundStyle(set.completed ? Color.green : Color.secondary)
            .frame(width: Column.check)
        }
        .listRowBackground(set.completed ? Color.green.opacity(0.15) : nil)
        .onChange(of: set.weightKg) { set.updatedAt = .now }
        .onChange(of: set.reps) { set.updatedAt = .now }
    }
}

private func previousLabel(_ set: WorkoutSet) -> String {
    switch (set.weightKg, set.reps) {
    case let (weight?, reps?): "\(formatWeight(weight)) kg × \(reps)"
    case let (nil, reps?): "× \(reps)"
    case let (weight?, nil): "\(formatWeight(weight)) kg"
    default: "—"
    }
}

func restLabel(_ seconds: Int) -> String {
    seconds <= 0 ? String(localized: "Off") : formatElapsed(seconds)
}
