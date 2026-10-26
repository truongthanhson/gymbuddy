import SwiftUI

/// UserDefaults keys for the settings screen; views read them with @AppStorage.
enum SettingsKey {
    static let weightUnit = "weightUnit"
    static let defaultRestSeconds = "defaultRestSeconds"
    static let healthEnabled = "healthEnabled"
    static let theme = "theme"
}

/// Light or dark colors; `.system` follows the phone.
enum ThemeMode: String, CaseIterable, Identifiable, Sendable {
    case system, light, dark

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
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
