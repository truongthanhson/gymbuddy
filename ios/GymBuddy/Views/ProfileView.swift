import Charts
import SwiftData
import SwiftUI

/// Everything the Profile tab shows, worked out once in the background so the screen only draws.
struct ProfileSnapshot: Sendable {
    static let chartWeeks = 8
    static let heatmapWeeks = 18
    static let volumeWeeks = 12
    static let recentRecordCount = 5
    static let topExerciseCount = 5
    /// The periods the muscle groups card can show, in days.
    static let musclePeriods = [7, 30, 90]

    var totals: ProfileTotals
    var firstWorkout: Date?
    var streaks: WeekStreaks
    var weekCounts: [Int]
    /// Workouts per day from `heatmapStart`; `today` is the index of today.
    var heatmap: [Int]
    var heatmapStart: Date
    var today: Int
    var weeklyVolumeKg: [Double]
    var muscleGroups: [Int: [BodyPartCount]]
    var recordCount: Int
    var recentRecords: [PersonalRecord]
    var topExercises: [TopExercise]
    var habits: TrainingHabits
}

/// Works out the Profile tab's numbers. Years of history take a moment, so callers run it off the main thread.
func profileSnapshot(_ workouts: [StatsWorkout], bodyParts: [String: String], now: Date, calendar: Calendar = .current) -> ProfileSnapshot {
    let starts = workouts.map(\.startedAt)
    let start = heatmapStart(now: now, weeks: ProfileSnapshot.heatmapWeeks, calendar: calendar)
    let sets = workouts.flatMap { workout in
        workout.exercises.flatMap { exercise in
            exercise.sets.filter(\.completed).map {
                BodyPartSet(bodyPart: bodyParts[exercise.exerciseId] ?? "", startedAt: workout.startedAt, warmup: $0.warmup)
            }
        }
    }
    let records = personalRecords(workouts)
    return ProfileSnapshot(
        totals: profileTotals(workouts),
        firstWorkout: starts.min(),
        streaks: weekStreaks(starts, now: now, calendar: calendar),
        weekCounts: workoutsPerWeek(starts, now: now, weeks: ProfileSnapshot.chartWeeks, calendar: calendar),
        heatmap: workoutsPerDay(starts, now: now, weeks: ProfileSnapshot.heatmapWeeks, calendar: calendar),
        heatmapStart: start,
        today: calendar.dateComponents([.day], from: start, to: calendar.startOfDay(for: now)).day ?? 0,
        weeklyVolumeKg: weeklyVolume(workouts, now: now, weeks: ProfileSnapshot.volumeWeeks, calendar: calendar),
        muscleGroups: Dictionary(uniqueKeysWithValues: ProfileSnapshot.musclePeriods.map { days in
            (days, setsPerBodyPart(sets, since: calendar.date(byAdding: .day, value: -days, to: now) ?? now))
        }),
        recordCount: records.count,
        recentRecords: Array(records.prefix(ProfileSnapshot.recentRecordCount)),
        topExercises: topExercises(workouts, now: now, limit: ProfileSnapshot.topExerciseCount),
        habits: trainingHabits(workouts, now: now, calendar: calendar)
    )
}

