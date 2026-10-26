import SwiftUI

/// A value a set can log.
enum SetField: String, CaseIterable, Sendable {
    case weight, reps, distance, duration
}

/// The values a set of an exercise in `category` logs, in column order (shared/test-vectors/set_fields.json).
func setFields(category: String) -> [SetField] {
    switch category {
    case ExerciseCategory.repsOnly.rawValue: [.reps]
    case ExerciseCategory.cardio.rawValue: [.distance, .duration]
    case ExerciseCategory.duration.rawValue: [.duration]
    default: [.weight, .reps]
    }
}

/// Seconds from "90", "1:30" or "1:05:09"; nil for nothing, zero or a malformed time.
func parseDuration(_ text: String) -> Int? {
    let parts = text.trimmingCharacters(in: .whitespaces).split(separator: ":", omittingEmptySubsequences: false)
    guard parts.count <= 3, parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isASCIIDigit) }) else { return nil }
    let numbers = parts.compactMap { Int($0) }
    guard numbers.count == parts.count else { return nil }
    // Everything after the first part is minutes or seconds of a clock time.
    guard numbers.dropFirst().allSatisfy({ $0 < 60 }) else { return nil }
    let seconds = numbers.reduce(0) { $0 * 60 + $1 }
    return seconds > 0 ? seconds : nil
}

func formatDuration(_ seconds: Int) -> String { formatElapsed(seconds) }

enum DistanceUnit: String, CaseIterable, Sendable {
    case km, mi

    var metersPerUnit: Double { self == .km ? 1000 : 1609.344 }
    var symbol: String { rawValue }

    /// Rounded to 2 decimals for display.
    func display(_ meters: Double) -> Double { (meters / metersPerUnit * 100).rounded() / 100 }
    func toMeters(_ value: Double) -> Double { value * metersPerUnit }
}

extension WeightUnit {
    /// Distances follow the weight unit: kilometers with kg, miles with lb.
    var distanceUnit: DistanceUnit { self == .lb ? .mi : .km }
}

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}

extension Binding where Value == Double? {
    /// Shows meters in `unit` and stores what the user types back as meters.
    func distance(in unit: DistanceUnit) -> Binding<Double?> {
        let base = self
        return Binding(
            get: { base.wrappedValue.map(unit.display) },
            set: { base.wrappedValue = $0.map(unit.toMeters) }
        )
    }
}
