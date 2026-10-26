import Foundation
import SwiftData

/// All writes to workouts go through here so timestamps and soft deletes stay consistent for sync.
@MainActor
enum WorkoutService {
    static func activeWorkout(in context: ModelContext) -> Workout? {
        var descriptor = FetchDescriptor<Workout>(
            predicate: #Predicate { $0.endedAt == nil && $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
        )
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    /// Starts a new workout, or returns the one already in progress.
    @discardableResult
    static func startEmptyWorkout(name: String, in context: ModelContext, now: Date = .now) -> Workout {
        if let active = activeWorkout(in: context) { return active }
        let workout = Workout(name: name, startedAt: now)
        context.insert(workout)
        save(context)
        return workout
    }

    static func addExercises(_ exerciseIds: [String], to workout: Workout, in context: ModelContext, now: Date = .now) {
        var order = workout.exercises.map(\.sortOrder).max() ?? -1
        for exerciseId in exerciseIds {
            order += 1
            let item = WorkoutExercise(exerciseId: exerciseId, sortOrder: order, now: now)
            context.insert(item)
            item.workout = workout
            let set = WorkoutSet(sortOrder: 0, now: now)
            context.insert(set)
            set.workoutExercise = item
        }
        workout.updatedAt = now
        save(context)
    }

    /// Adds a set that starts with the values of the last set, like Strong does.
    @discardableResult
    static func addSet(to item: WorkoutExercise, in context: ModelContext, now: Date = .now) -> WorkoutSet {
        let last = item.liveSets.last
        let set = WorkoutSet(
            sortOrder: (last?.sortOrder ?? -1) + 1,
            weightKg: last?.weightKg,
            reps: last?.reps,
            durationSec: last?.durationSec,
            distanceM: last?.distanceM,
            now: now
        )
        context.insert(set)
        set.workoutExercise = item
        save(context)
        return set
    }

    /// Puts warm-up sets (weight in kg, reps) before the exercise's sets.
    static func insertWarmupSets(_ sets: [(weightKg: Double, reps: Int)], into item: WorkoutExercise, in context: ModelContext, now: Date = .now) {
        guard !sets.isEmpty else { return }
        for set in item.liveSets {
            set.sortOrder += sets.count
            set.updatedAt = now
        }
        for (index, warmup) in sets.enumerated() {
            let set = WorkoutSet(sortOrder: index, weightKg: warmup.weightKg, reps: warmup.reps, now: now)
            set.setType = .warmup
            context.insert(set)
            set.workoutExercise = item
        }
        save(context)
    }

    /// Ticking an empty set fills it from the previous session.
    static func toggle(_ set: WorkoutSet, previous: WorkoutSet?, in context: ModelContext, now: Date = .now) {
        if !set.completed, set.weightKg == nil, set.reps == nil, set.durationSec == nil, set.distanceM == nil, let previous {
            set.weightKg = previous.weightKg
            set.reps = previous.reps
            set.durationSec = previous.durationSec
            set.distanceM = previous.distanceM
        }
        set.completed.toggle()
        set.completedAt = set.completed ? now : nil
        set.updatedAt = now
        save(context)
    }

    static func setType(_ type: SetType, for set: WorkoutSet, in context: ModelContext, now: Date = .now) {
        set.setType = type
        set.updatedAt = now
        save(context)
    }

    static func setRpe(_ rpe: Double?, for set: WorkoutSet, in context: ModelContext, now: Date = .now) {
        set.rpe = rpe
        set.updatedAt = now
        save(context)
    }

    /// A blank note is stored as no note.
    static func setNote(_ note: String, for item: WorkoutExercise, in context: ModelContext, now: Date = .now) {
        item.note = note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : note
        item.updatedAt = now
        save(context)
    }

    static func setNote(_ note: String, for workout: Workout, in context: ModelContext, now: Date = .now) {
        workout.note = note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : note
        workout.updatedAt = now
        save(context)
    }

    static func delete(_ set: WorkoutSet, in context: ModelContext, now: Date = .now) {
        set.deletedAt = now
        set.updatedAt = now
        save(context)
    }

    static func remove(_ item: WorkoutExercise, in context: ModelContext, now: Date = .now) {
        item.deletedAt = now
        item.updatedAt = now
        save(context)
    }

    /// Ends the workout; sets that were never ticked are dropped. With nothing ticked, the workout is discarded.
    static func finish(_ workout: Workout, in context: ModelContext, now: Date = .now) {
        let anyCompleted = workout.liveExercises.contains { $0.liveSets.contains(where: \.completed) }
        guard anyCompleted else {
            discard(workout, in: context, now: now)
            return
        }
        for item in workout.liveExercises {
            let sets = item.liveSets
            for set in sets where !set.completed {
                set.deletedAt = now
                set.updatedAt = now
            }
            if !sets.contains(where: \.completed) {
                item.deletedAt = now
                item.updatedAt = now
            }
        }
        workout.endedAt = now
        workout.updatedAt = now
        save(context)
    }

    static func discard(_ workout: Workout, in context: ModelContext, now: Date = .now) {
        workout.deletedAt = now
        workout.updatedAt = now
        save(context)
    }

    /// Rest time after each set of this exercise in this workout; 0 turns the timer off.
    static func setRestSeconds(_ seconds: Int, for item: WorkoutExercise, in context: ModelContext, now: Date = .now) {
        item.restSeconds = seconds
        item.updatedAt = now
        save(context)
    }

    static func rename(_ workout: Workout, to name: String, in context: ModelContext, now: Date = .now) {
        workout.name = name
        workout.updatedAt = now
        save(context)
    }

    /// Completed sets of `exerciseId` from the most recent finished workout that included it.
    static func previousSets(for exerciseId: String, in context: ModelContext) -> [WorkoutSet] {
        let id = exerciseId
        let descriptor = FetchDescriptor<WorkoutExercise>(
            predicate: #Predicate { $0.exerciseId == id && $0.deletedAt == nil }
        )
        let candidates = ((try? context.fetch(descriptor)) ?? []).filter {
            $0.workout?.endedAt != nil && $0.workout?.deletedAt == nil
        }
        let latest = candidates.max { ($0.workout?.startedAt ?? .distantPast) < ($1.workout?.startedAt ?? .distantPast) }
        return latest?.liveSets.filter(\.completed) ?? []
    }

    private static func save(_ context: ModelContext) {
        do {
            try context.save()
        } catch {
            assertionFailure("Save failed: \(error)")
        }
    }
}
