import Foundation

/// How an exercise is logged; raw values match shared/exercises.json.
enum ExerciseCategory: String, CaseIterable, Identifiable, Sendable {
    case barbell, dumbbell, machine, cable
    case weightedBodyweight = "weighted_bodyweight"
    case assistedBodyweight = "assisted_bodyweight"
    case repsOnly = "reps_only"
    case cardio, duration

    var id: String { rawValue }

    init(key: String) {
        self = ExerciseCategory(rawValue: key) ?? .barbell
    }

    var label: LocalizedStringResource {
        switch self {
        case .barbell: "Barbell"
        case .dumbbell: "Dumbbell"
        case .machine: "Machine"
        case .cable: "Cable"
        case .weightedBodyweight: "Weighted bodyweight"
        case .assistedBodyweight: "Assisted bodyweight"
        case .repsOnly: "Reps only"
        case .cardio: "Cardio"
        case .duration: "Duration"
        }
    }
}
