import Foundation
import XCTest
@testable import GymBuddy

/// Runs shared/test-vectors/profile_stats.json.
final class ProfileStatsTests: XCTestCase {
    private struct File: Decodable {
        struct SetCase: Decodable { let weightKg: Double?; let reps: Int?; let completed: Bool; let warmup: Bool }
        struct ExerciseCase: Decodable { let exerciseId: String; let sets: [SetCase] }
        struct WorkoutCase: Decodable { let startedAt: Double; let endedAt: Double; let exercises: [ExerciseCase] }
        struct Totals: Decodable { let workouts: Int; let durationSeconds: Int; let volumeKg: Double; let sets: Int }
        struct Streaks: Decodable { let current: Int; let best: Int }
        struct Habits: Decodable { let averageDurationSeconds: Int; let favoriteWeekday: Int?; let averageRestDays: Double? }
        struct Record: Decodable { let exerciseId: String; let startedAt: Double; let weightKg: Double; let reps: Int; let estimatedOneRepMax: Double }
        struct Top: Decodable { let exerciseId: String; let workouts: Int; let recentOneRepMax: Double?; let previousOneRepMax: Double? }
        struct Expected: Decodable {
            let totals: Totals
            let streaks: Streaks
            let heatmap: [Int]
            let weeklyVolume: [Double]
            let habits: Habits
            let records: [Record]
            let top: [Top]
        }
        struct Case: Decodable {
            let name: String
            let now: Double
            let heatmapWeeks: Int
            let volumeWeeks: Int
            let topLimit: Int
            let workouts: [WorkoutCase]
            let expected: Expected
        }
        let cases: [Case]
    }

    /// Times are epoch millis.
    private func date(_ millis: Double) -> Date { Date(timeIntervalSince1970: millis / 1000) }

    private func assertClose(_ expected: Double?, _ actual: Double?, _ message: String) {
        if let expected {
            XCTAssertEqual(actual ?? .nan, expected, accuracy: 0.01, message)
        } else {
            XCTAssertNil(actual, message)
        }
    }

    func testProfileStatsMatchSharedVectors() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "profile_stats", withExtension: "json"))
        let file = try JSONDecoder().decode(File.self, from: Data(contentsOf: url))
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        for c in file.cases {
            let now = date(c.now)
            let workouts = c.workouts.map { w in
                StatsWorkout(
                    startedAt: date(w.startedAt),
                    endedAt: date(w.endedAt),
                    exercises: w.exercises.map { e in
                        StatsExercise(exerciseId: e.exerciseId, sets: e.sets.map {
                            StatsSet(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed, warmup: $0.warmup)
                        })
                    }
                )
            }
            let starts = workouts.map(\.startedAt)
            let expected = c.expected

            let totals = profileTotals(workouts)
            XCTAssertEqual(totals.workouts, expected.totals.workouts, c.name)
            XCTAssertEqual(totals.durationSeconds, expected.totals.durationSeconds, c.name)
            assertClose(expected.totals.volumeKg, totals.volumeKg, c.name)
            XCTAssertEqual(totals.sets, expected.totals.sets, c.name)

            XCTAssertEqual(
                weekStreaks(starts, now: now, calendar: utc),
                WeekStreaks(current: expected.streaks.current, best: expected.streaks.best),
                c.name
            )
            XCTAssertEqual(workoutsPerDay(starts, now: now, weeks: c.heatmapWeeks, calendar: utc), expected.heatmap, c.name)
            let volumes = weeklyVolume(workouts, now: now, weeks: c.volumeWeeks, calendar: utc)
            XCTAssertEqual(volumes.count, expected.weeklyVolume.count, c.name)
            for (actual, wanted) in zip(volumes, expected.weeklyVolume) { assertClose(wanted, actual, c.name) }

            let habits = trainingHabits(workouts, now: now, calendar: utc)
            XCTAssertEqual(habits.averageDurationSeconds, expected.habits.averageDurationSeconds, c.name)
            XCTAssertEqual(habits.favoriteWeekday, expected.habits.favoriteWeekday, c.name)
            assertClose(expected.habits.averageRestDays, habits.averageRestDays, c.name)

            let records = personalRecords(workouts)
            XCTAssertEqual(records.count, expected.records.count, c.name)
            for (actual, wanted) in zip(records, expected.records) {
                XCTAssertEqual(actual.exerciseId, wanted.exerciseId, c.name)
                XCTAssertEqual(actual.startedAt, date(wanted.startedAt), c.name)
                assertClose(wanted.weightKg, actual.weightKg, c.name)
                XCTAssertEqual(actual.reps, wanted.reps, c.name)
                assertClose(wanted.estimatedOneRepMax, actual.estimatedOneRepMax, c.name)
            }

            let top = topExercises(workouts, now: now, limit: c.topLimit)
            XCTAssertEqual(top.count, expected.top.count, c.name)
            for (actual, wanted) in zip(top, expected.top) {
                XCTAssertEqual(actual.exerciseId, wanted.exerciseId, c.name)
                XCTAssertEqual(actual.workouts, wanted.workouts, c.name)
                assertClose(wanted.recentOneRepMax, actual.recentOneRepMax, c.name)
                assertClose(wanted.previousOneRepMax, actual.previousOneRepMax, c.name)
            }
        }
    }
}
