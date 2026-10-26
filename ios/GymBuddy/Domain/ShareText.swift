import Foundation

/// A completed set as shared text; `type` is the set type key ("warmup", "normal", "drop", "failure").
struct ShareSet: Equatable, Sendable {
    var type = "normal"
    var weightKg: Double?
    var reps: Int?
    var durationSec: Int?
    var distanceM: Double?
}

struct ShareExercise: Equatable, Sendable {
    var name: String
    var category: String
    var sets: [ShareSet]
}

/// "100 kg × 5", "× 12", "5 km · 25:00"; the values an exercise in `category` logs. Matches Android (share_text.json).
func setText(category: String, set: ShareSet, unit: WeightUnit) -> String {
    let parts: [String] = setFields(category: category).compactMap { field -> String? in
        switch field {
        case .weight: set.weightKg.map(unit.format)
        case .reps: set.reps.map { "× \($0)" }
        case .distance: set.distanceM.map { "\(formatWeight(unit.distanceUnit.display($0))) \(unit.distanceUnit.symbol)" }
        case .duration: set.durationSec.map(formatDuration)
        }
    }
    guard var text = parts.first else { return "—" }
    for part in parts.dropFirst() {
        text += part.hasPrefix("×") ? " \(part)" : " · \(part)"
    }
    return text
}

/// The text a finished workout is shared as, like Strong's "Share workout".
func workoutShareText(title: String, subtitle: String, exercises: [ShareExercise], unit: WeightUnit) -> String {
    var text = "\(title)\n\(subtitle)"
    for exercise in exercises where !exercise.sets.isEmpty {
        text += "\n\n\(exercise.name)"
        var number = 0
        for set in exercise.sets {
            let label: String
            switch set.type {
            case "warmup": label = "W"
            case "drop": label = "D"
            case "failure": label = "F"
            default:
                number += 1
                label = String(number)
            }
            text += "\n\(label): \(setText(category: exercise.category, set: set, unit: unit))"
        }
    }
    return text
}
