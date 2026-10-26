import Foundation
import SwiftData
import XCTest
@testable import GymBuddy

@MainActor
final class WatchStateBuilderTests: XCTestCase {
    func testBuildsStateInTheUsersUnitWithHints() throws {
        let container = try ModelContainer(
            for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        let context = container.mainContext
        let exercise = Exercise(id: "bench", name: "Bench Press", category: "barbell", bodyPart: "chest", isCustom: false)
        context.insert(exercise)
        let workout = WorkoutService.startEmptyWorkout(name: "Push", in: context, now: Date(timeIntervalSince1970: 1))
        WorkoutService.addExercises(["bench"], to: workout, in: context)
        let item = try XCTUnwrap(workout.liveExercises.first)
        let warmup = try XCTUnwrap(item.liveSets.first)
        warmup.setType = .warmup
        warmup.weightKg = 20
        warmup.reps = 10
        warmup.completed = true
        let empty = WorkoutService.addSet(to: item, in: context)
        empty.weightKg = nil
        empty.reps = nil
        let previous = [WorkoutSet(sortOrder: 0, weightKg: 20, reps: 10), WorkoutSet(sortOrder: 1, weightKg: 60, reps: 8)]

        let state = WatchStateBuilder.state(
            for: workout,
            exercises: ["bench": exercise],
            previous: { $0 == "bench" ? previous : [] },
            unit: .lb,
            restEndsAt: Date(timeIntervalSince1970: 5)
        )

        let built = try XCTUnwrap(state.workout)
        XCTAssertEqual(built.name, "Push")
        XCTAssertEqual(built.startedAt, 1000)
        XCTAssertEqual(built.restEndsAt, 5000)
        XCTAssertEqual(built.exercises.map(\.name), ["Bench Press"])
        let sets = try XCTUnwrap(built.exercises.first?.sets)
        XCTAssertEqual(sets.map(\.label), ["W", "1"])
        XCTAssertEqual(sets[0].weight, "44.09")
        XCTAssertEqual(sets[0].unit, "lb")
        XCTAssertTrue(sets[0].done)
        XCTAssertEqual(sets[1].id, empty.id.uuidString)
        XCTAssertEqual(sets[1].weight, "")
        XCTAssertEqual(sets[1].hintWeight, "132.28")
        XCTAssertEqual(sets[1].hintReps, "8")
        XCTAssertEqual(built.nextSet?.set.id, empty.id.uuidString)

        XCTAssertNil(WatchStateBuilder.state(for: nil, exercises: [:], previous: { _ in [] }, unit: .kg, restEndsAt: nil).workout)
    }
}
