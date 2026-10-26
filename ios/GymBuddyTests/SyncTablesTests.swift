import Foundation
import SwiftData
import XCTest
@testable import GymBuddy

/// Rows pushed from one device and pulled into another must rebuild the same data.
@MainActor
final class SyncTablesTests: XCTestCase {
    /// The container must outlive its context, so each test keeps it in a local.
    private func makeContainer() throws -> ModelContainer {
        try ModelContainer(
            for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self,
            TemplateFolder.self, WorkoutTemplate.self, TemplateExercise.self, TemplateSet.self, BodyMeasurement.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
    }

    /// Pushes every table from `source` and pulls it into `target`, through JSON like the server would.
    private func copy(from source: ModelContext, to target: ModelContext) throws {
        for table in SyncTables.all {
            let rows = try table.changed(source, .distantPast)
            let data = try JSONSerialization.data(withJSONObject: rows)
            let pulled = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [SyncRow])
            try table.apply(target, pulled)
        }
        try target.save()
    }

    func testWorkoutSurvivesTheRoundTrip() throws {
        let sourceContainer = try makeContainer()
        let source = sourceContainer.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "Push", in: source)
        WorkoutService.addExercises(["bench_press_barbell"], to: workout, in: source)
        let item = try XCTUnwrap(workout.liveExercises.first)
        let set = try XCTUnwrap(item.liveSets.first)
        set.weightKg = 82.5
        set.reps = 8
        WorkoutService.toggle(set, previous: nil, in: source)
        WorkoutService.finish(workout, in: source)

        let targetContainer = try makeContainer()
        let target = targetContainer.mainContext
        try copy(from: source, to: target)

        let pulled = try XCTUnwrap(try target.fetch(FetchDescriptor<Workout>()).first)
        XCTAssertEqual(pulled.id, workout.id)
        XCTAssertEqual(pulled.name, "Push")
        XCTAssertNotNil(pulled.endedAt)
        let pulledSet = try XCTUnwrap(pulled.liveExercises.first?.liveSets.first)
        XCTAssertEqual(pulled.liveExercises.first?.exerciseId, "bench_press_barbell")
        XCTAssertEqual(pulledSet.weightKg, 82.5)
        XCTAssertEqual(pulledSet.reps, 8)
        XCTAssertTrue(pulledSet.completed)
    }

    func testOlderRemoteRowDoesNotOverwriteLocalEdit() throws {
        let sourceContainer = try makeContainer()
        let source = sourceContainer.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "Old name", in: source)

        let targetContainer = try makeContainer()
        let target = targetContainer.mainContext
        try copy(from: source, to: target)

        let local = try XCTUnwrap(try target.fetch(FetchDescriptor<Workout>()).first)
        local.name = "Renamed here"
        local.updatedAt = workout.updatedAt.addingTimeInterval(10)
        try copy(from: source, to: target)

        XCTAssertEqual(try target.fetch(FetchDescriptor<Workout>()).first?.name, "Renamed here")
    }

    func testCustomExerciseRoundTrip() throws {
        let sourceContainer = try makeContainer()
        let source = sourceContainer.mainContext
        let exercise = ExerciseService.createCustom(name: "Landmine Press", bodyPart: .shoulders, category: .barbell, in: source)

        let targetContainer = try makeContainer()
        let target = targetContainer.mainContext
        try copy(from: source, to: target)

        let pulled = try XCTUnwrap(try target.fetch(FetchDescriptor<Exercise>()).first)
        XCTAssertEqual(pulled.id, exercise.id)
        XCTAssertEqual(pulled.name, "Landmine Press")
        XCTAssertTrue(pulled.isCustom)
    }

    func testMeasurementRoundTrip() throws {
        let sourceContainer = try makeContainer()
        let source = sourceContainer.mainContext
        let measurement = BodyMeasurement(kind: MeasurementKind.weight.rawValue, value: 80.5, unit: MeasurementUnit.kg.rawValue)
        source.insert(measurement)
        try source.save()

        let targetContainer = try makeContainer()
        let target = targetContainer.mainContext
        try copy(from: source, to: target)

        let pulled = try XCTUnwrap(try target.fetch(FetchDescriptor<BodyMeasurement>()).first)
        XCTAssertEqual(pulled.id, measurement.id)
        XCTAssertEqual(pulled.kind, "weight")
        XCTAssertEqual(pulled.value, 80.5, accuracy: 1e-9)
        XCTAssertEqual(pulled.unit, "kg")
    }
}
