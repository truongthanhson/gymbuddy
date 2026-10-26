import Foundation
import SwiftData
import XCTest
@testable import GymBuddy

@MainActor
final class ExerciseServiceTests: XCTestCase {
    /// The container must outlive its context, so each test keeps it in a local.
    private func makeContainer() throws -> ModelContainer {
        try ModelContainer(for: Exercise.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }

    func testCreateUpdateAndDeleteCustomExercise() throws {
        let container = try makeContainer()
        let context = container.mainContext

        let created = ExerciseService.createCustom(name: "  Landmine Press ", bodyPart: .shoulders, category: .barbell, in: context)
        XCTAssertTrue(created.isCustom)
        XCTAssertEqual(created.name, "Landmine Press")
        XCTAssertEqual(created.bodyPart, "shoulders")

        ExerciseService.update(created, name: "Landmine Press (1 arm)", bodyPart: .shoulders, category: .machine, in: context)
        let fetched = try XCTUnwrap(try context.fetch(FetchDescriptor<Exercise>()).first)
        XCTAssertEqual(fetched.name, "Landmine Press (1 arm)")
        XCTAssertEqual(ExerciseCategory(key: fetched.category), .machine)

        ExerciseService.delete(fetched, in: context)
        XCTAssertNotNil(fetched.deletedAt)
    }

    func testBuiltInExercisesAreReadOnly() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let builtIn = Exercise(id: "bench_press_barbell", name: "Bench Press (Barbell)", category: "barbell", bodyPart: "chest", isCustom: false)
        context.insert(builtIn)

        ExerciseService.update(builtIn, name: "Renamed", bodyPart: .back, category: .cable, in: context)
        ExerciseService.delete(builtIn, in: context)

        XCTAssertEqual(builtIn.name, "Bench Press (Barbell)")
        XCTAssertNil(builtIn.deletedAt)
    }
}
