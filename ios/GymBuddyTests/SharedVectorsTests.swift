import Foundation
import XCTest
@testable import GymBuddy

/// Runs the cross-platform test vectors in shared/test-vectors.
final class SharedVectorsTests: XCTestCase {
    private func vectors<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }

    func testEpleyMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let weightKg: Double; let reps: Int; let expected: Double }
            let cases: [Case]
        }
        for c in try vectors("one_rep_max", as: File.self).cases {
            XCTAssertEqual(estimatedOneRepMax(weightKg: c.weightKg, reps: c.reps), c.expected,
                           accuracy: 0.01, "\(c.weightKg) x \(c.reps)")
        }
    }

    func testWeightConversionMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let kg: Double; let lb: Double }
            let cases: [Case]
        }
        for c in try vectors("units", as: File.self).cases {
            XCTAssertEqual(WeightUnit.lb.fromKg(c.kg), c.lb, accuracy: 0.01)
            XCTAssertEqual(WeightUnit.lb.toKg(c.lb), c.kg, accuracy: 0.01)
        }
    }

    func testTypedWeightsSurviveTheKgRoundTrip() throws {
        struct File: Decodable {
            struct RoundTrip: Decodable {
                struct Case: Decodable { let unit: String; let typed: Double; let formatted: String }
                let cases: [Case]
            }
            let roundTrip: RoundTrip
        }
        for c in try vectors("units", as: File.self).roundTrip.cases {
            let unit = try XCTUnwrap(WeightUnit(rawValue: c.unit))
            let kg = unit.toKg(c.typed)
            XCTAssertEqual(unit.display(kg), c.typed, accuracy: 1e-9, "\(c.typed) \(c.unit)")
            XCTAssertEqual(unit.format(kg), c.formatted)
        }
    }

    func testSyncTimestampsMatchSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let text: String; let epochMillis: Int64 }
            let parse: [Case]
            let format: [Case]
        }
        let file = try vectors("timestamps", as: File.self)
        for c in file.parse {
            let date = try XCTUnwrap(Timestamp.parse(c.text), c.text)
            XCTAssertEqual(Int64((date.timeIntervalSince1970 * 1000).rounded()), c.epochMillis, c.text)
        }
        for c in file.format {
            XCTAssertEqual(Timestamp.format(Date(timeIntervalSince1970: Double(c.epochMillis) / 1000)), c.text)
        }
    }

    func testExerciseRecordsMatchSharedVectors() throws {
        struct File: Decodable {
            struct VectorSet: Decodable { let weightKg: Double?; let reps: Int?; let completed: Bool }
            struct Session: Decodable { let startedAt: Double; let sets: [VectorSet] }
            struct VectorRecord: Decodable { let value: Double; let startedAt: Double }
            struct Records: Decodable {
                let estimatedOneRepMax: VectorRecord?
                let maxWeight: VectorRecord?
                let maxSetVolume: VectorRecord?
                let maxReps: VectorRecord?
                let maxSessionVolume: VectorRecord?
            }
            struct Point: Decodable { let startedAt: Double; let estimatedOneRepMax: Double; let maxWeight: Double; let volumeKg: Double }
            struct Case: Decodable { let name: String; let sessions: [Session]; let records: Records; let points: [Point] }
            let cases: [Case]
        }
        func date(_ millis: Double) -> Date { Date(timeIntervalSince1970: millis / 1000) }
        func check(_ actual: Record?, _ expected: File.VectorRecord?, _ label: String) {
            guard let expected else { return XCTAssertNil(actual, label) }
            guard let actual else { return XCTFail("missing \(label)") }
            XCTAssertEqual(actual.value, expected.value, accuracy: 0.01, label)
            XCTAssertEqual(actual.startedAt, date(expected.startedAt), label)
        }
        for c in try vectors("exercise_records", as: File.self).cases {
            let sessions = c.sessions.map { session in
                ExerciseSession(
                    startedAt: date(session.startedAt),
                    sets: session.sets.map { SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed) }
                )
            }
            let (records, points) = exerciseProgress(sessions)
            check(records.estimatedOneRepMax, c.records.estimatedOneRepMax, "\(c.name) e1RM")
            check(records.maxWeight, c.records.maxWeight, "\(c.name) max weight")
            check(records.maxSetVolume, c.records.maxSetVolume, "\(c.name) set volume")
            check(records.maxReps, c.records.maxReps, "\(c.name) reps")
            check(records.maxSessionVolume, c.records.maxSessionVolume, "\(c.name) session volume")
            XCTAssertEqual(points.count, c.points.count, c.name)
            for (actual, expected) in zip(points, c.points) {
                XCTAssertEqual(actual.startedAt, date(expected.startedAt), c.name)
                XCTAssertEqual(actual.estimatedOneRepMax, expected.estimatedOneRepMax, accuracy: 0.01, c.name)
                XCTAssertEqual(actual.maxWeight, expected.maxWeight, accuracy: 0.01, c.name)
                XCTAssertEqual(actual.volumeKg, expected.volumeKg, accuracy: 0.01, c.name)
            }
        }
    }

    func testCalculatorsMatchSharedVectors() throws {
        struct File: Decodable {
            struct Plates: Decodable {
                let name: String
                let target: Double
                let bar: Double
                let plates: [Double]
                let perSide: [Double]
                let loaded: Double
                let remainder: Double
            }
            struct RepMax: Decodable {
                struct Row: Decodable { let reps: Int; let weight: Double }
                let oneRepMax: Double
                let rows: [Row]
            }
            let plates: [Plates]
            let repMax: RepMax
        }
        let file = try vectors("calculators", as: File.self)
        for c in file.plates {
            let load = plateLoad(target: c.target, bar: c.bar, plates: c.plates)
            XCTAssertEqual(load.perSide, c.perSide, c.name)
            XCTAssertEqual(load.loaded, c.loaded, accuracy: 0.01, c.name)
            XCTAssertEqual(load.remainder, c.remainder, accuracy: 0.01, c.name)
        }
        for row in file.repMax.rows {
            XCTAssertEqual(repMaxWeight(oneRepMax: file.repMax.oneRepMax, reps: row.reps), row.weight,
                           accuracy: 0.01, "\(row.reps) reps")
        }
    }

    func testCsvMatchesSharedVectors() throws {
        struct VSet: Decodable {
            let setType: String; let weightKg: Double?; let reps: Int?; let distanceM: Double?; let seconds: Int?; let rpe: Double?
        }
        struct VExercise: Decodable { let name: String; let note: String?; let sets: [VSet] }
        struct VWorkout: Decodable { let date: String; let name: String; let durationSec: Int?; let note: String?; let exercises: [VExercise] }
        struct File: Decodable {
            struct Fields: Decodable { let fields: [String]; let text: String }
            struct Durations: Decodable {
                struct Format: Decodable { let seconds: Int; let text: String }
                struct Parse: Decodable { let text: String; let seconds: Int? }
                let format: [Format]
                let parse: [Parse]
            }
            struct Import: Decodable { let name: String; let defaultUnit: String; let text: String; let workouts: [VWorkout] }
            struct Export: Decodable { let name: String; let unit: String; let workouts: [VWorkout]; let text: String }
            let fields: [Fields]
            let durations: Durations
            let `import`: [Import]
            let export: [Export]
        }
        func model(_ workouts: [VWorkout]) -> [CsvWorkout] {
            workouts.map { w in
                CsvWorkout(date: w.date, name: w.name, durationSec: w.durationSec, note: w.note, exercises: w.exercises.map { e in
                    CsvExercise(name: e.name, note: e.note, sets: e.sets.map {
                        CsvSet(setType: $0.setType, weightKg: $0.weightKg, reps: $0.reps, distanceM: $0.distanceM, seconds: $0.seconds, rpe: $0.rpe)
                    })
                })
            }
        }
        /// Weights are compared with a tolerance; everything else exactly.
        func assertSame(_ expected: [CsvWorkout], _ actual: [CsvWorkout], _ label: String) {
            func withoutWeights(_ list: [CsvWorkout]) -> [CsvWorkout] {
                list.map { w in
                    var w = w
                    w.exercises = w.exercises.map { e in
                        var e = e
                        e.sets = e.sets.map { s in var s = s; s.weightKg = nil; return s }
                        return e
                    }
                    return w
                }
            }
            XCTAssertEqual(withoutWeights(expected), withoutWeights(actual), label)
            let e = expected.flatMap { $0.exercises.flatMap { $0.sets.map(\.weightKg) } }
            let a = actual.flatMap { $0.exercises.flatMap { $0.sets.map(\.weightKg) } }
            XCTAssertEqual(e.count, a.count, label)
            for (x, y) in zip(e, a) {
                if let x { XCTAssertEqual(x, y ?? .nan, accuracy: 0.01, label) } else { XCTAssertNil(y, label) }
            }
        }

        let file = try vectors("csv", as: File.self)
        for c in file.fields {
            XCTAssertEqual(csvLine(c.fields), c.text)
            XCTAssertEqual(parseCsv(c.text), [c.fields])
        }
        for c in file.durations.format { XCTAssertEqual(formatCsvDuration(c.seconds), c.text) }
        for c in file.durations.parse { XCTAssertEqual(parseCsvDuration(c.text), c.seconds, c.text) }
        for c in file.import {
            let unit = try XCTUnwrap(WeightUnit(rawValue: c.defaultUnit))
            let actual = try XCTUnwrap(importCsv(c.text, defaultUnit: unit), c.name)
            assertSame(model(c.workouts), actual, c.name)
        }
        for c in file.export {
            let unit = try XCTUnwrap(WeightUnit(rawValue: c.unit))
            let text = exportCsv(model(c.workouts), unit: unit)
            XCTAssertEqual(text, c.text, c.name)
            assertSame(model(c.workouts), try XCTUnwrap(importCsv(text, defaultUnit: .kg)), c.name)
        }
        XCTAssertNil(importCsv("a,b\n1,2\n", defaultUnit: .kg))
    }
}
