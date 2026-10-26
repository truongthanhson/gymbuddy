import Foundation

/// RPE choices offered for a set, 6 to 10 in half steps, like Strong.
let rpeOptions: [Double] = (12...20).map { Double($0) / 2 }

/// "8" or "8.5".
func formatRpe(_ rpe: Double) -> String { formatWeight(rpe) }

extension SetType {
    var title: String {
        switch self {
        case .warmup: String(localized: "Warm-up set")
        case .normal: String(localized: "Normal set")
        case .drop: String(localized: "Drop set")
        case .failure: String(localized: "Failure set")
        }
    }
}
