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
            .appBackground()
            .navigationTitle("Profile")
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
                BarMark(x: .value("Sets", count.sets), y: .value("Muscle group", String(localized: count.bodyPart.label)))
                    .annotation(position: .trailing) { Text(verbatim: String(count.sets)).font(.caption) }
            }
            .frame(height: CGFloat(counts.count) * 28 + 20)
        }
    }

    @ViewBuilder
    private var bodyWeightChart: some View {
        if bodyWeights.count < 2 {
            Text("Log your body weight at least twice in Measure to see a chart.").foregroundStyle(.secondary)
        } else {
            Chart(bodyWeights) { entry in
                LineMark(x: .value("Date", entry.measuredAt), y: .value("Weight", weightUnit.display(entry.value)))
                PointMark(x: .value("Date", entry.measuredAt), y: .value("Weight", weightUnit.display(entry.value)))
            }
            .chartYScale(domain: .automatic(includesZero: false))
            .frame(height: 180)
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
        return Chart(bars) { bar in
            BarMark(x: .value("Week", bar.week, unit: .weekOfYear), y: .value("Workouts", bar.count))
        }
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
