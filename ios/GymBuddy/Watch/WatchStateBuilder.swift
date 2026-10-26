import Foundation
import SwiftData

/// The active workout as the watch sees it.
@MainActor
enum WatchStateBuilder {
    /// `previous` gives the last session's sets of an exercise, used as hints for sets not filled in yet.
    static func state(
        for workout: Workout?,
        exercises: [String: Exercise],
        previous: (String) -> [WorkoutSet],
        unit: WeightUnit,
        restEndsAt: Date?
    ) -> WatchState {
        guard let workout else { return WatchState(workout: nil) }
        func weight(_ kg: Double?) -> String { kg.map { formatWeight(unit.display($0)) } ?? "" }
        return WatchState(workout: WatchWorkout(
            name: workout.name,
            startedAt: workout.startedAt.epochMillis,
            restEndsAt: restEndsAt?.epochMillis,
            exercises: workout.liveExercises.map { item in
                let sets = item.liveSets
                let labels = watchSetLabels(sets.map(\.setTypeRaw))
                let hints = previous(item.exerciseId)
                return WatchExercise(
                    name: exercises[item.exerciseId]?.displayName ?? item.exerciseId,
                    sets: sets.enumerated().map { index, set in
                        let hint = hints.indices.contains(index) ? hints[index] : nil
                        return WatchSet(
                            id: set.id.uuidString,
                            label: labels[index],
                            weight: weight(set.weightKg),
                            reps: set.reps.map { String($0) } ?? "",
                            unit: unit.symbol,
                            done: set.completed,
                            hintWeight: hint?.weightKg.map { formatWeight(unit.display($0)) },
                            hintReps: hint?.reps.map { String($0) }
                        )
                    }
                )
            }
        ))
    }

    static func state(in context: ModelContext, unit: WeightUnit, restEndsAt: Date?) -> WatchState {
        let workout = WorkoutService.activeWorkout(in: context)
        let ids = Set(workout?.liveExercises.map(\.exerciseId) ?? [])
        let found = (try? context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
        return state(
            for: workout,
            exercises: Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first }),
            previous: { WorkoutService.previousSets(for: $0, in: context) },
            unit: unit,
            restEndsAt: restEndsAt
        )
    }
}
