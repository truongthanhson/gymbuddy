/// Weights are stored in kg; other units are for display only.
enum WeightUnit: String, CaseIterable, Sendable {
    case kg
    case lb

    var perKg: Double {
        switch self {
        case .kg: 1
        case .lb: 2.20462262185
        }
    }

    func fromKg(_ kg: Double) -> Double { kg * perKg }
    func toKg(_ value: Double) -> Double { value / perKg }
}
