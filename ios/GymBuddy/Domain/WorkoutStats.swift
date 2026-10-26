import Foundation

/// The numbers of a set that matter for stats; mirrors the Android implementation.
struct SetValues: Equatable, Sendable {
    var weightKg: Double?
    var reps: Int?
    var completed: Bool
}

/// Total volume in kg: weight × reps over completed sets.
func volumeKg(_ sets: [SetValues]) -> Double {
    sets.filter(\.completed).reduce(0) { $0 + ($1.weightKg ?? 0) * Double($1.reps ?? 0) }
}

/// Index of the completed set with the highest estimated 1RM (ties go to the earlier set), or nil.
func bestSetIndex(_ sets: [SetValues]) -> Int? {
    var best: Int?
    var bestValue = -Double.infinity
    for (index, set) in sets.enumerated() {
        let reps = set.reps ?? 0
        guard set.completed, reps > 0 else { continue }
        let value = estimatedOneRepMax(weightKg: set.weightKg ?? 0, reps: reps)
        if value > bestValue {
            best = index
            bestValue = value
        }
    }
    return best
}

/// "1:05:09" or "42:07" for an elapsed duration.
func formatElapsed(_ totalSeconds: Int) -> String {
    let s = max(totalSeconds, 0)
    let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
    return h > 0
        ? String(format: "%d:%02d:%02d", h, m, sec)
        : String(format: "%d:%02d", m, sec)
}

/// Weight without a trailing ".0": 100 → "100", 102.5 → "102.5".
func formatWeight(_ value: Double) -> String {
    if value.rounded() == value { return String(Int(value)) }
    var text = String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), value)
    while text.hasSuffix("0") { text.removeLast() }
    if text.hasSuffix(".") { text.removeLast() }
    return text
}
