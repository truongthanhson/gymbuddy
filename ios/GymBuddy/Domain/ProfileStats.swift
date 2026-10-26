import Foundation

// The Profile tab's statistics. Everything here must match shared/test-vectors/profile_stats.json (and Android).
// Weeks start on Monday.

struct StatsSet: Equatable, Sendable {
    var weightKg: Double?
    var reps: Int?
    var completed: Bool
    var warmup: Bool

    var counts: Bool { completed && !warmup && (reps ?? 0) > 0 }
}

struct StatsExercise: Equatable, Sendable {
    var exerciseId: String
    var sets: [StatsSet]
}

/// A finished workout, reduced to what the stats need.
struct StatsWorkout: Equatable, Sendable {
    var startedAt: Date
    var endedAt: Date
    var exercises: [StatsExercise]
}

struct ProfileTotals: Equatable, Sendable {
    var workouts: Int
    var durationSeconds: Int
    var volumeKg: Double
    var sets: Int
}

/// Weeks in a row with at least one workout. `current` still counts when this week has none yet.
struct WeekStreaks: Equatable, Sendable {
    var current: Int
    var best: Int
}

struct TrainingHabits: Equatable, Sendable {
    var averageDurationSeconds: Int
    /// 1 = Monday … 7 = Sunday, the day with the most workouts (the earlier day on a tie); nil without workouts.
    var favoriteWeekday: Int?
    /// Average rest days between training days over the last `habitDays` days; nil with fewer than two.
    var averageRestDays: Double?
}

/// A workout in which an exercise's best estimated 1RM beat every earlier workout of it.
struct PersonalRecord: Equatable, Sendable {
    var exerciseId: String
    var startedAt: Date
    var weightKg: Double
    var reps: Int
    var estimatedOneRepMax: Double
}

struct TopExercise: Equatable, Sendable {
    var exerciseId: String
    var workouts: Int
    /// Best estimated 1RM in the last `trendDays` days, and in the `trendDays` before; nil when not trained then.
    var recentOneRepMax: Double?
    var previousOneRepMax: Double?
}

let habitDays = 90
let trendDays = 90

private func daysBetween(_ from: Date, _ to: Date, _ calendar: Calendar) -> Int {
    calendar.dateComponents([.day], from: calendar.startOfDay(for: from), to: calendar.startOfDay(for: to)).day ?? 0
}

/// Whole weeks from the week of `date` back to the week of `now`; negative for later weeks.
private func weeksAgo(_ date: Date, now: Date, _ calendar: Calendar) -> Int {
    let days = daysBetween(WidgetSnapshot.startOfWeek(date, calendar: calendar), WidgetSnapshot.startOfWeek(now, calendar: calendar), calendar)
    return Int((Double(days) / 7).rounded(.down))
}

/// Volume counts every completed set, like the history cards; the set count leaves warm-ups out.
func profileTotals(_ workouts: [StatsWorkout]) -> ProfileTotals {
    var duration = 0
    var volume = 0.0
    var sets = 0
    for workout in workouts {
        duration += max(0, Int(workout.endedAt.timeIntervalSince(workout.startedAt)))
        for exercise in workout.exercises {
            for set in exercise.sets where set.completed {
                volume += (set.weightKg ?? 0) * Double(set.reps ?? 0)
                if !set.warmup { sets += 1 }
            }
        }
    }
    return ProfileTotals(workouts: workouts.count, durationSeconds: duration, volumeKg: volume, sets: sets)
}

func weekStreaks(_ starts: [Date], now: Date, calendar: Calendar = .current) -> WeekStreaks {
    // Future workouts are left out.
    let weeks = Set(starts.map { weeksAgo($0, now: now, calendar) }.filter { $0 >= 0 })
    guard !weeks.isEmpty else { return WeekStreaks(current: 0, best: 0) }
    var best = 0
    var run = 0
    var previous: Int?
    for ago in weeks.sorted() {
        run = previous.map { ago - $0 == 1 } == true ? run + 1 : 1
        best = max(best, run)
        previous = ago
    }
    var streak = 0
    var week = weeks.contains(0) ? 0 : 1
    while weeks.contains(week) {
        streak += 1
        week += 1
    }
    return WeekStreaks(current: streak, best: best)
}

/// Monday of the first week of a `weeks`-week heatmap ending this week.
func heatmapStart(now: Date, weeks: Int, calendar: Calendar = .current) -> Date {
    calendar.date(byAdding: .day, value: -7 * (weeks - 1), to: WidgetSnapshot.startOfWeek(now, calendar: calendar))!
}

/// Workouts per day for a `weeks`-week heatmap, Monday of the first week first; days after today are 0.
func workoutsPerDay(_ starts: [Date], now: Date, weeks: Int, calendar: Calendar = .current) -> [Int] {
    let first = heatmapStart(now: now, weeks: weeks, calendar: calendar)
    var counts = Array(repeating: 0, count: weeks * 7)
    for start in starts {
        let index = daysBetween(first, start, calendar)
        if counts.indices.contains(index) { counts[index] += 1 }
    }
    return counts
}

