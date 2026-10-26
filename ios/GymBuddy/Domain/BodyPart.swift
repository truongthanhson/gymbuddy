import Foundation

enum BodyPart: String, CaseIterable, Identifiable, Sendable {
    case chest, back, legs, shoulders, arms, core, olympic
    case fullBody = "full_body"
    case cardio, other

    var id: String { rawValue }

    init(key: String) {
        self = BodyPart(rawValue: key) ?? .other
    }

    var label: LocalizedStringResource {
        switch self {
        case .chest: "Chest"
        case .back: "Back"
        case .legs: "Legs"
        case .shoulders: "Shoulders"
        case .arms: "Arms"
        case .core: "Core"
        case .olympic: "Olympic"
        case .fullBody: "Full body"
        case .cardio: "Cardio"
        case .other: "Other"
        }
    }
}
