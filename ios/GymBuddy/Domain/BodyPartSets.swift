import Foundation

/// A completed set for the muscle groups card: its exercise's body part key and when its workout started.
struct BodyPartSet: Equatable, Sendable {
    var bodyPart: String
    var startedAt: Date
    var warmup: Bool
}

struct BodyPartCount: Equatable, Sendable, Identifiable {
    var bodyPart: BodyPart
    var sets: Int
    var id: BodyPart { bodyPart }
}

/// Completed sets per body part since `since`, most first; warm-ups don't count.
/// Must match shared/test-vectors/body_part_sets.json (and Android).
func setsPerBodyPart(_ sets: [BodyPartSet], since: Date) -> [BodyPartCount] {
    var counts: [BodyPart: Int] = [:]
    for set in sets where !set.warmup && set.startedAt >= since {
        counts[BodyPart(key: set.bodyPart), default: 0] += 1
    }
    let order = BodyPart.allCases
    return counts.map { BodyPartCount(bodyPart: $0.key, sets: $0.value) }
        .sorted { a, b in
            a.sets != b.sets ? a.sets > b.sets : order.firstIndex(of: a.bodyPart)! < order.firstIndex(of: b.bodyPart)!
        }
}
