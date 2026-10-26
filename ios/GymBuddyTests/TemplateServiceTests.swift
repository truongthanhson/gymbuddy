import Foundation
import SwiftData
import XCTest
@testable import GymBuddy

@MainActor
final class TemplateServiceTests: XCTestCase {
    /// The container must outlive its context, so each test keeps it in a local.
    private func makeContainer() throws -> ModelContainer {
        try ModelContainer(
            for: Exercise.self, Workout.self, WorkoutExercise.self, WorkoutSet.self,
            TemplateFolder.self, WorkoutTemplate.self, TemplateExercise.self, TemplateSet.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
    }

    private func pushDayDraft() -> TemplateDraft {
        TemplateDraft(
            name: "Push",
            exercises: [
                .init(exerciseId: "bench_press_barbell", sets: [
                    .init(targetWeightKg: 80, targetReps: 8),
                    .init(targetWeightKg: 80, targetReps: 8),
                ]),
                .init(exerciseId: "lateral_raise_dumbbell", restSeconds: 60, sets: [.init(targetWeightKg: 10, targetReps: 12)]),
            ]
        )
    }

    func testSaveAndReloadDraft() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let template = TemplateService.save(pushDayDraft(), in: context)

        let reloaded = TemplateService.draft(for: template)

        XCTAssertEqual(reloaded.name, "Push")
        XCTAssertEqual(reloaded.exercises.map(\.exerciseId), ["bench_press_barbell", "lateral_raise_dumbbell"])
        XCTAssertEqual(reloaded.exercises[0].sets.map(\.targetWeightKg), [80, 80])
        XCTAssertEqual(reloaded.exercises[1].restSeconds, 60)
    }

    func testEditingSoftDeletesRemovedRowsAndKeepsIds() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let template = TemplateService.save(pushDayDraft(), in: context)
        var draft = TemplateService.draft(for: template)
        let benchId = draft.exercises[0].id
        draft.exercises.removeLast()
        draft.exercises[0].sets.removeLast()

        TemplateService.save(draft, in: context)

        XCTAssertEqual(template.liveExercises.map(\.id), [benchId])
        XCTAssertEqual(template.liveExercises[0].liveSets.count, 1)
        XCTAssertEqual(template.exercises.count, 2, "removed exercise is kept as a tombstone for sync")
    }

    func testStartWorkoutCopiesTargetsAndMarksUsed() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let template = TemplateService.save(pushDayDraft(), in: context)

        let workout = try XCTUnwrap(TemplateService.startWorkout(from: template, in: context))

        XCTAssertEqual(workout.templateId, template.id)
        XCTAssertEqual(workout.liveExercises.map(\.exerciseId), ["bench_press_barbell", "lateral_raise_dumbbell"])
        XCTAssertEqual(workout.liveExercises[0].liveSets.map(\.weightKg), [80, 80])
        XCTAssertEqual(workout.liveExercises[1].restSeconds, 60)
        XCTAssertNotNil(template.lastUsedAt)
        XCTAssertNil(TemplateService.startWorkout(from: template, in: context), "only one workout at a time")
    }

    func testUpdateFromWorkoutUsesCompletedSets() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let template = TemplateService.save(pushDayDraft(), in: context)
        let workout = try XCTUnwrap(TemplateService.startWorkout(from: template, in: context))
        let bench = workout.liveExercises[0]
        bench.liveSets[0].weightKg = 85
        WorkoutService.toggle(bench.liveSets[0], previous: nil, in: context)
        WorkoutService.finish(workout, in: context)

        TemplateService.update(template, from: workout, in: context)

        let draft = TemplateService.draft(for: template)
        XCTAssertEqual(draft.exercises.map(\.exerciseId), ["bench_press_barbell"])
        XCTAssertEqual(draft.exercises[0].sets.map(\.targetWeightKg), [85])
    }

    func testDeletingAFolderKeepsItsTemplates() throws {
        let container = try makeContainer()
        let context = container.mainContext
        TemplateService.createFolder(named: "PPL", in: context)
        let folder = try XCTUnwrap(try context.fetch(FetchDescriptor<TemplateFolder>()).first)
        let template = TemplateService.save(pushDayDraft(), in: context)
        TemplateService.move(template, to: folder.id, in: context)

        TemplateService.delete(folder, in: context)

        XCTAssertNil(template.folderId)
        XCTAssertNil(template.deletedAt)
        XCTAssertNotNil(folder.deletedAt)
    }
}
