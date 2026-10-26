import Foundation

/// One finished workout's sets of a single exercise.
struct ExerciseSession: Sendable {
    var startedAt: Date
    var sets: [SetValues]
}

/// A best value and the workout that first reached it.
struct Record: Equatable, Sendable {
    var value: Double
    var startedAt: Date
}

struct ExerciseRecords: Equatable, Sendable {
    var estimatedOneRepMax: Record?
    var maxWeight: Record?
    /// Heaviest single set by weight × reps.
    var maxSetVolume: Record?
    var maxReps: Record?
    /// Most weight × reps in one workout.
    var maxSessionVolume: Record?
}

/// One chart point per workout.
struct SessionPoint: Equatable, Sendable {
    var startedAt: Date
    var estimatedOneRepMax: Double
    var maxWeight: Double
    var volumeKg: Double
}

/// Records and chart points for an exercise; must match shared/test-vectors/exercise_records.json (and Android).
/// Only completed sets with reps > 0 count; a record belongs to the first workout that reached it.
func exerciseProgress(_ sessions: [ExerciseSession]) -> (records: ExerciseRecords, points: [SessionPoint]) {
    var records = ExerciseRecords()
    var points: [SessionPoint] = []
    func better(_ current: Record?, _ value: Double, _ at: Date) -> Record {
        if let current, value <= current.value { return current }
        return Record(value: value, startedAt: at)
    }

    for session in sessions.sorted(by: { $0.startedAt < $1.startedAt }) {
        let sets = session.sets.filter { $0.completed && ($0.reps ?? 0) > 0 }
        guard !sets.isEmpty else { continue }
        let at = session.startedAt
        let bestOneRepMax = sets.map { estimatedOneRepMax(weightKg: $0.weightKg ?? 0, reps: $0.reps ?? 0) }.max() ?? 0
        let maxWeight = sets.map { $0.weightKg ?? 0 }.max() ?? 0
        let maxSetVolume = sets.map { ($0.weightKg ?? 0) * Double($0.reps ?? 0) }.max() ?? 0
        let maxReps = Double(sets.map { $0.reps ?? 0 }.max() ?? 0)
        let volume = volumeKg(sets)
        records = ExerciseRecords(
            estimatedOneRepMax: better(records.estimatedOneRepMax, bestOneRepMax, at),
            maxWeight: better(records.maxWeight, maxWeight, at),
            maxSetVolume: better(records.maxSetVolume, maxSetVolume, at),
            maxReps: better(records.maxReps, maxReps, at),
            maxSessionVolume: better(records.maxSessionVolume, volume, at)
        )
        points.append(SessionPoint(startedAt: at, estimatedOneRepMax: bestOneRepMax, maxWeight: maxWeight, volumeKg: volume))
    }
    return (records, points)
}
