import Foundation
import SwiftData

/// One body measurement (synced as `measurements`); `kind` and `unit` use the keys of `MeasurementKind`.
/// Not called `Measurement`, which Foundation already uses.
@Model
final class BodyMeasurement {
    @Attribute(.unique) var id: UUID
    var kind: String
    var value: Double
    var unit: String
    var measuredAt: Date
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    init(id: UUID = UUID(), kind: String, value: Double, unit: String, measuredAt: Date = .now, now: Date = .now) {
        self.id = id
        self.kind = kind
        self.value = value
        self.unit = unit
        self.measuredAt = measuredAt
        self.createdAt = now
        self.updatedAt = now
    }
}
