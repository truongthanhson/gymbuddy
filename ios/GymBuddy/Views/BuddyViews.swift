import SwiftData
import SwiftUI

// Buddy, the in-app coach. Its strings live in Buddy.xcstrings so they merge cleanly.

private func buddy(_ value: String.LocalizationValue) -> String { String(localized: value, table: "Buddy") }

private func lowercasedLabel(_ part: BodyPart) -> String { String(localized: part.label).lowercased() }

extension TargetKind {
    var buddyLabel: String {
        switch self {
        case .increase: buddy("add weight")
        case .repeat: buddy("one more rep")
        case .deload: buddy("deload")
        }
    }
}

/// One of Buddy's tips as a sentence.
func insightText(_ insight: CoachInsight, exercises: [String: Exercise], unit: WeightUnit) -> String {
    switch insight {
    case .inactive(let days):
        buddy("It's been \(days) days since your last workout. A lighter session today gets you back into rhythm.")
    case .deload(let exerciseId, let weightKg, let reps):
        buddy("\(exercises[exerciseId]?.displayName ?? exerciseId) has stalled. Try a deload this week: \(unit.format(weightKg)) × \(reps).")
    case .volumeSpike(let part, let sets):
        buddy("This week \(lowercasedLabel(part)) got \(sets) sets, much more than last week. Watch out for overload.")
    case .neglected(let part, let days):
        buddy("You haven't trained \(lowercasedLabel(part)) for \(days) days.")
    case .streak(let weeks):
        buddy("\(weeks) weeks in a row on target. Keep it up!")
    }
}

private struct BuddyHeader: View {
    var title: String = buddy("Buddy")
    var body: some View {
        Label { Text(verbatim: title).font(.subheadline.bold()) } icon: { Image(systemName: "sparkles") }
            .foregroundStyle(.tint)
    }
}

/// Buddy's card on the start screen: the most urgent tips and which muscles are rested.
struct BuddyTodaySection: View {
    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil }) private var workouts: [Workout]
    @Query private var exercises: [Exercise]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var expanded = false

    var body: some View {
        let byId = Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let snapshot = CoachService.snapshot(workouts: workouts, exercises: byId)
        Section {
            VStack(alignment: .leading, spacing: 8) {
                BuddyHeader()
                if snapshot.workoutCount == 0 {
                    Text(verbatim: buddy("Log your first workout and Buddy will start suggesting weights, rest days and deloads."))
                } else if snapshot.insights.isEmpty {
                    Text(verbatim: buddy("You're on track. Keep logging and Buddy will tell you when something needs attention."))
                } else {
                    let shown = expanded ? snapshot.insights : Array(snapshot.insights.prefix(1))
                    ForEach(Array(shown.enumerated()), id: \.offset) { _, insight in
                        Text(verbatim: insightText(insight, exercises: byId, unit: weightUnit))
                    }
                    if snapshot.insights.count > 1 {
                        Button {
                            withAnimation { expanded.toggle() }
                        } label: {
                            Text(verbatim: expanded ? buddy("Show less") : buddy("\(snapshot.insights.count - 1) more tips"))
                        }
                        .buttonStyle(.borderless)
                    }
                }
                let ready = snapshot.recovery.filter { $0.status == .ready }
                let recovering = snapshot.recovery.filter { $0.status == .recovering }
                if !ready.isEmpty || !recovering.isEmpty { Divider() }
                if !ready.isEmpty {
                    let parts = ready.map { String(localized: $0.bodyPart.label) }.joined(separator: ", ")
                    Text(verbatim: buddy("Ready: \(parts)")).font(.subheadline)
                }
                if !recovering.isEmpty {
                    let parts = recovering.map { buddy("\(String(localized: $0.bodyPart.label)) (\($0.hoursSince)h)") }.joined(separator: ", ")
                    Text(verbatim: buddy("Recovering: \(parts)")).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
    }
}

/// "Buddy: 62.5 kg × 8 · add weight" under an exercise while logging.
struct BuddyTargetHint: View {
    let exerciseId: String
    let bodyPart: String
    let category: String
    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil }) private var workouts: [Workout]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg

    var body: some View {
        let sessions = CoachService.sessions(of: workouts)[exerciseId] ?? []
        if let target = nextTarget(sessions, bodyPart: bodyPart, category: category) {
            Label {
                Text(verbatim: buddy("Buddy: \(weightUnit.format(target.weightKg)) × \(target.reps)") + " · " + target.kind.buddyLabel)
            } icon: {
                Image(systemName: "sparkles")
            }
            .font(.caption)
            .foregroundStyle(.purple)
        }
    }
}

/// The targets for the exercises of a workout just finished, for its summary.
struct BuddyNextTimeSection: View {
    let exerciseIds: [String]
    @Query(filter: #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil }) private var workouts: [Workout]
    @Query private var exercises: [Exercise]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg

    var body: some View {
        let byId = Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let sessions = CoachService.sessions(of: workouts)
        var seen = Set<String>()
        let rows: [(String, SetTarget)] = exerciseIds.compactMap { id in
            guard seen.insert(id).inserted, let e = byId[id],
                  let t = nextTarget(sessions[id] ?? [], bodyPart: e.bodyPart, category: e.category) else { return nil }
            return (e.displayName, t)
        }
        if !rows.isEmpty {
            Section {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    Text(verbatim: buddy("\(row.0): \(weightUnit.format(row.1.weightKg)) × \(row.1.reps)") + " · " + row.1.kind.buddyLabel)
                }
            } header: {
                BuddyHeader(title: buddy("Next time"))
            }
        }
    }
}
