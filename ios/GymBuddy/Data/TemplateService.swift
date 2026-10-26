import Foundation
import SwiftData

/// A template being edited; saved in one go so the editor can be cancelled.
struct TemplateDraft: Equatable {
    struct ExerciseDraft: Equatable, Identifiable {
        var id = UUID()
        var exerciseId: String
        var restSeconds: Int?
        var supersetGroup: Int?
        var sets: [SetDraft] = [SetDraft()]
    }

    struct SetDraft: Equatable, Identifiable {
        var id = UUID()
        var targetWeightKg: Double?
        var targetReps: Int?
    }

    var id: UUID?
    var name = ""
    var note: String?
    var folderId: UUID?
    var exercises: [ExerciseDraft] = []

    var canSave: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty && !exercises.isEmpty }
}

/// Routines (templates) and their folders. Every write bumps updatedAt and deletes are soft, for sync.
@MainActor
enum TemplateService {
    static func template(id: UUID, in context: ModelContext) -> WorkoutTemplate? {
        let descriptor = FetchDescriptor<WorkoutTemplate>(predicate: #Predicate { $0.id == id })
        return try? context.fetch(descriptor).first
    }

    static func draft(for template: WorkoutTemplate) -> TemplateDraft {
        TemplateDraft(
            id: template.id,
            name: template.name,
            note: template.note,
            folderId: template.folderId,
            exercises: template.liveExercises.map { e in
                TemplateDraft.ExerciseDraft(
                    id: e.id,
                    exerciseId: e.exerciseId,
                    restSeconds: e.restSeconds,
                    supersetGroup: e.supersetGroup,
                    sets: e.liveSets.map { TemplateDraft.SetDraft(id: $0.id, targetWeightKg: $0.targetWeightKg, targetReps: $0.targetReps) }
                )
            }
        )
    }

    /// Writes the draft; rows the draft no longer has are soft-deleted.
    @discardableResult
    static func save(_ draft: TemplateDraft, in context: ModelContext, now: Date = .now) -> WorkoutTemplate {
        let template: WorkoutTemplate
        if let id = draft.id, let existing = self.template(id: id, in: context) {
            template = existing
        } else {
            let order = ((try? context.fetch(FetchDescriptor<WorkoutTemplate>()))?.map(\.sortOrder).max() ?? -1) + 1
            template = WorkoutTemplate(id: draft.id ?? UUID(), name: draft.name, sortOrder: order, now: now)
            context.insert(template)
        }
        template.name = draft.name.trimmingCharacters(in: .whitespaces)
        template.note = draft.note
        template.folderId = draft.folderId
        template.updatedAt = now

        let keptExercises = Set(draft.exercises.map(\.id))
        let keptSets = Set(draft.exercises.flatMap { $0.sets.map(\.id) })
        for old in template.liveExercises {
            if !keptExercises.contains(old.id) {
                old.deletedAt = now
                old.updatedAt = now
            }
            for set in old.liveSets where !keptSets.contains(set.id) {
                set.deletedAt = now
                set.updatedAt = now
            }
        }

        let existingExercises = Dictionary(template.exercises.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for (index, e) in draft.exercises.enumerated() {
            let item: TemplateExercise
            if let found = existingExercises[e.id] {
                item = found
            } else {
                item = TemplateExercise(id: e.id, exerciseId: e.exerciseId, sortOrder: index, now: now)
                context.insert(item)
                item.template = template
            }
            item.exerciseId = e.exerciseId
            item.sortOrder = index
            item.restSeconds = e.restSeconds
            item.supersetGroup = e.supersetGroup
            item.deletedAt = nil
            item.updatedAt = now

            let existingSets = Dictionary(item.sets.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            for (setIndex, s) in e.sets.enumerated() {
                let set: TemplateSet
                if let found = existingSets[s.id] {
                    set = found
                } else {
                    set = TemplateSet(id: s.id, sortOrder: setIndex, now: now)
                    context.insert(set)
                    set.templateExercise = item
                }
                set.sortOrder = setIndex
                set.targetWeightKg = s.targetWeightKg
                set.targetReps = s.targetReps
                set.deletedAt = nil
                set.updatedAt = now
            }
        }
        save(context)
        return template
    }

    static func delete(_ template: WorkoutTemplate, in context: ModelContext, now: Date = .now) {
        template.deletedAt = now
        template.updatedAt = now
        save(context)
    }

    static func move(_ template: WorkoutTemplate, to folderId: UUID?, in context: ModelContext, now: Date = .now) {
        template.folderId = folderId
        template.updatedAt = now
        save(context)
    }

    @discardableResult
    static func duplicate(_ template: WorkoutTemplate, copySuffix: String, in context: ModelContext) -> WorkoutTemplate {
        var copy = draft(for: template)
        copy.id = nil
        copy.name = "\(template.name) \(copySuffix)"
        copy.exercises = copy.exercises.map { e in
            var e = e
            e.id = UUID()
            e.sets = e.sets.map { var s = $0; s.id = UUID(); return s }
            return e
        }
        return save(copy, in: context)
    }

    static func createFolder(named name: String, in context: ModelContext, now: Date = .now) {
        let order = ((try? context.fetch(FetchDescriptor<TemplateFolder>()))?.map(\.sortOrder).max() ?? -1) + 1
        context.insert(TemplateFolder(name: name, sortOrder: order, now: now))
        save(context)
    }

    static func rename(_ folder: TemplateFolder, to name: String, in context: ModelContext, now: Date = .now) {
        folder.name = name
        folder.updatedAt = now
        save(context)
    }

    /// Deletes the folder; its templates move out of it rather than being deleted.
    static func delete(_ folder: TemplateFolder, in context: ModelContext, now: Date = .now) {
        let folderId: UUID? = folder.id
        let descriptor = FetchDescriptor<WorkoutTemplate>(predicate: #Predicate { $0.folderId == folderId })
        for template in (try? context.fetch(descriptor)) ?? [] {
            template.folderId = nil
            template.updatedAt = now
        }
        folder.deletedAt = now
        folder.updatedAt = now
        save(context)
    }

    /// Starts a workout pre-filled with the template's exercises and target sets; nil if one is already running.
    @discardableResult
    static func startWorkout(from template: WorkoutTemplate, in context: ModelContext, now: Date = .now) -> Workout? {
        guard WorkoutService.activeWorkout(in: context) == nil else { return nil }
        let workout = Workout(name: template.name, startedAt: now)
        workout.templateId = template.id
        context.insert(workout)
        for (index, e) in template.liveExercises.enumerated() {
            let item = WorkoutExercise(exerciseId: e.exerciseId, sortOrder: index, now: now)
            item.restSeconds = e.restSeconds
            item.supersetGroup = e.supersetGroup
            context.insert(item)
            item.workout = workout
            let sets = e.liveSets
            if sets.isEmpty {
                let set = WorkoutSet(sortOrder: 0, now: now)
                context.insert(set)
                set.workoutExercise = item
            }
            for (setIndex, s) in sets.enumerated() {
                let set = WorkoutSet(sortOrder: setIndex, weightKg: s.targetWeightKg, reps: s.targetReps, now: now)
                set.setTypeRaw = s.setTypeRaw
                context.insert(set)
                set.workoutExercise = item
            }
        }
        template.lastUsedAt = now
        template.updatedAt = now
        save(context)
        return workout
    }

    /// Replaces the template's exercises and targets with what was done in the workout (completed sets only).
    static func update(_ template: WorkoutTemplate, from workout: Workout, in context: ModelContext, now: Date = .now) {
        var updated = draft(for: template)
        var reuse = Dictionary(grouping: updated.exercises, by: \.exerciseId)
        updated.exercises = workout.liveExercises.compactMap { e in
            let done = e.liveSets.filter(\.completed)
            guard !done.isEmpty else { return nil }
            // Keep the id of a matching template exercise so the row is updated instead of replaced.
            let previous = reuse[e.exerciseId]?.isEmpty == false ? reuse[e.exerciseId]?.removeFirst() : nil
            return TemplateDraft.ExerciseDraft(
                id: previous?.id ?? UUID(),
                exerciseId: e.exerciseId,
                restSeconds: e.restSeconds,
                supersetGroup: e.supersetGroup,
                sets: done.enumerated().map { i, s in
                    TemplateDraft.SetDraft(
                        id: previous.flatMap { $0.sets.indices.contains(i) ? $0.sets[i].id : nil } ?? UUID(),
                        targetWeightKg: s.weightKg,
                        targetReps: s.reps
                    )
                }
            )
        }
        save(updated, in: context, now: now)
    }

    /// Saves a finished workout as a new template.
    @discardableResult
    static func createTemplate(from workout: Workout, in context: ModelContext, now: Date = .now) -> WorkoutTemplate {
        let newDraft = TemplateDraft(
            name: workout.name,
            exercises: workout.liveExercises.map { e in
                let done = e.liveSets.filter(\.completed)
                return TemplateDraft.ExerciseDraft(
                    exerciseId: e.exerciseId,
                    restSeconds: e.restSeconds,
                    supersetGroup: e.supersetGroup,
                    sets: (done.isEmpty ? e.liveSets : done).map { TemplateDraft.SetDraft(targetWeightKg: $0.weightKg, targetReps: $0.reps) }
                )
            }
        )
        return save(newDraft, in: context, now: now)
    }

    private static func save(_ context: ModelContext) {
        do {
            try context.save()
        } catch {
            assertionFailure("Save failed: \(error)")
        }
    }
}
