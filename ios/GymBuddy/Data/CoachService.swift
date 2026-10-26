import Foundation

/// What Buddy knows right now, from the finished workouts.
struct CoachSnapshot {
    var insights: [CoachInsight] = []
    var recovery: [Recovery] = []
    /// Next target per exercise id.
    var targets: [String: SetTarget] = [:]
    var workoutCount = 0
}

/// Default weekly goal until the goal profile lets people pick one.
let defaultWorkoutsPerWeek = 3

enum CoachService {
    /// The coach's sessions per exercise id, from finished workouts.
    static func sessions(of workouts: [Workout]) -> [String: [CoachSession]] {
        var out: [String: [CoachSession]] = [:]
        for workout in workouts {
            for item in workout.liveExercises {
                let done = item.liveSets.filter(\.completed)
                guard !done.isEmpty else { continue }
                out[item.exerciseId, default: []].append(CoachSession(
                    startedAt: workout.startedAt,
                    sets: done.map { CoachSet(weightKg: $0.weightKg, reps: $0.reps, rpe: $0.rpe, warmup: $0.setType == .warmup) }
                ))
            }
        }
        return out
    }

    static func snapshot(
        workouts: [Workout],
        exercises: [String: Exercise],
        goalPerWeek: Int = defaultWorkoutsPerWeek,
        now: Date = .now
    ) -> CoachSnapshot {
        let finished = workouts.filter { $0.endedAt != nil && $0.deletedAt == nil }
        var coachExercises: [CoachExercise] = []
        var partSets: [BodyPartSet] = []
        for (id, sessions) in sessions(of: finished) {
            guard let exercise = exercises[id] else { continue }
            coachExercises.append(CoachExercise(exerciseId: id, bodyPart: exercise.bodyPart, category: exercise.category, sessions: sessions))
            for session in sessions {
                partSets += session.sets.map { BodyPartSet(bodyPart: exercise.bodyPart, startedAt: session.startedAt, warmup: $0.warmup) }
            }
        }
        var targets: [String: SetTarget] = [:]
        for e in coachExercises {
            targets[e.exerciseId] = nextTarget(e.sessions, bodyPart: e.bodyPart, category: e.category)
        }
        return CoachSnapshot(
            insights: coachInsights(
                exercises: coachExercises,
                bodyPartSets: partSets,
                workoutStarts: finished.map(\.startedAt),
                goalPerWeek: goalPerWeek,
                now: now
            ),
            recovery: recovery(partSets, now: now),
            targets: targets,
            workoutCount: finished.count
        )
    }
}
