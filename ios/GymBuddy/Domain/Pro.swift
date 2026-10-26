import Foundation

/// Routines a free user can keep; Pro removes the limit, like Strong. Matches Android.
let freeTemplateLimit = 3

/// What GymBuddy Pro unlocks. Everything else, including logging, history and sync, stays free.
enum ProFeature: CaseIterable, Sendable {
    case unlimitedTemplates, exerciseCharts, bodyMeasurements

    var label: LocalizedStringResource {
        switch self {
        case .unlimitedTemplates: "Unlimited routines (free: \(freeTemplateLimit))"
        case .exerciseCharts: "Charts of every exercise"
        case .bodyMeasurements: "Body fat and body part measurements"
        }
    }
}

func canAddTemplate(existing: Int, isPro: Bool) -> Bool { isPro || existing < freeTemplateLimit }

/// Body weight stays free; the other measurements need Pro.
func measurementNeedsPro(_ kind: MeasurementKind) -> Bool { kind != .weight }

/// Store product ids, the same on the App Store and Google Play.
enum ProProducts {
    static let monthly = "gymbuddy_pro_monthly"
    static let yearly = "gymbuddy_pro_yearly"
    static let lifetime = "gymbuddy_pro_lifetime"
    static let all = [monthly, yearly, lifetime]
}
