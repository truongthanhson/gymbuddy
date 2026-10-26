import Charts
import SwiftData
import SwiftUI

/// Strong's Profile tab: who you are, how many workouts you have done, and workouts per week. Settings are behind the gear.
struct ProfileView: View {
    private static let chartWeeks = 8
    private static let muscleDays = 30

    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil })
    private var workouts: [Workout]
    @Query private var exercises: [Exercise]
    @Query(filter: #Predicate<BodyMeasurement> { $0.kind == "weight" && $0.deletedAt == nil }, sort: \BodyMeasurement.measuredAt)
    private var bodyWeights: [BodyMeasurement]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var grown = false
    @State private var selectedWeek: Date?
    @State private var selectedPart: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: AuthService.shared.session?.email ?? String(localized: "GymBuddy"))
                            .font(.title2.bold())
                        Text("\(workouts.count) workouts")
                            .foregroundStyle(.secondary)
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
                Section("Workouts per week") {
                    chart.frame(height: 180)
                }
                if ProStore.shared.isPro {
                    Section("Sets per muscle group, last \(Self.muscleDays) days") { muscleGroups }
                    Section("Body weight") { bodyWeightChart }
                } else {
                    Section {
                        NavigationLink {
                            PaywallView()
                        } label: {
                            Label("Muscle groups and body weight charts come with GymBuddy Pro", systemImage: "lock.fill")
                        }
                    }
                }
            }
            .navigationTitle("Profile")
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

    @ViewBuilder
    private var muscleGroups: some View {
        let parts = Dictionary(exercises.map { ($0.id, $0.bodyPart) }, uniquingKeysWith: { first, _ in first })
        let sets = workouts.flatMap { workout in
            workout.liveExercises.flatMap { exercise in
                exercise.liveSets.filter(\.completed).map {
                    BodyPartSet(bodyPart: parts[exercise.exerciseId] ?? "", startedAt: workout.startedAt, warmup: $0.setType == .warmup)
                }
            }
        }
        let since = Calendar.current.date(byAdding: .day, value: -Self.muscleDays, to: .now) ?? .now
        let counts = setsPerBodyPart(sets, since: since)
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

    private var chart: some View {
        let now = Date.now
        let counts = workoutsPerWeek(workouts.map(\.startedAt), now: now, weeks: Self.chartWeeks)
        let current = WidgetSnapshot.startOfWeek(now)
        let bars = counts.indices.map { index in
            WeekCount(
                week: Calendar.current.date(byAdding: .day, value: -7 * (counts.count - 1 - index), to: current)!,
                count: counts[index]
            )
        }
        let weeks = bars.map(\.week)
        let average = Double(counts.reduce(0, +)) / Double(max(counts.count, 1))
        // Match the touch to the week whose middle is closest, whatever day the calendar starts its weeks on.
        let selected = selectedWeek.flatMap { date in
            nearestIndex(bars.map { $0.week.timeIntervalSince1970 + 3.5 * 24 * 60 * 60 }, to: date.timeIntervalSince1970)
        }
        return Chart {
            ForEach(Array(bars.enumerated()), id: \.element.id) { index, bar in
                BarMark(x: .value("Week", bar.week, unit: .weekOfYear), y: .value("Workouts", grown ? bar.count : 0))
                    .cornerRadius(6)
                    .foregroundStyle(Color.accentColor.opacity(selected == nil || selected == index ? 1 : 0.35))
                    .annotation(position: .top) {
                        Text(verbatim: String(bar.count))
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
                            Text("\(bar.count) workouts").font(.callout.bold())
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

private struct WeekCount: Identifiable {
    let week: Date
    let count: Int
    var id: Date { week }
}
