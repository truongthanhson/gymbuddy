import Foundation

// The Coach Engine: Buddy's rule-based advice. Must match shared/coach-rules.md,
// shared/test-vectors/coach.json and the Android implementation.

private let hour: TimeInterval = 3600
private let day: TimeInterval = 24 * hour

/// One logged set as the coach sees it.
struct CoachSet: Equatable, Sendable {
    var weightKg: Double?
    var reps: Int?
    var rpe: Double?
    var warmup: Bool
}

/// One exercise in one finished workout.
struct CoachSession: Equatable, Sendable {
    var startedAt: Date
    var sets: [CoachSet]
}

/// An exercise with its sessions, for `coachInsights`.
struct CoachExercise: Equatable, Sendable {
    var exerciseId: String
    var bodyPart: String
    var category: String
    var sessions: [CoachSession]
}

enum TargetKind: String, Sendable { case increase, `repeat`, deload }

struct SetTarget: Equatable, Sendable {
    var kind: TargetKind
    var weightKg: Double
    var reps: Int
}

private let weightedCategories: Set<String> = ["barbell", "dumbbell", "machine", "cable", "weighted_bodyweight"]

private extension CoachSession {
    var workingSets: [CoachSet] {
        sets.filter { !$0.warmup && ($0.reps ?? 0) > 0 && ($0.weightKg ?? 0) > 0 }
    }
}

private func withWorkingSets(_ sessions: [CoachSession]) -> [CoachSession] {
    sessions.filter { !$0.workingSets.isEmpty }.sorted { $0.startedAt < $1.startedAt }
}

private func repRange(_ topReps: Int) -> ClosedRange<Int> {
    if topReps <= 6 { return 4...6 }
    if topReps <= 12 { return 8...12 }
    return 12...20
}

/// Weight jump for one progression step, in kg.
func coachStep(bodyPart: String, category: String) -> Double {
    if category == "dumbbell" { return 2 }
    if bodyPart == BodyPart.legs.rawValue { return 5 }
    return 2.5
}

private func round2(_ x: Double) -> Double { (x * 100).rounded() / 100 }

private func floorToStep(_ x: Double, _ step: Double) -> Double { round2((x / step + 1e-9).rounded(.down) * step) }

/// Fatigued (RPE 9+ twice in a row) or no new estimated 1RM in the last 3 of at least 4 sessions.
func isStalled(_ sessions: [CoachSession]) -> Bool {
    let ss = withWorkingSets(sessions)
    if ss.count >= 2 {
        let fatigued = ss.suffix(2).allSatisfy { s in
            let rpes = s.workingSets.compactMap(\.rpe)
            return !rpes.isEmpty && rpes.reduce(0, +) / Double(rpes.count) >= 9
        }
        if fatigued { return true }
    }
    if ss.count < 4 { return false }
    func best(_ s: CoachSession) -> Double {
        s.workingSets.map { estimatedOneRepMax(weightKg: $0.weightKg!, reps: $0.reps!) }.max()!
    }
    return ss.suffix(3).map(best).max()! <= ss.dropLast(3).map(best).max()! + 1e-6
}

/// What to aim for next time, or nil when the exercise isn't logged with weight and reps.
func nextTarget(_ sessions: [CoachSession], bodyPart: String, category: String) -> SetTarget? {
    guard weightedCategories.contains(category), let last = withWorkingSets(sessions).last else { return nil }
    let working = last.workingSets
    let top = working.map { $0.weightKg! }.max()!
    let topReps = working.filter { $0.weightKg == top }.map { $0.reps! }
    let range = repRange(topReps.max()!)
    let minReps = topReps.min()!
    let step = coachStep(bodyPart: bodyPart, category: category)
    if isStalled(sessions) { return SetTarget(kind: .deload, weightKg: floorToStep(top * 0.9, step), reps: minReps) }
    if minReps >= range.upperBound { return SetTarget(kind: .increase, weightKg: round2(top + step), reps: range.lowerBound) }
    return SetTarget(kind: .repeat, weightKg: top, reps: min(range.upperBound, minReps + 1))
}

enum RecoveryStatus: String, Sendable { case recovering, ready }

struct Recovery: Equatable, Sendable, Identifiable {
    var bodyPart: BodyPart
    var hoursSince: Int
    var status: RecoveryStatus
    var id: BodyPart { bodyPart }
}

private let coachedParts = BodyPart.allCases.filter { $0 != .cardio && $0 != .other }

private func order(_ part: BodyPart) -> Int { BodyPart.allCases.firstIndex(of: part)! }

