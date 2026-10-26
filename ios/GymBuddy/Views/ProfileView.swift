import Charts
import SwiftData
import SwiftUI

/// Strong's Profile tab: who you are, how many workouts you have done, and workouts per week. Settings are behind the gear.
struct ProfileView: View {
    private static let chartWeeks = 8

    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil })
    private var workouts: [Workout]

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
                Section("Workouts per week") {
                    chart.frame(height: 180)
                }
            }
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
