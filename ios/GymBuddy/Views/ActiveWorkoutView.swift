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

    private func finish(saveToTemplate: Bool) {
        RestTimer.shared.skip()
        let saveable = anyCompleted
        WorkoutService.finish(workout, in: context)
        defer {
            SyncService.shared.requestSync()
            WidgetSnapshotWriter.update(in: context)
        }
        if saveable, let endedAt = workout.endedAt {
            HealthService.saveWorkout(id: workout.id, name: workout.name, start: workout.startedAt, end: endedAt)
        }
        guard saveToTemplate, saveable else { return }
        if let templateId = workout.templateId, let template = TemplateService.template(id: templateId, in: context) {
            TemplateService.update(template, from: workout, in: context)
        } else {
            TemplateService.createTemplate(from: workout, in: context)
        }
    }

    var body: some View {
        List {
            Section {
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    Text(verbatim: formatElapsed(Int(timeline.date.timeIntervalSince(workout.startedAt))))
                        .font(.title3.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                NoteField(note: workout.note, placeholder: "Workout note") {
                    WorkoutService.setNote($0, for: workout, in: context)
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
        .confirmationDialog("Finish workout?", isPresented: $showFinish, titleVisibility: .visible) {
            Button("Finish") { finish(saveToTemplate: false) }
            if anyCompleted {
                if workout.templateId == nil {
                    Button("Finish and save as a new template") { finish(saveToTemplate: true) }
                } else {
                    Button("Finish and update the template") { finish(saveToTemplate: true) }
                }
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
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @AppStorage(SettingsKey.defaultRestSeconds) private var appDefaultRest = defaultRestSeconds
    let item: WorkoutExercise
    let exercise: Exercise?
    @State private var noteOpen = false

    private var title: String { exercise?.displayName ?? item.exerciseId }

    private var restSeconds: Int {
        resolveRestSeconds(
            workoutExerciseRest: item.restSeconds,
            exerciseDefault: exercise?.defaultRestSeconds,
            appDefault: appDefaultRest
        )
    }

    var body: some View {
        let previous = WorkoutService.previousSets(for: item.exerciseId, in: context)
        let sets = item.liveSets
        let labels = watchSetLabels(sets.map(\.setTypeRaw))
        Section {
            if item.note != nil || noteOpen {
                NoteField(note: item.note, placeholder: "Note") {
                    // Keep the field while the user clears it.
                    noteOpen = true
                    WorkoutService.setNote($0, for: item, in: context)
                }
            }
            SetHeader(unit: weightUnit)
            ForEach(Array(sets.enumerated()), id: \.element.id) { index, set in
                let previousSet = previous.indices.contains(index) ? previous[index] : nil
                SetRow(label: labels[index], set: set, previous: previousSet, unit: weightUnit) {
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
                    if item.note == nil && !noteOpen {
                        Button {
                            noteOpen = true
                        } label: {
                            Label("Add note", systemImage: "note.text")
                        }
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
    let unit: WeightUnit

    var body: some View {
        HStack {
            Text("Set").frame(width: Column.set)
            Text("Previous").frame(maxWidth: .infinity)
            Text(verbatim: unit.symbol).frame(width: Column.value)
            Text("Reps").frame(width: Column.value)
            Image(systemName: "checkmark").frame(width: Column.check)
        }
        .font(.caption.bold())
        .foregroundStyle(.secondary)
    }
}

private struct SetRow: View {
    @Environment(\.modelContext) private var context
    let label: String
    @Bindable var set: WorkoutSet
    let previous: WorkoutSet?
    let unit: WeightUnit
    let onToggle: () -> Void

    var body: some View {
        HStack {
            SetLabel(label: label, set: set)
                .frame(width: Column.set)
            Text(verbatim: previous.map { previousLabel($0, unit: unit) } ?? "—")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
            TextField(
                previous?.weightKg.map { formatWeight(unit.display($0)) } ?? "",
                value: $set.weightKg.weight(in: unit),
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

/// The set's number or W/D/F; tapping it changes the set type or the RPE.
private struct SetLabel: View {
    @Environment(\.modelContext) private var context
    let label: String
    let set: WorkoutSet

    private var color: Color {
        switch set.setType {
        case .warmup: .orange
        case .drop: .purple
        case .failure: .red
        case .normal: .primary
        }
    }

    var body: some View {
        Menu {
            ForEach(SetType.allCases, id: \.self) { type in
                Button {
                    WorkoutService.setType(type, for: set, in: context)
                } label: {
                    if type == set.setType {
                        Label(type.title, systemImage: "checkmark")
                    } else {
                        Text(verbatim: type.title)
                    }
                }
            }
            Menu("RPE") {
                Button("None") { WorkoutService.setRpe(nil, for: set, in: context) }
                ForEach(rpeOptions, id: \.self) { option in
                    Button {
                        WorkoutService.setRpe(option, for: set, in: context)
                    } label: {
                        if option == set.rpe {
                            Label(formatRpe(option), systemImage: "checkmark")
                        } else {
                            Text(verbatim: formatRpe(option))
                        }
                    }
                }
            }
        } label: {
            VStack(spacing: 0) {
                Text(verbatim: label).bold().foregroundStyle(color)
                if let rpe = set.rpe {
                    Text(verbatim: "@\(formatRpe(rpe))").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.borderless)
    }
}

/// A note that saves as the user types.
private struct NoteField: View {
    let note: String?
    let placeholder: LocalizedStringKey
    let onChange: (String) -> Void
    @State private var text = ""

    var body: some View {
        TextField(placeholder, text: $text, axis: .vertical)
            .font(.callout)
            .onAppear { text = note ?? "" }
            .onChange(of: text) { _, newValue in
                if newValue != (note ?? "") { onChange(newValue) }
            }
    }
}

private func previousLabel(_ set: WorkoutSet, unit: WeightUnit) -> String {
    switch (set.weightKg, set.reps) {
    case let (weight?, reps?): "\(unit.format(weight)) × \(reps)"
    case let (nil, reps?): "× \(reps)"
    case let (weight?, nil): unit.format(weight)
    default: "—"
    }
}

func restLabel(_ seconds: Int) -> String {
    seconds <= 0 ? String(localized: "Off") : formatElapsed(seconds)
}