/// Hours since each body part was last trained in the past 28 days, most rested first.
func recovery(_ sets: [BodyPartSet], now: Date) -> [Recovery] {
    var last: [BodyPart: Date] = [:]
    for s in sets {
        let part = BodyPart(key: s.bodyPart)
        guard !s.warmup, coachedParts.contains(part), s.startedAt <= now, s.startedAt >= now - 28 * day else { continue }
        last[part] = max(last[part] ?? .distantPast, s.startedAt)
    }
    return last.map { part, at in
        let since = now.timeIntervalSince(at)
        return Recovery(bodyPart: part, hoursSince: Int((since / hour).rounded(.down)), status: since < 48 * hour ? .recovering : .ready)
    }
    .sorted { a, b in a.hoursSince != b.hoursSince ? a.hoursSince > b.hoursSince : order(a.bodyPart) < order(b.bodyPart) }
}

enum CoachInsight: Equatable, Sendable {
    case inactive(days: Int)
    case deload(exerciseId: String, weightKg: Double, reps: Int)
    case volumeSpike(bodyPart: BodyPart, sets: Int)
    case neglected(bodyPart: BodyPart, days: Int)
    case streak(weeks: Int)
}

private func wholeDays(_ interval: TimeInterval) -> Int { Int((interval / day).rounded(.down)) }

/// Body parts trained much more this week than last (`volumeSpike`) or dropped for two weeks (`neglected`).
func volumeInsights(_ sets: [BodyPartSet], now: Date) -> [CoachInsight] {
    var spikes: [CoachInsight] = []
    var neglected: [CoachInsight] = []
    for part in coachedParts {
        let mine = sets.filter { !$0.warmup && BodyPart(key: $0.bodyPart) == part }
        func count(from: TimeInterval, to: TimeInterval) -> Int {
            mine.filter { $0.startedAt > now - from && $0.startedAt <= now - to }.count
        }
        let current = count(from: 7 * day, to: 0)
        let previous = count(from: 14 * day, to: 7 * day)
        let older = count(from: 42 * day, to: 14 * day)
        if previous >= 6 && Double(current) > Double(previous) * 1.3 {
            spikes.append(.volumeSpike(bodyPart: part, sets: current))
        } else if current + previous == 0 && older >= 6 {
            let latest = mine.map(\.startedAt).filter { $0 <= now }.max()!
            neglected.append(.neglected(bodyPart: part, days: wholeDays(now.timeIntervalSince(latest))))
        }
    }
    return spikes + neglected
}

/// Weeks in a row (7-day blocks back from now) with at least `goalPerWeek` workouts.
/// Whole days without a workout before Buddy reminds the person; see shared/coach-rules.md.
func reminderGapDays(_ daysPerWeek: Int) -> Int { max(2, 7 / min(max(daysPerWeek, 1), 7) + 1) }

/// When the training reminder is due, or nil before the first workout.
func reminderDueAt(lastWorkout: Date?, daysPerWeek: Int) -> Date? {
    lastWorkout.map { $0 + Double(reminderGapDays(daysPerWeek)) * day }
}

func streakWeeks(_ workoutStarts: [Date], now: Date, goalPerWeek: Int) -> Int {
    func count(_ i: Int) -> Int {
        workoutStarts.filter { $0 > now - Double(7 * (i + 1)) * day && $0 <= now - Double(7 * i) * day }.count
    }
    var i = count(0) >= goalPerWeek ? 0 : 1
    var weeks = 0
    while count(i) >= goalPerWeek {
        weeks += 1
        i += 1
    }
    return weeks
}

/// Everything Buddy has to say, most urgent first.
func coachInsights(
    exercises: [CoachExercise],
    bodyPartSets: [BodyPartSet],
    workoutStarts: [Date],
    goalPerWeek: Int,
    now: Date
) -> [CoachInsight] {
    var out: [CoachInsight] = []
    let starts = workoutStarts.filter { $0 <= now }
    if let latest = starts.max() {
        let days = wholeDays(now.timeIntervalSince(latest))
        if days >= 7 { out.append(.inactive(days: days)) }
    }
    let deloads: [(Date, CoachInsight)] = exercises.compactMap { e in
        guard let target = nextTarget(e.sessions, bodyPart: e.bodyPart, category: e.category),
              let latest = e.sessions.map(\.startedAt).max(),
              target.kind == .deload, now.timeIntervalSince(latest) <= 14 * day else { return nil }
        return (latest, .deload(exerciseId: e.exerciseId, weightKg: target.weightKg, reps: target.reps))
    }
    out += deloads.sorted { $0.0 > $1.0 }.map(\.1)
    out += volumeInsights(bodyPartSets, now: now)
    let weeks = streakWeeks(starts, now: now, goalPerWeek: goalPerWeek)
    if weeks >= 2 { out.append(.streak(weeks: weeks)) }
    return out
}
