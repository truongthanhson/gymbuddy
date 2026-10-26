import Foundation

/// Buddy's own settings, kept apart from the app settings.
enum BuddyKey {
    static let aiConsent = "buddy.aiConsent"
    static let tone = "buddy.tone"
    static let goal = "buddy.goal"
    static let daysPerWeek = "buddy.daysPerWeek"
    static let experience = "buddy.experience"
    static let limitations = "buddy.limitations"
    static let reminders = "buddy.reminders"
    /// Start of the training cycle as seconds since 1970; 0 without one.
    static let cycleStart = "buddy.cycleStart"
    static let shareHealth = "buddy.shareHealth"
}

/// How Buddy talks.
enum BuddyTone: String, CaseIterable, Identifiable, Sendable {
    case gentle, balanced, strict
    var id: String { rawValue }
}

enum TrainingGoal: String, CaseIterable, Identifiable, Sendable {
    case general, strength, muscle
    case fatLoss = "fat_loss"
    var id: String { rawValue }
}

enum Experience: String, CaseIterable, Identifiable, Sendable {
    case beginner, intermediate, advanced
    var id: String { rawValue }
}

struct BuddySettings: Sendable {
    var aiConsent: Bool
    var tone: BuddyTone
    var goal: TrainingGoal
    var daysPerWeek: Int
    var experience: Experience
    var limitations: String
    var cycleStart: Date?
    var shareHealth: Bool

    static var current: BuddySettings {
        let defaults = UserDefaults.standard
        let days = defaults.integer(forKey: BuddyKey.daysPerWeek)
        return BuddySettings(
            aiConsent: defaults.bool(forKey: BuddyKey.aiConsent),
            tone: BuddyTone(rawValue: defaults.string(forKey: BuddyKey.tone) ?? "") ?? .balanced,
            goal: TrainingGoal(rawValue: defaults.string(forKey: BuddyKey.goal) ?? "") ?? .general,
            daysPerWeek: days == 0 ? defaultWorkoutsPerWeek : min(max(days, 1), 7),
            experience: Experience(rawValue: defaults.string(forKey: BuddyKey.experience) ?? "") ?? .intermediate,
            limitations: defaults.string(forKey: BuddyKey.limitations) ?? "",
            cycleStart: cycleStartDate(defaults.double(forKey: BuddyKey.cycleStart)),
            shareHealth: defaults.bool(forKey: BuddyKey.shareHealth)
        )
    }

    /// The goal profile as Buddy AI reads it.
    var profile: [String: Any] {
        var out: [String: Any] = ["goal": goal.rawValue, "daysPerWeek": daysPerWeek, "experience": experience.rawValue]
        let trimmed = limitations.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { out["injuriesOrLimitations"] = trimmed }
        return out
    }
}

/// The stored cycle start (seconds since 1970, 0 for none) as a date.
func cycleStartDate(_ seconds: Double) -> Date? { seconds > 0 ? Date(timeIntervalSince1970: seconds) : nil }
