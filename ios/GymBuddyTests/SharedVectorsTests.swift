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
            struct Warmup: Decodable {
                struct Case: Decodable { let name: String; let working: Double; let bar: Double; let plates: [Double]; let sets: [[Double]] }
                let cases: [Case]
            }
            let plates: [Plates]
            let repMax: RepMax
            let warmup: Warmup
        }
        let file = try vectors("calculators", as: File.self)
        for c in file.warmup.cases {
            let expected = c.sets.map { WarmupSet(weight: $0[0], reps: Int($0[1])) }
            XCTAssertEqual(warmupSets(working: c.working, bar: c.bar, plates: c.plates), expected, c.name)
        }
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

    func testWatchLinkMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Labels: Decodable { let types: [String]; let labels: [String] }
            struct Next: Decodable { let note: String; let state: WatchState; let exercise: Int?; let setId: String? }
            struct Action: Decodable { let json: String; let action: String; let setId: String? }
            let labels: [Labels]
            let nextSet: [Next]
            let actions: [Action]
        }
        let file = try vectors("watch", as: File.self)
        for c in file.labels {
            XCTAssertEqual(watchSetLabels(c.types), c.labels)
        }
        for c in file.nextSet {
            let next = c.state.workout?.nextSet
            XCTAssertEqual(next?.exerciseIndex, c.exercise, c.note)
            XCTAssertEqual(next?.set.id, c.setId, c.note)
            XCTAssertEqual(WatchState.decode(try XCTUnwrap(c.state.encoded())), c.state, c.note)
        }
        for c in file.actions {
            let action = try XCTUnwrap(WatchAction.decode(c.json))
            XCTAssertEqual(action.action, c.action)
            XCTAssertEqual(action.setId, c.setId)
        }
    }

    func testSetFieldsMatchSharedVectors() throws {
        struct File: Decodable {
            struct Fields: Decodable { let category: String; let fields: [String] }
            struct Parse: Decodable { let text: String; let seconds: Int? }
            struct Format: Decodable { let seconds: Int; let text: String }
            struct Distance: Decodable { let meters: Double; let unit: String; let display: Double }
            let fields: [Fields]
            let parseDuration: [Parse]
            let formatDuration: [Format]
            let distance: [Distance]
        }
        let file = try vectors("set_fields", as: File.self)
        for c in file.fields {
            XCTAssertEqual(setFields(category: c.category).map(\.rawValue), c.fields, c.category)
        }
        for c in file.parseDuration {
            XCTAssertEqual(parseDuration(c.text), c.seconds, "'\(c.text)'")
        }
        for c in file.formatDuration {
            XCTAssertEqual(formatDuration(c.seconds), c.text)
        }
        for c in file.distance {
            let unit = try XCTUnwrap(DistanceUnit(rawValue: c.unit))
            XCTAssertEqual(unit.display(c.meters), c.display, accuracy: 0.001)
        }
        XCTAssertEqual(WeightUnit.lb.distanceUnit, .mi)
        XCTAssertEqual(WeightUnit.kg.distanceUnit, .km)
    }

    func testSupersetsMatchSharedVectors() throws {
        struct File: Decodable {
            struct Rest: Decodable { let groups: [Int?]; let results: [Bool] }
            struct Edit: Decodable { let note: String; let groups: [Int?]; let index: Int; let result: [Int?] }
            let startsRest: [Rest]
            let supersetWithNext: [Edit]
            let removeFromSuperset: [Edit]
        }
        let file = try vectors("supersets", as: File.self)
        for c in file.startsRest {
            XCTAssertEqual(c.groups.indices.map { startsRest(groups: c.groups, index: $0) }, c.results)
        }
        for c in file.supersetWithNext {
            XCTAssertEqual(supersetWithNext(groups: c.groups, index: c.index), c.result, c.note)
        }
        for c in file.removeFromSuperset {
            XCTAssertEqual(removeFromSuperset(groups: c.groups, index: c.index), c.result, c.note)
        }
    }

    func testMonthGridMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable {
                let year: Int; let month: Int; let firstDayOfWeek: Int
                let leading: Int; let days: Int; let weeks: Int
            }
            let monthGrid: [Case]
        }
        for c in try vectors("calendar", as: File.self).monthGrid {
            let grid = monthGrid(year: c.year, month: c.month, firstDayOfWeek: c.firstDayOfWeek)
            XCTAssertEqual(grid, MonthGrid(leading: c.leading, days: c.days), "\(c.year)-\(c.month)")
            XCTAssertEqual(grid.weeks, c.weeks, "\(c.year)-\(c.month)")
        }
    }

    func testLiveRecordsMatchSharedVectors() throws {
        struct File: Decodable {
            struct SetCase: Decodable { let weightKg: Double?; let reps: Int?; let completed: Bool }
            struct Case: Decodable { let name: String; let previous: [SetCase]; let current: [SetCase]; let badges: [[String]] }
            let cases: [Case]
        }
        func values(_ sets: [File.SetCase]) -> [SetValues] {
            sets.map { SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed) }
        }
        for c in try vectors("live_records", as: File.self).cases {
            let expected = c.badges.map { Set($0.compactMap(RecordKind.init(rawValue:))) }
            XCTAssertEqual(liveRecords(previous: values(c.previous), current: values(c.current)), expected, c.name)
        }
    }

    func testWorkoutsPerWeekMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let name: String; let now: Double; let weeks: Int; let startedAts: [Double]; let counts: [Int] }
            let cases: [Case]
        }
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        for c in try vectors("workouts_per_week", as: File.self).cases {
            let result = workoutsPerWeek(
                c.startedAts.map { Date(timeIntervalSince1970: $0 / 1000) },
                now: Date(timeIntervalSince1970: c.now / 1000),
                weeks: c.weeks,
                calendar: utc
            )
            XCTAssertEqual(result, c.counts, c.name)
        }
    }

    func testShareTextMatchesSharedVectors() throws {
        struct SetCase: Decodable {
            let type: String?
            let weightKg: Double?
            let reps: Int?
            let durationSec: Int?
            let distanceM: Double?
            var share: ShareSet {
                ShareSet(type: type ?? "normal", weightKg: weightKg, reps: reps, durationSec: durationSec, distanceM: distanceM)
            }
        }
        struct TextCase: Decodable {
            let category: String
            let unit: String
            let text: String
            let weightKg: Double?
            let reps: Int?
            let durationSec: Int?
            let distanceM: Double?
        }
        struct ExerciseCase: Decodable { let name: String; let category: String; let sets: [SetCase] }
        struct ShareCase: Decodable { let title: String; let subtitle: String; let unit: String; let exercises: [ExerciseCase]; let text: String }
        struct File: Decodable { let setText: [TextCase]; let shareText: [ShareCase] }
        let file = try vectors("share_text", as: File.self)
        for c in file.setText {
            let unit = try XCTUnwrap(WeightUnit(rawValue: c.unit))
            let set = ShareSet(weightKg: c.weightKg, reps: c.reps, durationSec: c.durationSec, distanceM: c.distanceM)
            XCTAssertEqual(setText(category: c.category, set: set, unit: unit), c.text)
        }
        for c in file.shareText {
            let unit = try XCTUnwrap(WeightUnit(rawValue: c.unit))
            let exercises = c.exercises.map { ShareExercise(name: $0.name, category: $0.category, sets: $0.sets.map(\.share)) }
            XCTAssertEqual(workoutShareText(title: c.title, subtitle: c.subtitle, exercises: exercises, unit: unit), c.text)
        }
    }

    func testBodyPartSetsMatchSharedVectors() throws {
        struct SetCase: Decodable { let bodyPart: String; let startedAt: Double; let warmup: Bool }
        struct Count: Decodable { let bodyPart: String; let sets: Int }
        struct Case: Decodable { let since: Double; let sets: [SetCase]; let expected: [Count] }
        struct File: Decodable { let order: [String]; let cases: [Case] }
        let file = try vectors("body_part_sets", as: File.self)
        XCTAssertEqual(BodyPart.allCases.map(\.rawValue), file.order)
        // Times are epoch millis.
        func date(_ millis: Double) -> Date { Date(timeIntervalSince1970: millis / 1000) }
        for c in file.cases {
            let sets = c.sets.map { BodyPartSet(bodyPart: $0.bodyPart, startedAt: date($0.startedAt), warmup: $0.warmup) }
            let expected = c.expected.map { BodyPartCount(bodyPart: BodyPart(key: $0.bodyPart), sets: $0.sets) }
            XCTAssertEqual(setsPerBodyPart(sets, since: date(c.since)), expected)
        }
    }

    func testStrongNamesMatchSharedVectors() throws {
        struct Alias: Decodable { let name: String; let library: String }
        struct Category: Decodable { let name: String; let category: String? }
        struct BodyPartCase: Decodable { let name: String; let bodyPart: String }
        struct BodyParts: Decodable { let library: [[String]]; let cases: [BodyPartCase] }
        struct File: Decodable { let aliases: [Alias]; let categories: [Category]; let bodyParts: BodyParts }
        let file = try vectors("strong_names", as: File.self)
        for c in file.aliases { XCTAssertEqual(libraryName(c.name), c.library, c.name) }
        for c in file.categories { XCTAssertEqual(categoryFromName(c.name), c.category, c.name) }
        let library = file.bodyParts.library.map { ($0[0], $0[1]) }
        for c in file.bodyParts.cases { XCTAssertEqual(guessBodyPart(c.name, library: library), c.bodyPart, c.name) }
    }

    func testCoachMatchesSharedVectors() throws {
        struct SetCase: Decodable { let weightKg: Double?; let reps: Int?; let rpe: Double?; let warmup: Bool }
        struct SessionCase: Decodable { let startedAt: Double; let sets: [SetCase] }
        struct PartSet: Decodable { let bodyPart: String; let startedAt: Double; let warmup: Bool }
        struct Target: Decodable { let kind: String; let weightKg: Double; let reps: Int }
        struct TargetCase: Decodable {
            let description: String; let bodyPart: String; let category: String
            let sessions: [SessionCase]; let expected: Target?
        }
        struct RecoveryRow: Decodable { let bodyPart: String; let hoursSince: Int; let status: String }
        struct RecoveryCase: Decodable { let now: Double; let sets: [PartSet]; let expected: [RecoveryRow] }
        struct Insight: Decodable {
            let type: String; let value: Int?; let bodyPart: String?
            let exerciseId: String?; let weightKg: Double?; let reps: Int?
        }
        struct VolumeCase: Decodable { let now: Double; let sets: [PartSet]; let expected: [Insight] }
        struct StreakCase: Decodable {
            let description: String; let now: Double; let goalPerWeek: Int; let workoutStarts: [Double]; let expected: Int
        }
        struct ExerciseCase: Decodable { let exerciseId: String; let bodyPart: String; let category: String; let sessions: [SessionCase] }
        struct InsightsCase: Decodable {
            let description: String; let now: Double; let goalPerWeek: Int; let workoutStarts: [Double]
            let exercises: [ExerciseCase]; let bodyPartSets: [PartSet]; let expected: [Insight]; let cycleWeek: Int?
        }
        struct File: Decodable {
            let nextTarget: [TargetCase]; let recovery: [RecoveryCase]; let volume: [VolumeCase]
            let streak: [StreakCase]; let insights: [InsightsCase]
        }
        let file = try vectors("coach", as: File.self)
        // Times are epoch millis.
        func date(_ millis: Double) -> Date { Date(timeIntervalSince1970: millis / 1000) }
        func sessions(_ s: [SessionCase]) -> [CoachSession] {
            s.map { CoachSession(startedAt: date($0.startedAt), sets: $0.sets.map { CoachSet(weightKg: $0.weightKg, reps: $0.reps, rpe: $0.rpe, warmup: $0.warmup) }) }
        }
        func partSets(_ s: [PartSet]) -> [BodyPartSet] {
            s.map { BodyPartSet(bodyPart: $0.bodyPart, startedAt: date($0.startedAt), warmup: $0.warmup) }
        }
        func insight(_ i: Insight) -> CoachInsight {
            switch i.type {
            case "inactive": .inactive(days: i.value!)
            case "deload": .deload(exerciseId: i.exerciseId!, weightKg: i.weightKg!, reps: i.reps!)
            case "volume_spike": .volumeSpike(bodyPart: BodyPart(key: i.bodyPart!), sets: i.value!)
            case "neglected": .neglected(bodyPart: BodyPart(key: i.bodyPart!), days: i.value!)
            case "cycle_deload": .cycleDeload
            default: .streak(weeks: i.value!)
            }
        }
        for c in file.nextTarget {
            let actual = nextTarget(sessions(c.sessions), bodyPart: c.bodyPart, category: c.category)
            if let e = c.expected {
                let a = try XCTUnwrap(actual, c.description)
                XCTAssertEqual(a.kind.rawValue, e.kind, c.description)
                XCTAssertEqual(a.weightKg, e.weightKg, accuracy: 0.01, c.description)
                XCTAssertEqual(a.reps, e.reps, c.description)
            } else {
                XCTAssertNil(actual, c.description)
            }
        }
        for c in file.recovery {
            let expected = c.expected.map {
                Recovery(bodyPart: BodyPart(key: $0.bodyPart), hoursSince: $0.hoursSince, status: RecoveryStatus(rawValue: $0.status)!)
            }
            XCTAssertEqual(recovery(partSets(c.sets), now: date(c.now)), expected)
        }
        for c in file.volume {
            XCTAssertEqual(volumeInsights(partSets(c.sets), now: date(c.now)), c.expected.map(insight))
        }
        for c in file.streak {
            XCTAssertEqual(streakWeeks(c.workoutStarts.map(date), now: date(c.now), goalPerWeek: c.goalPerWeek), c.expected, c.description)
        }
        for c in file.insights {
            let exercises = c.exercises.map {
                CoachExercise(exerciseId: $0.exerciseId, bodyPart: $0.bodyPart, category: $0.category, sessions: sessions($0.sessions))
            }
            let actual = coachInsights(
                exercises: exercises, bodyPartSets: partSets(c.bodyPartSets),
                workoutStarts: c.workoutStarts.map(date), goalPerWeek: c.goalPerWeek, now: date(c.now), cycleWeek: c.cycleWeek
            )
            XCTAssertEqual(actual, c.expected.map(insight), c.description)
        }
    }

    func testCycleMatchesSharedVectors() throws {
        struct Target: Decodable { let kind: String; let weightKg: Double; let reps: Int }
        struct WeekCase: Decodable { let description: String; let cycleStart: Double?; let now: Double; let expected: Int? }
        struct TargetCase: Decodable {
            let description: String; let target: Target?; let week: Int; let bodyPart: String; let category: String; let expected: Target?
        }
        struct File: Decodable { let cycle: [WeekCase]; let cycleTarget: [TargetCase] }
        let file = try vectors("coach", as: File.self)
        func date(_ millis: Double) -> Date { Date(timeIntervalSince1970: millis / 1000) }
        for c in file.cycle {
            XCTAssertEqual(cycleWeek(cycleStart: c.cycleStart.map(date), now: date(c.now)), c.expected, c.description)
        }
        for c in file.cycleTarget {
            let target = c.target.map { SetTarget(kind: TargetKind(rawValue: $0.kind)!, weightKg: $0.weightKg, reps: $0.reps) }
            let actual = cycleTarget(target, week: c.week, bodyPart: c.bodyPart, category: c.category)
            if let e = c.expected {
                let a = try XCTUnwrap(actual, c.description)
                XCTAssertEqual(a.kind.rawValue, e.kind, c.description)
                XCTAssertEqual(a.weightKg, e.weightKg, accuracy: 0.01, c.description)
                XCTAssertEqual(a.reps, e.reps, c.description)
            } else {
                XCTAssertNil(actual, c.description)
            }
        }
    }

    func testReminderMatchesSharedVectors() throws {
        struct Case: Decodable { let description: String; let daysPerWeek: Int; let lastWorkout: Double?; let expected: Double? }
        struct File: Decodable { let reminder: [Case] }
        for c in try vectors("coach", as: File.self).reminder {
            let last = c.lastWorkout.map { Date(timeIntervalSince1970: $0 / 1000) }
            let actual = reminderDueAt(lastWorkout: last, daysPerWeek: c.daysPerWeek).map { $0.timeIntervalSince1970 * 1000 }
            XCTAssertEqual(actual, c.expected, c.description)
        }
    }

    func testChartRangesMatchSharedVectors() throws {
        struct File: Decodable {
            struct Filter: Decodable { let days: [Double]; let range: String; let kept: [Int] }
            struct Available: Decodable { let days: [Double]; let ranges: [String] }
            struct Nearest: Decodable { let xs: [Double]; let x: Double; let index: Int }
            let filter: [Filter]
            let available: [Available]
            let nearestIndex: [Nearest]
        }
        func dates(_ days: [Double]) -> [Date] { days.map { Date(timeIntervalSince1970: $0 * 24 * 60 * 60) } }
        let file = try vectors("chart_range", as: File.self)
        for c in file.filter {
            let all = dates(c.days)
            let range = try XCTUnwrap(ChartRange(rawValue: c.range))
            XCTAssertEqual(range.filter(all, date: { $0 }), c.kept.map { all[$0] }, "\(c.days) \(c.range)")
        }
        for c in file.available {
            XCTAssertEqual(ChartRange.available(dates(c.days), date: { $0 }).map(\.rawValue), c.ranges, "\(c.days)")
        }
        for c in file.nearestIndex {
            XCTAssertEqual(nearestIndex(c.xs, to: c.x), c.index, "\(c.xs) \(c.x)")
        }
    }
}
