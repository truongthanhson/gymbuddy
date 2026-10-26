import Foundation

extension WeightUnit {
    /// Plates in this unit; must match shared/test-vectors/calculators.json (and Android).
    var defaultPlates: [Double] {
        switch self {
        case .kg: [25, 20, 15, 10, 5, 2.5, 1.25]
        case .lb: [45, 35, 25, 10, 5, 2.5]
        }
    }

    var defaultBar: Double {
        switch self {
        case .kg: 20
        case .lb: 45
        }
    }
}

/// `perSide` is heaviest first; `remainder` is what the plates could not make (negative below the bar).
struct PlateLoad: Equatable, Sendable {
    var perSide: [Double]
    var loaded: Double
    var remainder: Double
}

private func round4(_ value: Double) -> Double { (value * 10_000).rounded() / 10_000 }

/// Loads plates greedily, heaviest first, on each side of the bar.
func plateLoad(target: Double, bar: Double, plates: [Double]) -> PlateLoad {
    var perSide: [Double] = []
    var side = (target - bar) / 2
    for plate in plates.filter({ $0 > 0 }).sorted(by: >) {
        while side >= plate - 1e-9 {
            perSide.append(plate)
            side -= plate
        }
    }
    let loaded = round4(bar + 2 * perSide.reduce(0, +))
    return PlateLoad(perSide: perSide, loaded: loaded, remainder: round4(target - loaded))
}

/// Weight you could lift for `reps` given a one-rep max (Epley inverted).
func repMaxWeight(oneRepMax: Double, reps: Int) -> Double {
    reps <= 1 ? oneRepMax : oneRepMax / (1 + Double(reps) / 30)
}
