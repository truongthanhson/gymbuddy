import SwiftUI

/// UserDefaults keys for the settings screen; views read them with @AppStorage.
enum SettingsKey {
    static let weightUnit = "weightUnit"
    static let defaultRestSeconds = "defaultRestSeconds"
}

extension Binding where Value == Double? {
    /// Shows a kg value in `unit` and stores what the user types back as kg.
    func weight(in unit: WeightUnit) -> Binding<Double?> {
        let base = self
        return Binding(
            get: { base.wrappedValue.map(unit.display) },
            set: { base.wrappedValue = $0.map(unit.toKg) }
        )
    }
}
