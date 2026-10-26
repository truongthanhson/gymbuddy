import Foundation

/// Rest after a set when neither the exercise in this workout nor the library exercise sets one.
let defaultRestSeconds = 120

/// Rest-time choices offered per exercise, in seconds (0 = off).
let restTimeOptions = [0, 30, 45, 60, 90, 120, 150, 180, 240, 300]

/// Picks the rest time for a set: per-workout override, then the exercise's default, then the app default. 0 = off.
func resolveRestSeconds(workoutExerciseRest: Int?, exerciseDefault: Int?, appDefault: Int = defaultRestSeconds) -> Int {
    max(workoutExerciseRest ?? exerciseDefault ?? appDefault, 0)
}

/// A running rest timer; mirrors the Android implementation.
struct RestTimerState: Equatable, Sendable {
    var endsAt: Date
    var totalSeconds: Int

    static func start(seconds: Int, now: Date = .now) -> RestTimerState? {
        seconds <= 0 ? nil : RestTimerState(endsAt: now.addingTimeInterval(TimeInterval(seconds)), totalSeconds: seconds)
    }

    func remainingSeconds(at now: Date = .now) -> Int {
        Int(max(endsAt.timeIntervalSince(now), 0).rounded(.up))
    }

    /// Fraction of the rest still to go, 1 → 0.
    func progress(at now: Date = .now) -> Double {
        totalSeconds <= 0 ? 0 : min(max(Double(remainingSeconds(at: now)) / Double(totalSeconds), 0), 1)
    }

    /// Adds (or with a negative delta removes) time; nil when that leaves no rest.
    func adjusted(by deltaSeconds: Int, now: Date = .now) -> RestTimerState? {
        let next = RestTimerState(
            endsAt: endsAt.addingTimeInterval(TimeInterval(deltaSeconds)),
            totalSeconds: max(totalSeconds + deltaSeconds, 1)
        )
        return next.remainingSeconds(at: now) <= 0 ? nil : next
    }
}