/// Volume in kg per week, oldest first; the last entry is this week.
func weeklyVolume(_ workouts: [StatsWorkout], now: Date, weeks: Int, calendar: Calendar = .current) -> [Double] {
    var volumes = Array(repeating: 0.0, count: weeks)
    for workout in workouts {
        let ago = weeksAgo(workout.startedAt, now: now, calendar)
        guard (0..<weeks).contains(ago) else { continue }
        volumes[weeks - 1 - ago] += profileTotals([workout]).volumeKg
    }
    return volumes
}

func trainingHabits(_ workouts: [StatsWorkout], now: Date, calendar: Calendar = .current) -> TrainingHabits {
    guard !workouts.isEmpty else { return TrainingHabits(averageDurationSeconds: 0, favoriteWeekday: nil, averageRestDays: nil) }
    let average = profileTotals(workouts).durationSeconds / workouts.count
    var perWeekday = Array(repeating: 0, count: 7)
    for workout in workouts {
        // Calendar weekdays are 1 = Sunday … 7 = Saturday; turn them into 0 = Monday … 6 = Sunday.
        perWeekday[(calendar.component(.weekday, from: workout.startedAt) + 5) % 7] += 1
    }
    var favorite = 0
    for day in 1..<7 where perWeekday[day] > perWeekday[favorite] { favorite = day }

    let since = calendar.date(byAdding: .day, value: -habitDays, to: calendar.startOfDay(for: now))!
    let days = Set(workouts.map { calendar.startOfDay(for: $0.startedAt) }.filter { $0 > since }).sorted()
    var rest: Double?
    if days.count >= 2 {
        let gaps = zip(days, days.dropFirst()).map { daysBetween($0, $1, calendar) - 1 }
        rest = Double(gaps.reduce(0, +)) / Double(gaps.count)
    }
    return TrainingHabits(averageDurationSeconds: average, favoriteWeekday: favorite + 1, averageRestDays: rest)
}

/// Every estimated-1RM record, newest first. The first workout of an exercise sets its baseline, not a record.
func personalRecords(_ workouts: [StatsWorkout]) -> [PersonalRecord] {
    var best: [String: Double] = [:]
    var records: [PersonalRecord] = []
    for workout in workouts.sorted(by: { $0.startedAt < $1.startedAt }) {
        for exercise in workout.exercises {
            var top: StatsSet?
            var topValue = 0.0
            for set in exercise.sets where set.counts && (set.weightKg ?? 0) > 0 {
                let value = estimatedOneRepMax(weightKg: set.weightKg!, reps: set.reps!)
                if value > topValue {
                    top = set
                    topValue = value
                }
            }
            guard let top else { continue }
            let previous = best[exercise.exerciseId]
            if let previous, topValue > previous {
                records.append(PersonalRecord(
                    exerciseId: exercise.exerciseId,
                    startedAt: workout.startedAt,
                    weightKg: top.weightKg!,
                    reps: top.reps!,
                    estimatedOneRepMax: topValue
                ))
            }
            if previous == nil || topValue > previous! { best[exercise.exerciseId] = topValue }
        }
    }
    return records.reversed()
}

/// The `limit` exercises in the most workouts (with a finished working set), most first, ties by id.
func topExercises(_ workouts: [StatsWorkout], now: Date, limit: Int) -> [TopExercise] {
    var counts: [String: Int] = [:]
    var recent: [String: Double] = [:]
    var previous: [String: Double] = [:]
    let day = 24.0 * 60 * 60
    let recentFrom = now.addingTimeInterval(-Double(trendDays) * day)
    let previousFrom = recentFrom.addingTimeInterval(-Double(trendDays) * day)
    for workout in workouts {
        var seen = Set<String>()
        for exercise in workout.exercises where exercise.sets.contains(where: \.counts) && seen.insert(exercise.exerciseId).inserted {
            counts[exercise.exerciseId, default: 0] += 1
        }
        let isRecent = workout.startedAt >= recentFrom
        guard isRecent || workout.startedAt >= previousFrom else { continue }
        for exercise in workout.exercises {
            let values = exercise.sets.filter { $0.counts && ($0.weightKg ?? 0) > 0 }
                .map { estimatedOneRepMax(weightKg: $0.weightKg!, reps: $0.reps!) }
            guard let top = values.max() else { continue }
            if isRecent {
                recent[exercise.exerciseId] = max(recent[exercise.exerciseId] ?? 0, top)
            } else {
                previous[exercise.exerciseId] = max(previous[exercise.exerciseId] ?? 0, top)
            }
        }
    }
    return counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }
        .prefix(limit)
        .map { TopExercise(exerciseId: $0.key, workouts: $0.value, recentOneRepMax: recent[$0.key], previousOneRepMax: previous[$0.key]) }
}