/// Strong's Profile tab: who you are and how your training is going. Settings are behind the gear.
struct ProfileView: View {
    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil })
    private var workouts: [Workout]
    @Query private var exercises: [Exercise]
    @Query(filter: #Predicate<BodyMeasurement> { $0.kind == "weight" && $0.deletedAt == nil }, sort: \BodyMeasurement.measuredAt)
    private var bodyWeights: [BodyMeasurement]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var stats: ProfileSnapshot?
    @State private var grown = false
    @State private var selectedWeek: Date?
    @State private var selectedPart: String?
    @State private var muscleDays = 30

    /// Changes whenever a finished workout is added, edited or removed, so the stats are worked out again.
    private var statsKey: [Date] {
        [workouts.map(\.updatedAt).max() ?? .distantPast, Date(timeIntervalSince1970: Double(workouts.count))]
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: AuthService.shared.session?.email ?? String(localized: "GymBuddy"))
                            .font(.title2.bold())
                        if let first = stats?.firstWorkout {
                            Text("Training since \(first.formatted(date: .abbreviated, time: .omitted))")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section {
                    NavigationLink {
                        PaywallView()
                    } label: {
                        Label {
                            VStack(alignment: .leading) {
                                Text(verbatim: "GymBuddy Pro")
                                Text(ProStore.shared.isPro ? "Active" : "Unlimited routines, charts and all measurements")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "crown.fill").foregroundStyle(.tint)
                        }
                    }
                }
                if let stats {
                    freeSections(stats)
                    if ProStore.shared.isPro {
                        proSections(stats)
                    } else {
                        Section {
                            NavigationLink {
                                PaywallView()
                            } label: {
                                Label("Volume, records, top exercises, muscle groups and more stats come with GymBuddy Pro", systemImage: "lock.fill")
                            }
                        }
                    }
                }
            }
            .appBackground()
            .navigationTitle("Profile")
            .task(id: statsKey) { await refresh() }
            .onAppear {
                // Bars grow from zero each time the tab appears.
                grown = false
                withAnimation(.spring(duration: 0.8, bounce: 0.3).delay(0.1)) { grown = true }
            }
            .toolbar {
                NavigationLink {
                    SettingsView()
                } label: {
                    Label("Settings", systemImage: "gearshape")
                }
            }
        }
    }

    /// Copies the workouts into plain values here, then does the counting off the main thread.
    private func refresh() async {
        let input = workouts.map { workout in
            StatsWorkout(
                startedAt: workout.startedAt,
                endedAt: workout.endedAt ?? workout.startedAt,
                exercises: workout.liveExercises.map { exercise in
                    StatsExercise(exerciseId: exercise.exerciseId, sets: exercise.liveSets.map {
                        StatsSet(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed, warmup: $0.setType == .warmup)
                    })
                }
            )
        }
        let parts = Dictionary(exercises.map { ($0.id, $0.bodyPart) }, uniquingKeysWith: { first, _ in first })
        let result = await Task.detached(priority: .userInitiated) {
            profileSnapshot(input, bodyParts: parts, now: .now)
        }.value
        if !Task.isCancelled { stats = result }
    }

    private var exerciseNames: [String: String] {
        Dictionary(exercises.map { ($0.id, $0.displayName) }, uniquingKeysWith: { first, _ in first })
    }

    @ViewBuilder
    private func freeSections(_ stats: ProfileSnapshot) -> some View {
        Section {
            Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 14) {
                GridRow {
                    StatCell(value: stats.totals.workouts.formatted(), label: "Workouts")
                    StatCell(value: String(localized: "\(stats.totals.durationSeconds / 3600) h"), label: "Total time")
                }
                GridRow {
                    StatCell(
                        value: "\(weightUnit.display(stats.totals.volumeKg).formatted(.number.notation(.compactName))) \(weightUnit.symbol)",
                        label: "Total volume"
                    )
                    StatCell(value: stats.totals.sets.formatted(.number.notation(.compactName)), label: "Sets done")
                }
            }
            .padding(.vertical, 4)
        }
        Section("Consistency") {
            Grid(alignment: .leading, horizontalSpacing: 16) {
                GridRow {
                    StatCell(value: String(localized: "\(stats.streaks.current) weeks"), label: "Current streak")
                    StatCell(value: String(localized: "\(stats.streaks.best) weeks"), label: "Longest streak")
                }
            }
            .padding(.vertical, 4)
            VStack(alignment: .leading, spacing: 8) {
                Heatmap(days: stats.heatmap, today: stats.today)
                Text("Last \(ProfileSnapshot.heatmapWeeks) weeks, one square per day")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
        Section("Workouts per week") {
            workoutsChart(stats.weekCounts).frame(height: 180)
        }
    }

    @ViewBuilder
    private func proSections(_ stats: ProfileSnapshot) -> some View {
        Section("Volume per week") {
            volumeChart(stats.weeklyVolumeKg).frame(height: 180)
        }
        Section("Sets per muscle group") {
            Picker("Period", selection: $muscleDays) {
                ForEach(ProfileSnapshot.musclePeriods, id: \.self) { days in
                    Text("\(days) days").tag(days)
                }
            }
            .pickerStyle(.segmented)
            muscleGroups(stats.muscleGroups[muscleDays] ?? [])
        }
        Section {
            if stats.recentRecords.isEmpty {
                Text("Beat your best estimated 1RM on an exercise to set a record.").foregroundStyle(.secondary)
            }
            let names = exerciseNames
            ForEach(Array(stats.recentRecords.enumerated()), id: \.offset) { _, record in
                StatRow(
                    title: names[record.exerciseId] ?? record.exerciseId,
                    subtitle: record.startedAt.formatted(date: .numeric, time: .omitted),
                    value: "\(formatWeight(weightUnit.display(record.weightKg))) \(weightUnit.symbol) × \(record.reps)",
                    detail: "1RM ≈ \(oneDecimal(record.estimatedOneRepMax))"
                )
            }
        } header: {
            Text("Personal records")
        } footer: {
            if stats.recordCount > 0 {
                Text("\(stats.recordCount) estimated 1RM records in total")
            }
        }
        Section("Most trained exercises") {
            if stats.topExercises.isEmpty {
                Text("No finished sets yet.").foregroundStyle(.secondary)
            }
            let names = exerciseNames
            ForEach(stats.topExercises, id: \.exerciseId) { top in
                let change = top.recentOneRepMax.flatMap { recent in
                    top.previousOneRepMax.map { weightUnit.display(recent) - weightUnit.display($0) }
                }
                StatRow(
                    title: names[top.exerciseId] ?? top.exerciseId,
                    subtitle: String(localized: "\(top.workouts) workouts"),
                    value: top.recentOneRepMax.map { "1RM ≈ \(oneDecimal($0))" } ?? "",
                    detail: change.map { String(localized: "\(signed($0)) vs. 3 months before") },
                    detailColor: changeColor(change)
                )
            }
        }
        Section("Training habits") {
            let habits = stats.habits
            LabeledContent("Average workout", value: String(localized: "\(habits.averageDurationSeconds / 60) min"))
            if let day = habits.favoriteWeekday {
                // Calendar weekday symbols start on Sunday; ours are 1 = Monday … 7 = Sunday.
                LabeledContent("Favorite day", value: Calendar.current.standaloneWeekdaySymbols[day % 7].capitalized)
            }
            if let rest = habits.averageRestDays {
                LabeledContent(
                    "Rest between workouts (90 days)",
                    value: String(localized: "\(rest.formatted(.number.precision(.fractionLength(1)))) days")
                )
            }
        }
        Section("Body weight") { bodyWeightChart }
    }

    private func oneDecimal(_ kg: Double) -> String {
        "\(formatWeight((weightUnit.display(kg) * 10).rounded() / 10)) \(weightUnit.symbol)"
    }

    private func changeColor(_ change: Double?) -> Color {
        guard let change, change != 0 else { return .secondary }
        return change > 0 ? Palette.success : .red
    }

    private func signed(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        let sign = rounded > 0 ? "+" : rounded < 0 ? "−" : "±"
        return "\(sign)\(formatWeight(abs(rounded))) \(weightUnit.symbol)"
    }

    @ViewBuilder
    private func muscleGroups(_ counts: [BodyPartCount]) -> some View {
        if counts.isEmpty {
            Text("No finished sets yet.").foregroundStyle(.secondary)
        } else {
            Chart(counts) { count in
                let name = String(localized: count.bodyPart.label)
                BarMark(x: .value("Sets", grown ? count.sets : 0), y: .value("Muscle group", name))
                    .cornerRadius(6)
                    .foregroundStyle(Palette.color(for: count.bodyPart).opacity(selectedPart == nil || selectedPart == name ? 1 : 0.35))
                    .annotation(position: .trailing) {
                        Text(verbatim: String(count.sets))
                            .font(.caption.bold())
                            .monospacedDigit()
                            .opacity(grown ? 1 : 0)
                    }
            }
            .chartYSelection(value: $selectedPart)
            .sensoryFeedback(.selection, trigger: selectedPart)
            .frame(height: CGFloat(counts.count) * 28 + 20)
        }
    }

    @ViewBuilder
    private var bodyWeightChart: some View {
        if bodyWeights.count < 2 {
            Text("Log your body weight at least twice in Measure to see a chart.").foregroundStyle(.secondary)
        } else {
            let unit = weightUnit
            InteractiveLineChart(
                points: bodyWeights.map { ChartPoint(date: $0.measuredAt, value: unit.display($0.value)) },
                formatValue: { "\(formatWeight($0)) \(unit.symbol)" },
                height: 180
            )
            .padding(.vertical, 8)
        }
    }

    private func weekBars<T>(_ values: [T]) -> [WeekValue<T>] {
        let current = WidgetSnapshot.startOfWeek(.now)
        return values.indices.map { index in
            WeekValue(week: Calendar.current.date(byAdding: .day, value: -7 * (values.count - 1 - index), to: current)!, value: values[index])
        }
    }

    private func volumeChart(_ volumes: [Double]) -> some View {
        let bars = weekBars(volumes.map { weightUnit.display($0) })
        return Chart(bars) { bar in
            BarMark(x: .value("Week", bar.week, unit: .weekOfYear), y: .value("Volume", grown ? bar.value : 0))
                .cornerRadius(6)
                .foregroundStyle(Palette.teal)
        }
        .chartXAxis {
            AxisMarks(values: bars.map(\.week)) { _ in
                AxisValueLabel(format: .dateTime.day().month(.defaultDigits), centered: true)
            }
        }
        .chartYAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let volume = value.as(Double.self) {
                        Text(verbatim: volume.formatted(.number.notation(.compactName)))
                    }
                }
            }
        }
    }

    private func workoutsChart(_ counts: [Int]) -> some View {
        let bars = weekBars(counts)
        let weeks = bars.map(\.week)
        let average = Double(counts.reduce(0, +)) / Double(max(counts.count, 1))
        // Match the touch to the week whose middle is closest, whatever day the calendar starts its weeks on.
        let selected = selectedWeek.flatMap { date in
            nearestIndex(bars.map { $0.week.timeIntervalSince1970 + 3.5 * 24 * 60 * 60 }, to: date.timeIntervalSince1970)
        }
        return Chart {
            ForEach(Array(bars.enumerated()), id: \.element.id) { index, bar in
                BarMark(x: .value("Week", bar.week, unit: .weekOfYear), y: .value("Workouts", grown ? bar.value : 0))
                    .cornerRadius(6)
                    .foregroundStyle(Color.accentColor.opacity(selected == nil || selected == index ? 1 : 0.35))
                    .annotation(position: .top) {
                        Text(verbatim: String(bar.value))
                            .font(.caption2.bold())
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                            .opacity(grown && selected == nil ? 1 : 0)
                    }
            }
            if average > 0 {
                RuleMark(y: .value("Average", grown ? average : 0))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 4]))
                    .foregroundStyle(Color.secondary)
                    .annotation(position: .top, alignment: .trailing) {
                        Text("Avg \(average.formatted(.number.precision(.fractionLength(1))))")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
            }
            if let selected {
                let bar = bars[selected]
                RuleMark(x: .value("Week", bar.week, unit: .weekOfYear))
                    .foregroundStyle(Color.clear)
                    .annotation(position: .top, spacing: 0, overflowResolution: .init(x: .fit(to: .chart), y: .fit(to: .chart))) {
                        VStack(spacing: 2) {
                            Text("\(bar.value) workouts").font(.callout.bold())
                            Text("week of \(bar.week.formatted(.dateTime.day().month(.defaultDigits)))")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                    }
            }
        }
        .chartXSelection(value: $selectedWeek)
        .sensoryFeedback(.selection, trigger: selected)
        .chartXAxis {
            AxisMarks(values: weeks) { _ in
                AxisValueLabel(format: .dateTime.day().month(.defaultDigits), centered: true)
            }
        }
        .chartYAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                AxisGridLine()
                AxisValueLabel()
            }
        }
    }
}

