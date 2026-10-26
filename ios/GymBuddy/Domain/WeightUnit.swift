import Foundation

/// Weights are stored in kg; other units are for display only.
enum WeightUnit: String, CaseIterable, Identifiable, Sendable {
    case kg
    case lb

    var id: String { rawValue }

    var perKg: Double {
        switch self {
        case .kg: 1
        case .lb: 2.20462262185
        }
    }

    var symbol: String { rawValue }

    func fromKg(_ kg: Double) -> Double { kg * perKg }
    func toKg(_ value: Double) -> Double { value / perKg }

    /// A stored kg value in this unit, rounded to 2 decimals so what the user typed survives the round trip.
    func display(_ kg: Double) -> Double { (fromKg(kg) * 100).rounded() / 100 }

    /// "102.5 kg" / "225 lb".
    func format(_ kg: Double) -> String { "\(formatWeight(display(kg))) \(symbol)" }
}
