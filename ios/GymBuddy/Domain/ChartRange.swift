import Foundation

/// Time ranges a chart can zoom to, counted back from its newest point (not from today), so old data still fills the chart.
/// Must match shared/test-vectors/chart_range.json (and Android).
enum ChartRange: String, CaseIterable, Identifiable {
    case oneMonth = "1M"
    case threeMonths = "3M"
    case sixMonths = "6M"
    case oneYear = "1Y"
    case all = "ALL"

    var id: String { rawValue }

    var days: Int? {
        switch self {
        case .oneMonth: 30
        case .threeMonths: 91
        case .sixMonths: 182
        case .oneYear: 365
        case .all: nil
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .oneMonth: "1M"
        case .threeMonths: "3M"
        case .sixMonths: "6M"
        case .oneYear: "1Y"
        case .all: "All"
        }
    }

    /// The points of `points` (oldest first) inside this range.
    func filter<T>(_ points: [T], date: (T) -> Date) -> [T] {
        guard let days, let last = points.last else { return points }
        let cutoff = date(last).addingTimeInterval(-Double(days) * 24 * 60 * 60)
        return points.filter { date($0) >= cutoff }
    }

    /// The ranges worth offering: each keeps at least two points and fewer than all of them; `.all` is always there.
    static func available<T>(_ points: [T], date: (T) -> Date) -> [ChartRange] {
        allCases.filter { range in
            let kept = range.filter(points, date: date).count
            return range == .all || (kept >= 2 && kept < points.count)
        }
    }
}

/// Index of the value closest to `x` in `xs` (sorted ascending), the earlier one on a tie; nil when empty.
func nearestIndex(_ xs: [Double], to x: Double) -> Int? {
    guard !xs.isEmpty else { return nil }
    var best = 0
    for i in xs.indices.dropFirst() where abs(xs[i] - x) < abs(xs[best] - x) {
        best = i
    }
    return best
}
