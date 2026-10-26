import SwiftData
import SwiftUI

/// The workout being logged. With `editing` it edits a finished workout from history instead:
/// no timer, rest timer or Finish button, and leaving goes through Save.
struct ActiveWorkoutView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query private var exercises: [Exercise]
    @Query(filter: #Predicate<Workout> { $0.endedAt == nil && $0.deletedAt == nil })
    private var activeWorkouts: [Workout]
    let workout: Workout
    var editing = false
    /// Called with the summary when a workout with ticked sets is finished.
    var onFinished: (WorkoutSummary) -> Void = { _ in }

    @State private var showPicker = false
    @State private var showFinish = false
    @State private var showCancel = false
    @State private var showRename = false
    @State private var newName = ""
    @State private var replacing: WorkoutExercise?
    @State private var showDelete = false
    @State private var showDuration = false
    @State private var durationText = ""
    @State private var templateSaved = false

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
            onFinished(WorkoutSummary.make(for: workout, names: namesById, in: context))
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
                if editing {
                    EditTimes(workout: workout) {
                        durationText = String(Int((workout.endedAt ?? workout.startedAt).timeIntervalSince(workout.startedAt)) / 60)
                        showDuration = true
                    }
                } else {
                    TimelineView(.periodic(from: .now, by: 1)) { timeline in
                        Text(verbatim: formatElapsed(Int(timeline.date.timeIntervalSince(workout.startedAt))))
                            .font(.title3.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
                NoteField(note: workout.note, placeholder: "Workout note") {
                    WorkoutService.setNote($0, for: workout, in: context)
                }
            }
            let items = workout.liveExercises
            let groups = items.map(\.supersetGroup)
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                ExerciseSection(
                    item: item,
                    exercise: namesById[item.exerciseId],
                    workout: workout,
                    editing: editing,
                    index: index,
                    groups: groups,
                    onReplace: { replacing = item }
                )
            }
            Section {
                Button("Add exercises") { showPicker = true }
                if !editing {
                    Button("Cancel workout", role: .destructive) { showCancel = true }
                }
            } footer: {
                if editing {
                    Text("Changes are saved as you go. Sets that are not ticked are removed when you tap Save.")
                }
            }
        }
        .appBackground()
        .safeAreaInset(edge: .bottom) {
            if !editing, let state = RestTimer.shared.state {
                RestTimerBar(state: state)
            }
        }
        .navigationTitle(workout.name)
        .navigationBarBackButtonHidden(editing)
        .toolbar {
            if editing {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Save") { saveEdits() }
                        .buttonStyle(.borderedProminent)
                }
                ToolbarItem(placement: .topBarTrailing) { editMenu }
            } else {
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
        }
        .alert("Delete workout?", isPresented: $showDelete) {
            Button("Delete", role: .destructive) {
                WorkoutService.discard(workout, in: context)
                SyncService.shared.requestSync()
                WidgetSnapshotWriter.update(in: context)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This workout will be removed from your history.")
        }
        .alert("Duration (minutes)", isPresented: $showDuration) {
            TextField("Duration (minutes)", text: $durationText)
                .keyboardType(.numberPad)
            Button("OK") {
                if let minutes = Int(durationText), minutes > 0 {
                    WorkoutService.setTimes(
                        of: workout,
                        startedAt: workout.startedAt,
                        endedAt: workout.startedAt.addingTimeInterval(TimeInterval(minutes * 60)),
                        in: context
                    )
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Saved as a template", isPresented: $templateSaved) {
            Button("OK") {}
        }
        .sheet(isPresented: $showPicker) {
            ExercisePickerView { ids in
                WorkoutService.addExercises(ids, to: workout, in: context)
            }
        }
        .sheet(item: $replacing) { item in
            ExercisePickerView(single: true) { ids in
                if let id = ids.first { WorkoutService.replace(item, with: id, in: context) }
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

extension ActiveWorkoutView {
    private var editMenu: some View {
        Menu {
            Button("Rename", systemImage: "pencil") {
                newName = workout.name
                showRename = true
            }
            ShareLink(
                item: WorkoutSummary.shareText(
                    name: workout.name,
                    startedAt: workout.startedAt,
                    exercises: WorkoutSummary.shareExercises(of: workout, names: namesById)
                )
            ) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            Button("Save as a new template", systemImage: "doc.on.doc") {
                TemplateService.createTemplate(from: workout, in: context)
                templateSaved = true
            }
            if activeWorkouts.isEmpty {
                Button("Perform again", systemImage: "arrow.clockwise") {
                    WorkoutService.finishEditing(workout, in: context)
                    WorkoutService.repeatWorkout(workout, in: context)
                    dismiss()
                }
            }
            Button("Delete workout", systemImage: "trash", role: .destructive) { showDelete = true }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }

    private func saveEdits() {
        WorkoutService.finishEditing(workout, in: context)
        SyncService.shared.requestSync()
        WidgetSnapshotWriter.update(in: context)
        dismiss()
    }
}

/// Date and duration of a finished workout; the date is edited in place, the duration through `onEditDuration`.
private struct EditTimes: View {
    @Environment(\.modelContext) private var context
    let workout: Workout
    let onEditDuration: () -> Void

    var body: some View {
        let duration = (workout.endedAt ?? workout.startedAt).timeIntervalSince(workout.startedAt)
        DatePicker(
            "Date",
            selection: Binding(
                get: { workout.startedAt },
                set: { start in
                    WorkoutService.setTimes(of: workout, startedAt: start, endedAt: start.addingTimeInterval(duration), in: context)
                }
            )
        )
        Button(action: onEditDuration) {
            LabeledContent("Duration", value: formatElapsed(Int(duration)))
        }
        .foregroundStyle(.primary)
    }
}

private struct ExerciseSection: View {
    @Environment(\.modelContext) private var context
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @AppStorage(SettingsKey.defaultRestSeconds) private var appDefaultRest = defaultRestSeconds
    let item: WorkoutExercise
    let exercise: Exercise?
    let workout: Workout
    let editing: Bool
    let index: Int
    let groups: [Int?]
    let onReplace: () -> Void
    @State private var noteOpen = false
    // Looked up once per exercise, not on every redraw: typing a weight redraws the section.
    @State private var previous: [WorkoutSet] = []
    @State private var recordSets: [SetValues] = []

    private var group: Int? { groups[index] }
    private var isLast: Bool { index == groups.count - 1 }

    private var title: String { exercise?.displayName ?? item.exerciseId }

    private var canAddWarmup: Bool {
        let sets = item.liveSets
        return setFields(category: exercise?.category ?? "").contains(.weight)
            && sets.contains { $0.setType == .normal && $0.weightKg != nil }
            && !sets.contains { $0.setType == .warmup }
    }

    /// Strong-style warm-up sets before the heaviest normal set.
    private func addWarmupSets() {
        guard let working = item.liveSets.filter({ $0.setType == .normal }).compactMap(\.weightKg).max() else { return }
        let sets = warmupSets(working: weightUnit.display(working), bar: weightUnit.bar(), plates: weightUnit.plates())
        WorkoutService.insertWarmupSets(sets.map { (weightUnit.toKg($0.weight), $0.reps) }, into: item, in: context)
    }

    private var restSeconds: Int {
        resolveRestSeconds(
            workoutExerciseRest: item.restSeconds,
            exerciseDefault: exercise?.defaultRestSeconds,
            appDefault: appDefaultRest
        )
    }

    var body: some View {
        // Warm-up sets never count as records.
        let records = liveRecords(
            previous: recordSets,
            current: item.liveSets.map {
                SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed && $0.setType != .warmup)
            }
        )
        let sets = item.liveSets
        let labels = watchSetLabels(sets.map(\.setTypeRaw))
        Section {
            if !editing, let exercise {
                BuddyTargetHint(exerciseId: item.exerciseId, bodyPart: exercise.bodyPart, category: exercise.category)
            }
            if item.note != nil || noteOpen {
                NoteField(note: item.note, placeholder: "Note") {
                    // Keep the field while the user clears it.
                    noteOpen = true
                    WorkoutService.setNote($0, for: item, in: context)
                }
            }
            let category = exercise?.category ?? ""
            let fields = setFields(category: category)
            SetHeader(unit: weightUnit, fields: fields, assisted: category == ExerciseCategory.assistedBodyweight.rawValue)
            ForEach(Array(sets.enumerated()), id: \.element.id) { setIndex, set in
                let previousSet = previous.indices.contains(setIndex) ? previous[setIndex] : nil
                SetRow(label: labels[setIndex], set: set, previous: previousSet, records: records[setIndex], unit: weightUnit, fields: fields) {
                    let wasCompleted = set.completed
                    WorkoutService.toggle(set, previous: previousSet, in: context)
                    if !wasCompleted, !editing {
                        // In a superset only the last exercise starts the rest timer.
                        if startsRest(groups: groups, index: index) {
                            RestTimer.shared.start(seconds: restSeconds, exerciseName: title)
                        }
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
                if let group {
                    // Exercises of one superset share a coloured bar, like Strong.
                    RoundedRectangle(cornerRadius: 2)
                        .fill(supersetColor(group))
                        .frame(width: 4, height: 28)
                }
                VStack(alignment: .leading, spacing: 0) {
                    if let group {
                        Text("Superset").font(.caption2).foregroundStyle(supersetColor(group)).textCase(nil)
                    }
                    Text(verbatim: title)
                        .font(.headline)
                        .foregroundStyle(Color.accentColor)
                        .textCase(nil)
                }
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
                    if canAddWarmup {
                        Button {
                            addWarmupSets()
                        } label: {
                            Label("Add warm-up sets", systemImage: "flame")
                        }
                    }
                    if item.note == nil && !noteOpen {
                        Button {
                            noteOpen = true
                        } label: {
                            Label("Add note", systemImage: "note.text")
                        }
                    }
                    if index > 0 {
                        Button {
                            WorkoutService.move(item, by: -1, in: workout, context: context)
                        } label: {
                            Label("Move up", systemImage: "arrow.up")
                        }
                    }
                    if !isLast {
                        Button {
                            WorkoutService.move(item, by: 1, in: workout, context: context)
                        } label: {
                            Label("Move down", systemImage: "arrow.down")
                        }
                    }
                    if !isLast && (group == nil || groups[index + 1] != group) {
                        Button {
                            WorkoutService.updateSupersets(in: workout, context: context) { supersetWithNext(groups: $0, index: index) }
                        } label: {
                            Label("Superset with next exercise", systemImage: "link")
                        }
                    }
                    if group != nil {
                        Button {
                            WorkoutService.updateSupersets(in: workout, context: context) { removeFromSuperset(groups: $0, index: index) }
                        } label: {
                            Label("Remove from superset", systemImage: "link.badge.plus")
                        }
                    }
                    Button(action: onReplace) {
                        Label("Replace exercise", systemImage: "arrow.triangle.2.circlepath")
                    }
                    Button("Remove exercise", role: .destructive) {
                        WorkoutService.remove(item, in: context)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .task(id: "\(item.exerciseId)|\(workout.startedAt.timeIntervalSince1970)") {
            // In an edited workout, "Previous" is the session before that one.
            previous = WorkoutService.previousSets(for: item.exerciseId, before: workout.startedAt, in: context)
            recordSets = recordHolders(WorkoutService.historySets(for: item.exerciseId, before: workout.startedAt, in: context))
        }
    }
}

private func supersetColor(_ group: Int) -> Color {
    [Color.orange, .purple, .teal, .pink][((group % 4) + 4) % 4]
}

private enum Column {
    static let set: CGFloat = 32
    static let value: CGFloat = 64
    static let check: CGFloat = 36
}

private struct SetHeader: View {
    let unit: WeightUnit
    let fields: [SetField]
    let assisted: Bool

    var body: some View {
        HStack {
            Text("Set").frame(width: Column.set)
            Text("Previous").frame(maxWidth: .infinity)
            ForEach(fields, id: \.self) { field in
                Group {
                    switch field {
                    // Assistance makes the lift lighter, so Strong shows it as a negative weight.
                    case .weight: Text(verbatim: assisted ? "-\(unit.symbol)" : unit.symbol)
                    case .reps: Text("Reps")
                    case .distance: Text(verbatim: unit.distanceUnit.symbol)
                    case .duration: Text("Time")
                    }
                }
                .frame(width: Column.value)
            }
            Image(systemName: "checkmark").frame(width: Column.check)
        }
        .font(.caption.bold())
        .foregroundStyle(.secondary)
    }
}

/// Strong-style medal on a set that beat a personal record, naming which ones.
private struct RecordBadge: View {
    let records: Set<RecordKind>

    var body: some View {
        let names = RecordKind.allCases.filter(records.contains).map { String(localized: name($0)) }
        Label {
            Text(verbatim: names.joined(separator: " · "))
        } icon: {
            Image(systemName: "trophy.fill")
        }
        .labelStyle(.titleAndIcon)
        .font(.caption2.weight(.semibold))
        .foregroundStyle(Palette.gold)
        .accessibilityLabel("Personal record")
    }

    private func name(_ kind: RecordKind) -> LocalizedStringResource {
        switch kind {
        case .oneRepMax: "1RM"
        case .weight: "Weight"
        case .volume: "Volume"
        case .reps: "Reps"
        }
    }
}

private struct SetRow: View {
    @Environment(\.modelContext) private var context
    let label: String
    @Bindable var set: WorkoutSet
    let previous: WorkoutSet?
    var records: Set<RecordKind> = []
    let unit: WeightUnit
    let fields: [SetField]
    let onToggle: () -> Void

    var body: some View {
        HStack {
            SetLabel(label: label, set: set)
                .frame(width: Column.set)
            VStack(spacing: 2) {
                Text(verbatim: previous.map { previousLabel($0, unit: unit, fields: fields) } ?? "—")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if !records.isEmpty {
                    RecordBadge(records: records)
                }
            }
            .frame(maxWidth: .infinity)
            ForEach(fields, id: \.self) { field in
                Group {
                    switch field {
                    case .weight:
                        TextField(
                            previous?.weightKg.map { formatWeight(unit.display($0)) } ?? "",
                            value: $set.weightKg.weight(in: unit),
                            format: .number
                        )
                        .keyboardType(.decimalPad)
                    case .reps:
                        TextField(previous?.reps.map { String($0) } ?? "", value: $set.reps, format: .number)
                            .keyboardType(.numberPad)
                    case .distance:
                        TextField(
                            previous?.distanceM.map { formatWeight(unit.distanceUnit.display($0)) } ?? "",
                            value: $set.distanceM.distance(in: unit.distanceUnit),
                            format: .number
                        )
                        .keyboardType(.decimalPad)
                    case .duration:
                        DurationField(seconds: $set.durationSec, placeholder: previous?.durationSec.map(formatDuration) ?? "")
                    }
                }
                .multilineTextAlignment(.center)
                .font(.body.weight(.semibold).monospacedDigit())
                .textFieldStyle(.plain)
                .padding(.vertical, 6)
                .background(
                    set.completed ? Color.clear : Palette.raised,
                    in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                )
                .frame(width: Column.value)
            }
            Button(action: onToggle) {
                Image(systemName: "checkmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(set.completed ? Color.white : Palette.secondaryText)
                    .frame(width: 30, height: 30)
                    .background(set.completed ? Palette.success : Palette.raised, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
            .frame(width: Column.check)
            .sensoryFeedback(.success, trigger: set.completed) { _, done in done }
        }
        // Done sets turn green, like ticking them off on paper.
        .listRowBackground(set.completed ? Palette.success.opacity(0.15) : Palette.card)
        .animation(.easeOut(duration: 0.2), value: set.completed)
        .onChange(of: set.weightKg) { set.updatedAt = .now }
        .onChange(of: set.reps) { set.updatedAt = .now }
        .onChange(of: set.distanceM) { set.updatedAt = .now }
        .onChange(of: set.durationSec) { set.updatedAt = .now }
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

/// "60 kg × 8", "× 12", "5 km 25:00" or "1:00", depending on what the exercise logs.
private func previousLabel(_ set: WorkoutSet, unit: WeightUnit, fields: [SetField]) -> String {
    let parts: [String] = fields.compactMap { field in
        switch field {
        case .weight: set.weightKg.map(unit.format)
        case .reps: set.reps.map { "× \($0)" }
        case .distance: set.distanceM.map { "\(formatWeight(unit.distanceUnit.display($0))) \(unit.distanceUnit.symbol)" }
        case .duration: set.durationSec.map(formatDuration)
        }
    }
    return parts.isEmpty ? "—" : parts.joined(separator: " ")
}

/// A time typed as 1:30 or 90; keeps the user's text while typing and saves the parsed seconds.
private struct DurationField: View {
    @Binding var seconds: Int?
    let placeholder: String
    @State private var text = ""

    var body: some View {
        TextField(placeholder, text: $text)
            .keyboardType(.numbersAndPunctuation)
            .onAppear { text = seconds.map(formatDuration) ?? "" }
            .onChange(of: text) { _, newValue in
                let parsed = parseDuration(newValue)
                if parsed != seconds { seconds = parsed }
            }
            .onChange(of: seconds) { _, newValue in
                // Filled in elsewhere, e.g. from the previous session.
                if parseDuration(text) != newValue { text = newValue.map(formatDuration) ?? "" }
            }
    }
}

func restLabel(_ seconds: Int) -> String {
    seconds <= 0 ? String(localized: "Off") : formatElapsed(seconds)
}
