import Foundation

// How a Strong CSV import names and files exercises; must match shared/test-vectors/strong_names.json (and Android).

/// Strong names that the library spells differently, lowercased.
private let strongAliases: [String: String] = [
    "triceps pushdown (cable - straight bar)": "Triceps Pushdown (Cable)",
    "triceps pushdown (cable - rope)": "Triceps Pushdown (Rope)",
    "crunch (machine)": "Ab Crunch (Machine)",
    "calf press on seated leg press": "Calf Press (Leg Press)",
    "triceps extension (dumbbell)": "Overhead Triceps Extension (Dumbbell)",
    "triceps extension (cable)": "Overhead Triceps Extension (Cable)",
    "strict military press (barbell)": "Overhead Press (Barbell)",
    "leg press (machine)": "Leg Press",
    "hack squat": "Hack Squat (Machine)",
]

/// The library's name for a Strong exercise, or `name` itself.
func libraryName(_ name: String) -> String {
    strongAliases[name.trimmingCharacters(in: .whitespaces).lowercased()] ?? name
}

/// The category from the equipment in brackets, like "Bicep Curl (Machine)", as a key; nil when it doesn't say.
func categoryFromName(_ name: String) -> String? {
    let lower = name.lowercased()
    if lower.contains("(barbell") { return "barbell" }
    if lower.contains("(dumbbell") { return "dumbbell" }
    if lower.contains("(machine") || lower.contains("(plate loaded") || lower.contains("(smith machine") { return "machine" }
    if lower.contains("(cable") { return "cable" }
    if lower.contains("(assisted") { return "assisted_bodyweight" }
    if lower.contains("(weighted") { return "weighted_bodyweight" }
    if lower.contains("(bodyweight") { return "reps_only" }
    return nil
}

/// Words that give away the body part, checked in this order (some shoulder moves would otherwise read as chest or back).
private let bodyPartWords: [(String, [String])] = [
    ("shoulders", ["reverse fly", "upright row", "rear delt", "face pull", "handstand", "pike push", "push press", "landmine press"]),
    ("legs", [
        "calf", "leg press", "leg extension", "leg curl", "squat", "lunge", "hamstring", "glute", "hip thrust", "deadlift",
        "step up", "good morning", "box jump", "wall sit", "abduct", "adduct",
    ]),
    ("back", ["row", "pulldown", "pull up", "pull-up", "chin up", "chin-up", "lat ", "back extension", "shrug", "rack pull"]),
    ("chest", ["bench", "chest", "fly", "flye", "pec ", "pec deck", "push up", "push-up", "crossover"]),
    ("shoulders", ["shoulder", "overhead press", "military", "lateral raise", "front raise", "arnold"]),
    ("arms", ["curl", "tricep", "bicep", "dip", "skull", "kickback", "wrist"]),
    ("core", [
        "crunch", "plank", "sit up", "sit-up", "ab ", "abs", "russian twist", "leg raise", "knee raise", "woodchop",
        "dead bug", "hollow", "oblique", "toes to bar", "mountain climber", "pallof",
    ]),
]

private func baseName(_ name: String) -> String {
    let lower = name.lowercased()
    let base = lower.range(of: " (").map { String(lower[..<$0.lowerBound]) } ?? lower
    return base.trimmingCharacters(in: .whitespaces)
}

/// The body part key for an exercise that is not in the library: that of a `library` exercise (name, body part key)
/// with the same name before the brackets, else from words in the name, else "other".
func guessBodyPart(_ name: String, library: [(String, String)]) -> String {
    let base = baseName(name)
    if let match = library.first(where: { baseName($0.0) == base }) { return match.1 }
    let words = name.lowercased() + " "
    let match = bodyPartWords.first { _, list in list.contains { words.contains($0) } }
    return match?.0 ?? "other"
}
