import Foundation

/// Phone ↔ watch protocol (shared/watch-protocol.md), used by the iPhone app and the watch app.
enum WatchLink {
    /// Key of the JSON string in the application context and in messages.
    static let jsonKey = "json"
}

struct WatchSet: Codable, Equatable, Sendable, Identifiable {
    var id: String
    var label: String
    var weight: String
    var reps: String
    var unit: String
    var done: Bool
    var hintWeight: String?
    var hintReps: String?
}

struct WatchExercise: Codable, Equatable, Sendable {
    var name: String
    var sets: [WatchSet]
}

struct WatchWorkout: Codable, Equatable, Sendable {
    var name: String
    /// Milliseconds since the epoch, like every time in the protocol.
    var startedAt: Int64
    var restEndsAt: Int64?
    var exercises: [WatchExercise]

    var startDate: Date { Date(timeIntervalSince1970: Double(startedAt) / 1000) }
    var restEndDate: Date? { restEndsAt.map { Date(timeIntervalSince1970: Double($0) / 1000) } }

    /// The first set not done yet, across exercises in order.
    var nextSet: (exerciseIndex: Int, set: WatchSet)? {
        for (index, exercise) in exercises.enumerated() {
            if let set = exercise.sets.first(where: { !$0.done }) { return (index, set) }
        }
        return nil
    }
}

/// What the phone shares with the watch; `workout` is nil when no workout is in progress.
struct WatchState: Codable, Equatable, Sendable {
    var workout: WatchWorkout?

    func encoded() -> String? {
        (try? JSONEncoder().encode(self)).flatMap { String(data: $0, encoding: .utf8) }
    }

    static func decode(_ json: String) -> WatchState? {
        try? JSONDecoder().decode(WatchState.self, from: Data(json.utf8))
    }
}

struct WatchAction: Codable, Equatable, Sendable {
    static let completeSet = "completeSet"
    static let skipRest = "skipRest"

    var action: String
    var setId: String?

    func encoded() -> String? {
        (try? JSONEncoder().encode(self)).flatMap { String(data: $0, encoding: .utf8) }
    }

    static func decode(_ json: String) -> WatchAction? {
        try? JSONDecoder().decode(WatchAction.self, from: Data(json.utf8))
    }
}

/// Set labels like Strong's: W, D, F for warm-up, drop and failure sets, else the number of the normal set.
func watchSetLabels(_ types: [String]) -> [String] {
    var normal = 0
    return types.map {
        switch $0 {
        case "warmup": return "W"
        case "drop": return "D"
        case "failure": return "F"
        default:
            normal += 1
            return String(normal)
        }
    }
}

extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
}
