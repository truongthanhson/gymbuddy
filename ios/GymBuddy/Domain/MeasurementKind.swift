import SwiftUI

/// How a measurement is stored; body weight is in kg and shown in the user's unit.
enum MeasurementUnit: String, Sendable {
    case kg, percent, cm
}

/// Body measurements; raw values match the `kind` column in supabase/migrations (and Android).
enum MeasurementKind: String, CaseIterable, Identifiable, Sendable {
    case weight
    case bodyFat = "body_fat"
    case neck, chest, waist, hips
    case armLeft = "arm_left"
    case armRight = "arm_right"
    case thighLeft = "thigh_left"
    case thighRight = "thigh_right"
    case calfLeft = "calf_left"
    case calfRight = "calf_right"

    var id: String { rawValue }

    var unit: MeasurementUnit {
        switch self {
        case .weight: .kg
        case .bodyFat: .percent
        default: .cm
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .weight: "Body weight"
        case .bodyFat: "Body fat"
        case .neck: "Neck"
        case .chest: "Chest"
        case .waist: "Waist"
        case .hips: "Hips"
        case .armLeft: "Left arm"
        case .armRight: "Right arm"
        case .thighLeft: "Left thigh"
        case .thighRight: "Right thigh"
        case .calfLeft: "Left calf"
        case .calfRight: "Right calf"
        }
    }

    func symbol(_ weightUnit: WeightUnit) -> String {
        switch unit {
        case .kg: weightUnit.symbol
        case .percent: "%"
        case .cm: "cm"
        }
    }

    /// A stored value in the unit the user types in.
    func display(_ value: Double, _ weightUnit: WeightUnit) -> Double {
        unit == .kg ? weightUnit.display(value) : (value * 100).rounded() / 100
    }

    /// What the user typed, converted to the stored unit.
    func toStored(_ typed: Double, _ weightUnit: WeightUnit) -> Double {
        unit == .kg ? weightUnit.toKg(typed) : typed
    }

    /// "80.5 kg", "18.2 %", "38 cm".
    func format(_ value: Double, _ weightUnit: WeightUnit) -> String {
        "\(formatWeight(display(value, weightUnit))) \(symbol(weightUnit))"
    }
}
