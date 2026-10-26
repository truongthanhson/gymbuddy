import Charts
import SwiftData
import SwiftUI

/// One finished workout's completed sets of this exercise.
private struct HistoryWorkout: Identifiable {
    var id: UUID
    var name: String
    var startedAt: Date
    var sets: [SetValues]
}

private enum DetailTab: Hashable { case about, history, charts, records }

private enum ChartMetric: String, CaseIterable, Identifiable {
    case oneRepMax, maxWeight, volume
    var id: String { rawValue }

    var label: LocalizedStringKey {
        switch self {
        case .oneRepMax: "Estimated 1RM"
        case .maxWeight: "Max weight"
        case .volume: "Volume"
        }
    }

    func value(_ point: SessionPoint) -> Double {
        switch self {
        case .oneRepMax: point.estimatedOneRepMax
        case .maxWeight: point.maxWeight
        case .volume: point.volumeKg
        }
    }
}

/// History, charts and personal records of one exercise, like Strong's exercise page.
struct ExerciseDetailView: View {
    let exercise: Exercise

    @Query private var items: [WorkoutExercise]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @Environment(\.dismiss) private var dismiss
    @State private var tab: DetailTab = .about
    @State private var metric: ChartMetric = .oneRepMax
    @State private var editing = false

    init(exercise: Exercise) {
        self.exercise = exercise
        let id = exercise.id
        _items = Query(filter: #Predicate<WorkoutExercise> { $0.exerciseId == id && $0.deletedAt == nil })
    }

    /// Newest first; an exercise added twice to a workout shows as one entry.
    private var history: [HistoryWorkout] {
        var byWorkout: [UUID: HistoryWorkout] = [:]
        for item in items {
            guard let workout = item.workout, workout.endedAt != nil, workout.deletedAt == nil else { continue }
            let sets = item.liveSets.filter(\.completed).map(\.values)
            guard !sets.isEmpty else { continue }
            byWorkout[workout.id, default: HistoryWorkout(id: workout.id, name: workout.name, startedAt: workout.startedAt, sets: [])]
                .sets.append(contentsOf: sets)
        }
        return byWorkout.values.sorted { $0.startedAt > $1.startedAt }
    }

    var body: some View {
        let history = history
        let progress = exerciseProgress(history.map { ExerciseSession(startedAt: $0.startedAt, sets: $0.sets) })
        VStack(spacing: 0) {
            Picker("", selection: $tab) {
                Text("About").tag(DetailTab.about)
                Text("History").tag(DetailTab.history)
                Text("Charts").tag(DetailTab.charts)
                Text("Records").tag(DetailTab.records)
            }
            .pickerStyle(.segmented)
            .padding()
            switch tab {
            case .about: aboutTab
            case .history: historyTab(history)
            case .charts:
                if ProStore.shared.isPro { chartsTab(progress.points) } else { ProLockedView(feature: .exerciseCharts) }
            case .records: recordsTab(progress.records)
            }
        }
        .navigationTitle(Text(verbatim: exercise.displayName))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if exercise.isCustom {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") { editing = true }
                }
            }
        }
        .sheet(isPresented: $editing) { ExerciseEditorView(exercise: exercise) }
        .onChange(of: exercise.deletedAt) { _, deletedAt in
            if deletedAt != nil { dismiss() }
        }
    }

    /// Body part, equipment and Strong-style step-by-step instructions.
    private var aboutTab: some View {
        let steps = ExerciseInstructions.steps(for: exercise.id)
        return List {
            Section {
                let bodyPart = String(localized: BodyPart(key: exercise.bodyPart).label)
                let category = ExerciseCategory(rawValue: exercise.category).map { String(localized: $0.label) } ?? exercise.category
                Text(verbatim: "\(bodyPart) · \(category)")
                    .foregroundStyle(.secondary)
            }
            Section("Instructions") {
                if steps.isEmpty {
                    Text("No instructions for this exercise.").foregroundStyle(.secondary)
                }
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .firstTextBaseline) {
                        Text(verbatim: "\(index + 1).").bold().frame(width: 24, alignment: .leading)
                        Text(verbatim: step)
                    }
                }
            }
        }
    }

    private func empty(_ text: LocalizedStringKey) -> some View {
        ContentUnavailableView(text, systemImage: "chart.xyaxis.line")
            .frame(maxHeight: .infinity)
    }

    @ViewBuilder
    private func historyTab(_ history: [HistoryWorkout]) -> some View {
        if history.isEmpty {
            empty("No completed sets for this exercise yet.")
        } else {
            List(history) { workout in
                Section {
                    let best = bestSetIndex(workout.sets)
                    ForEach(Array(workout.sets.enumerated()), id: \.offset) { index, set in
                        setRow(index: index, set: set, isBest: index == best)
                    }
                } header: {
                    VStack(alignment: .leading) {
                        Text(verbatim: workout.name)
                        Text(workout.startedAt, format: .dateTime.day().month().year())
                    }
                }
            }
        }
    }

    private func setRow(index: Int, set: SetValues, isBest: Bool) -> some View {
        HStack {
            Text(verbatim: "\(index + 1)")
                .foregroundStyle(.secondary)
                .frame(width: 28, alignment: .leading)
            Text(verbatim: setLabel(set))
                .fontWeight(isBest ? .bold : nil)
            Spacer()
            if let reps = set.reps, reps > 0 {
                let oneRepMax = estimatedOneRepMax(weightKg: set.weightKg ?? 0, reps: reps)
                Text("1RM \(formatWeight(weightUnit.display(oneRepMax)))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func setLabel(_ set: SetValues) -> String {
        switch (set.weightKg, set.reps) {
        case let (weight?, reps?): "\(weightUnit.format(weight)) × \(reps)"
        case let (nil, reps?): "× \(reps)"
        case let (weight?, nil): weightUnit.format(weight)
        case (nil, nil): "—"
        }
    }

    @ViewBuilder
    private func chartsTab(_ points: [SessionPoint]) -> some View {
        if points.count < 2 {
            empty("Finish this exercise in at least two workouts to see a chart.")
        } else {
            VStack(alignment: .leading, spacing: 16) {
                Picker("", selection: $metric) {
                    ForEach(ChartMetric.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                progressChart(points)
                    .frame(height: 240)
                Spacer()
            }
            .padding()
        }
    }

    private func progressChart(_ points: [SessionPoint]) -> some View {
        let unit = weightUnit
        let metric = metric
        return Chart(points, id: \.startedAt) { point in
            let value = unit.display(metric.value(point))
            LineMark(x: .value("Date", point.startedAt), y: .value("Value", value))
            PointMark(x: .value("Date", point.startedAt), y: .value("Value", value))
        }
        .chartYAxisLabel(unit.symbol)
    }

    @ViewBuilder
    private func recordsTab(_ records: ExerciseRecords) -> some View {
        if records.estimatedOneRepMax == nil {
            empty("No completed sets for this exercise yet.")
        } else {
            List {
                recordRow("Estimated 1RM", records.estimatedOneRepMax, weightUnit.format)
                recordRow("Max weight", records.maxWeight, weightUnit.format)
                recordRow("Best set volume", records.maxSetVolume, weightUnit.format)
                recordRow("Max reps", records.maxReps) { String(Int($0)) }
                recordRow("Best workout volume", records.maxSessionVolume, weightUnit.format)
            }
        }
    }

    @ViewBuilder
    private func recordRow(_ label: LocalizedStringKey, _ record: Record?, _ format: (Double) -> String) -> some View {
        if let record {
            HStack {
                VStack(alignment: .leading) {
                    Text(label)
                    Text(record.startedAt, format: .dateTime.day().month().year())
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(verbatim: format(record.value)).font(.headline)
            }
        }
    }
}
