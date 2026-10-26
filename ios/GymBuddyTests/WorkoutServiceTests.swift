import Foundation
import SwiftData
import XCTest
@testable import GymBuddy

@MainActor
final class WorkoutServiceTests: XCTestCase {
    /// The container must outlive its context, so each test keeps it in a local.
    private func makeContainer() throws -> ModelContainer {
        try ModelContainer(
            for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
    }

    func testStartReturnsTheWorkoutAlreadyInProgress() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let first = WorkoutService.startEmptyWorkout(name: "A", in: context)
        let second = WorkoutService.startEmptyWorkout(name: "B", in: context)
        XCTAssertEqual(first.id, second.id)
    }

    func testAddSetCopiesTheLastSet() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "A", in: context)
        WorkoutService.addExercises(["squat_barbell"], to: workout, in: context)
        let item = try XCTUnwrap(workout.liveExercises.first)
        let first = try XCTUnwrap(item.liveSets.first)
        first.weightKg = 100
        first.reps = 5

        let added = WorkoutService.addSet(to: item, in: context)

        XCTAssertEqual(added.weightKg, 100)
        XCTAssertEqual(added.reps, 5)
        XCTAssertEqual(item.liveSets.map(\.sortOrder), [0, 1])
    }

    func testFinishDropsUntickedSetsAndUsesThemAsPrevious() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "A", in: context)
        WorkoutService.addExercises(["bench_press_barbell", "deadlift_barbell"], to: workout, in: context)
        let bench = workout.liveExercises[0]
        let set = try XCTUnwrap(bench.liveSets.first)
        set.weightKg = 80
        set.reps = 8
        WorkoutService.toggle(set, previous: nil, in: context)
        WorkoutService.addSet(to: bench, in: context)

        WorkoutService.finish(workout, in: context)

        XCTAssertNotNil(workout.endedAt)
        XCTAssertEqual(bench.liveSets.count, 1)
        XCTAssertEqual(workout.liveExercises.map(\.exerciseId), ["bench_press_barbell"])
        let previous = WorkoutService.previousSets(for: "bench_press_barbell", in: context)
        XCTAssertEqual(previous.map(\.weightKg), [80])
    }

    func testFinishWithNothingTickedDiscards() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "A", in: context)
        WorkoutService.addExercises(["plank"], to: workout, in: context)
        WorkoutService.finish(workout, in: context)
        XCTAssertNotNil(workout.deletedAt)
        XCTAssertNil(WorkoutService.activeWorkout(in: context))
    }

    func testTickingAnEmptySetFillsItFromPrevious() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let old = WorkoutService.startEmptyWorkout(name: "Old", in: context)
        WorkoutService.addExercises(["bench_press_barbell"], to: old, in: context)
        let oldSet = try XCTUnwrap(old.liveExercises.first?.liveSets.first)
        oldSet.weightKg = 60
        oldSet.reps = 10
        WorkoutService.toggle(oldSet, previous: nil, in: context)
        WorkoutService.finish(old, in: context)

        let workout = WorkoutService.startEmptyWorkout(name: "New", in: context)
        WorkoutService.addExercises(["bench_press_barbell"], to: workout, in: context)
        let set = try XCTUnwrap(workout.liveExercises.first?.liveSets.first)

        WorkoutService.toggle(set, previous: oldSet, in: context)

        XCTAssertTrue(set.completed)
        XCTAssertEqual(set.weightKg, 60)
        XCTAssertEqual(set.reps, 10)
    }

    func testSetDetailsAndNotes() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "A", in: context)
        WorkoutService.addExercises(["squat_barbell"], to: workout, in: context)
        let item = try XCTUnwrap(workout.liveExercises.first)
        let set = try XCTUnwrap(item.liveSets.first)

        WorkoutService.setType(.warmup, for: set, in: context)
        WorkoutService.setRpe(8.5, for: set, in: context)
        XCTAssertEqual(set.setType, .warmup)
        XCTAssertEqual(set.rpe, 8.5)

        WorkoutService.setNote("Belt on", for: item, in: context)
        WorkoutService.setNote("Felt strong", for: workout, in: context)
        XCTAssertEqual(item.note, "Belt on")
        XCTAssertEqual(workout.note, "Felt strong")
        WorkoutService.setNote("  ", for: item, in: context)
        XCTAssertNil(item.note)
        XCTAssertEqual(rpeOptions.first, 6)
        XCTAssertEqual(rpeOptions.last, 10)
        XCTAssertEqual(rpeOptions.count, 9)
    }

    func testWarmupSetsGoBeforeTheWorkingSets() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let workout = WorkoutService.startEmptyWorkout(name: "A", in: context)
        WorkoutService.addExercises(["squat_barbell"], to: workout, in: context)
        let item = try XCTUnwrap(workout.liveExercises.first)
        let working = try XCTUnwrap(item.liveSets.first)
        working.weightKg = 100
        working.reps = 5

        WorkoutService.insertWarmupSets([(20, 10), (40, 5)], into: item, in: context)

        let sets = item.liveSets
        XCTAssertEqual(sets.map(\.weightKg), [20, 40, 100])
        XCTAssertEqual(sets.map(\.setType), [.warmup, .warmup, .normal])
        XCTAssertEqual(watchSetLabels(sets.map(\.setTypeRaw)), ["W", "W", "1"])
    }
}
