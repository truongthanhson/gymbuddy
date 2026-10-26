import SwiftData
import SwiftUI

struct HistoryView: View {
    @Query(
        filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil },
        sort: \Workout.startedAt,
        order: .reverse
    )
    private var workouts: [Workout]
    @Query private var exercises: [Exercise]

    var body: some View {
        NavigationStack {
            List(workouts) { workout in
                NavigationLink(value: workout) {
                    WorkoutCard(workout: workout, namesById: namesById)
                }
            }
            .overlay {
                if workouts.isEmpty {
                    ContentUnavailableView(
                        "History",
                        systemImage: "clock",
                        description: Text("No workouts yet. Finished workouts show up here.")
                    )
                }
            }
            .navigationTitle("History")
            .toolbar {
                NavigationLink {
                    HistoryCalendarView(workouts: workouts)
                } label: {
                    Label("Calendar", systemImage: "calendar")
                }
            }
            .navigationDestination(for: Workout.self) { workout in
                ActiveWorkoutView(workout: workout, editing: true)
            }
        }
    }

    private var namesById: [String: Exercise] {
        Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }
}

private struct WorkoutCard: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    let workout: Workout
    let namesById: [String: Exercise]

    var body: some View {
        let items = workout.liveExercises
        let allSets = items.flatMap(\.liveSets).map(\.values)
        let duration = Int((workout.endedAt ?? workout.startedAt).timeIntervalSince(workout.startedAt))

        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: workout.name).font(.headline)
            Text(workout.startedAt, format: .dateTime.day().month().year().hour().minute())
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(verbatim: "\(formatElapsed(duration)) · \(weightUnit.format(volumeKg(allSets)))")
                .font(.caption)
                .foregroundStyle(.secondary)
            ForEach(items) { item in
                let sets = item.liveSets.map(\.values)
                HStack {
                    Text(verbatim: "\(sets.count) × \(namesById[item.exerciseId]?.displayName ?? item.exerciseId)")
                        .lineLimit(1)
                    Spacer()
                    if let index = bestSetIndex(sets) {
                        Text(verbatim: "\(weightUnit.format(sets[index].weightKg ?? 0)) × \(sets[index].reps ?? 0)")
                    }
                }
                .font(.subheadline)
            }
        }
        .padding(.vertical, 4)
    }
}

/// Months with the days you trained marked, newest month first, like Strong's history calendar.
private struct HistoryCalendarView: View {
    /// Finished workouts, newest first.
    let workouts: [Workout]

    var body: some View {
        let calendar = Calendar.current
        // The first workout of each day.
        let byDay = Dictionary(
            workouts.reversed().map { (calendar.startOfDay(for: $0.startedAt), $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let now = calendar.dateInterval(of: .month, for: .now)!.start
        let earliest = byDay.keys.min().flatMap { calendar.dateInterval(of: .month, for: $0)?.start } ?? now
        let months = Array(
            sequence(first: now) { calendar.date(byAdding: .month, value: -1, to: $0)! }
                .prefix(while: { $0 >= earliest })
        )
        // A ScrollView, not a List: a List row with several links opens the first one wherever it is tapped.
        ScrollView {
            LazyVStack(spacing: 24) {
                ForEach(months, id: \.self) { month in
                    MonthView(month: month, byDay: byDay)
                }
            }
            .padding()
        }
        .navigationTitle("Calendar")
    }
}

private struct MonthView: View {
    let month: Date
    let byDay: [Date: Workout]

    var body: some View {
        let calendar = Calendar.current
        let parts = calendar.dateComponents([.year, .month], from: month)
        let firstDay = isoFirstWeekday(calendar)
        let grid = monthGrid(year: parts.year!, month: parts.month!, firstDayOfWeek: firstDay)
        let days = (1...grid.days).map { calendar.date(byAdding: .day, value: $0 - 1, to: month)! }
        let count = days.filter { byDay[$0] != nil }.count
        // Calendar weekday symbols start on Sunday.
        let symbols = calendar.veryShortWeekdaySymbols
        let firstIndex = calendar.firstWeekday - 1
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(month, format: .dateTime.month(.wide).year()).font(.headline)
                Spacer()
                Text("\(count) workouts").font(.caption).foregroundStyle(.secondary)
            }
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(0..<7, id: \.self) { i in
                    Text(verbatim: symbols[(firstIndex + i) % 7])
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                ForEach(0..<(grid.weeks * 7), id: \.self) { cell in
                    let day = cell - grid.leading
                    if days.indices.contains(day) {
                        DayCell(number: day + 1, workout: byDay[days[day]])
                    } else {
                        Color.clear.frame(height: 32)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

private struct DayCell: View {
    let number: Int
    let workout: Workout?

    var body: some View {
        if let workout {
            NavigationLink(value: workout) {
                Text(verbatim: "\(number)")
                    .frame(maxWidth: .infinity, minHeight: 32)
                    .background(Circle().fill(Color.accentColor))
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        } else {
            Text(verbatim: "\(number)").frame(maxWidth: .infinity, minHeight: 32)
        }
    }
}
