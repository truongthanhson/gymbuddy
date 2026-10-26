import Foundation
import SwiftData

/// A built-in (seeded from shared/exercises.json) or custom exercise.
/// Built-in ids are stable slugs; custom ids are UUID strings.
@Model
final class Exercise {
    @Attribute(.unique) var id: String
    var name: String
    var nameVi: String?
    var category: String
    var bodyPart: String
    var primaryMuscle: String?
    var secondaryMuscles: [String]
    var defaultRestSeconds: Int?
    var isCustom: Bool
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    init(
        id: String,
        name: String,
        nameVi: String? = nil,
        category: String,
        bodyPart: String,
        primaryMuscle: String? = nil,
        secondaryMuscles: [String] = [],
        defaultRestSeconds: Int? = nil,
        isCustom: Bool,
        createdAt: Date = .now,
        updatedAt: Date = .now
    ) {
        self.id = id
        self.name = name
        self.nameVi = nameVi
        self.category = category
        self.bodyPart = bodyPart
        self.primaryMuscle = primaryMuscle
        self.secondaryMuscles = secondaryMuscles
        self.defaultRestSeconds = defaultRestSeconds
        self.isCustom = isCustom
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    /// The name to show for the app's current language.
    var displayName: String {
        if Bundle.main.preferredLocalizations.first == "vi", let nameVi { return nameVi }
        return name
    }
}