private struct WeekValue<T>: Identifiable {
    let week: Date
    let value: T
    var id: Date { week }
}

/// A big number with a caption.
private struct StatCell: View {
    let value: String
    let label: LocalizedStringKey

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: value).font(.title2.bold()).monospacedDigit().lineLimit(1)
            Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A list line: name and a gray subtitle on the left, a value and an optional detail on the right.
private struct StatRow: View {
    let title: String
    let subtitle: String
    let value: String
    var detail: String?
    var detailColor: Color = .secondary

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title).lineLimit(1)
                Text(verbatim: subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(verbatim: value).monospacedDigit()
                if let detail {
                    Text(verbatim: detail).font(.caption).monospacedDigit().foregroundStyle(detailColor)
                }
            }
        }
    }
}

/// GitHub-style grid of the last weeks: one column per week, Monday on top, darker for more workouts.
/// Days after `today` are left empty.
private struct Heatmap: View {
    let days: [Int]
    let today: Int

    var body: some View {
        let weeks = max(days.count / 7, 1)
        Canvas { context, size in
            let gap: CGFloat = 3
            let cell = (size.width - gap * CGFloat(weeks - 1)) / CGFloat(weeks)
            for index in days.indices where index <= today {
                let rect = CGRect(
                    x: CGFloat(index / 7) * (cell + gap),
                    y: CGFloat(index % 7) * (cell + gap),
                    width: cell,
                    height: cell
                )
                let color: Color = switch days[index] {
                case 0: Palette.raised
                case 1: Color.accentColor.opacity(0.6)
                default: Color.accentColor
                }
                context.fill(Path(roundedRect: rect, cornerRadius: cell / 4), with: .color(color))
            }
        }
        .aspectRatio(CGFloat(weeks) / 7, contentMode: .fit)
        .accessibilityLabel(Text("Last \(weeks) weeks, one square per day"))
    }
}
