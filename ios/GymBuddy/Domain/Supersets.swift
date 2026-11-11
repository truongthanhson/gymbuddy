import Foundation

// Supersets are neighbouring exercises with the same group number; nil is not in a superset.
// Groups are given in exercise order (shared/test-vectors/supersets.json).

/// Whether a set of the exercise at `index` starts the rest timer: only the last exercise of a superset does.
func startsRest(groups: [Int?], index: Int) -> Bool {
    guard groups.indices.contains(index), let group = groups[index] else { return true }
    return index + 1 >= groups.count || groups[index + 1] != group
}

/// Joins the exercise at `index` and the one after it into a superset.
func supersetWithNext(groups: [Int?], index: Int) -> [Int?] {
    guard groups.indices.contains(index), groups.indices.contains(index + 1) else { return groups }
    let group = groups[index] ?? groups[index + 1] ?? ((groups.compactMap { $0 }.max() ?? 0) + 1)
    var result = groups
    result[index] = group
    result[index + 1] = group
    return result
}

/// Takes the exercise at `index` out of its superset; a superset left with one exercise ends.
func removeFromSuperset(groups: [Int?], index: Int) -> [Int?] {
    guard groups.indices.contains(index), let group = groups[index] else { return groups }
    var result = groups
    result[index] = nil
    if result.filter({ $0 == group }).count == 1 {
        result = result.map { $0 == group ? nil : $0 }
    }
    return result
}

/// Groups after the exercises were reordered: an exercise no longer next to another of its group leaves it.
func supersetsAfterReorder(groups: [Int?]) -> [Int?] {
    groups.indices.map { index in
        guard let group = groups[index] else { return nil }
        let before = index > 0 ? groups[index - 1] : nil
        let after = index + 1 < groups.count ? groups[index + 1] : nil
        return before == group || after == group ? group : nil
    }
}
