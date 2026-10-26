import Foundation

/// The compact training summary Buddy AI reads: goal profile, the Coach Engine's output,
/// the last 8 weeks of workouts, records and body weight. Weights are written in the person's unit.
struct CoachContext {
    let workouts: [Workout]
    let exercises: [String: Exercise]
    let snapshot: CoachSnapshot
    let unit: WeightUnit
    let vietnamese: Bool

    private func name(_ id: String) -> String {
        guard let e = exercises[id] else { return id }
        if vietnamese, let vi = e.nameVi { return vi }
        return e.name
    }

    private func day(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    private func fmt(_ kg: Double) -> String { unit.format(kg) }

    /// One workout as text lines per exercise.
    func workout(_ w: Workout) -> [String: Any] {
        var out: [String: Any] = ["date": day(w.startedAt), "name": w.name]
        if let end = w.endedAt { out["duration"] = formatElapsed(Int(end.timeIntervalSince(w.startedAt))) }
        out["exercises"] = w.liveExercises.compactMap { item -> [String: Any]? in
            let done = item.liveSets.filter(\.completed)
            guard !done.isEmpty else { return nil }
            let sets = done.map { s -> String in
                var text = ""
                if let kg = s.weightKg, let reps = s.reps { text = "\(fmt(kg)) × \(reps)" }
                else if let reps = s.reps { text = "\(reps) reps" }
                else if let sec = s.durationSec { text = formatElapsed(sec) }
                else if let m = s.distanceM { text = "\(Int(m)) m" }
                if let rpe = s.rpe { text += " @\(rpe.formatted())" }
                if s.setType != .normal { text += " (\(s.setType.rawValue))" }
                return text
            }
            return ["name": name(item.exerciseId), "bodyPart": exercises[item.exerciseId]?.bodyPart ?? "other", "sets": sets.joined(separator: ", ")]
        }
        return out
    }

    private func insight(_ i: CoachInsight) -> [String: Any] {
        switch i {
        case .inactive(let days): ["type": "inactive", "days": days]
        case .deload(let id, let kg, let reps): ["type": "deload", "exercise": name(id), "target": "\(fmt(kg)) × \(reps)"]
        case .volumeSpike(let part, let sets): ["type": "volume_spike", "bodyPart": part.rawValue, "setsThisWeek": sets]
        case .neglected(let part, let days): ["type": "neglected", "bodyPart": part.rawValue, "days": days]
        case .streak(let weeks): ["type": "streak", "weeks": weeks]
        }
    }

    /// Everything but the task-specific part, which the caller adds.
    func build(profile: [String: Any], bodyWeights: [BodyMeasurement], now: Date = .now) -> [String: Any] {
        let finished = workouts.filter { $0.endedAt != nil && $0.deletedAt == nil }
        let recentIds = Set(finished.filter { $0.startedAt >= now.addingTimeInterval(-28 * 86400) }.flatMap { $0.liveExercises.map(\.exerciseId) })
        let targets = snapshot.targets.filter { recentIds.contains($0.key) }.sorted { $0.key < $1.key }.map { id, t -> [String: Any] in
            ["exercise": name(id), "target": "\(fmt(t.weightKg)) × \(t.reps)", "kind": t.kind.rawValue]
        }
        let recent = finished.filter { $0.startedAt >= now.addingTimeInterval(-56 * 86400) }
            .sorted { $0.startedAt > $1.startedAt }.prefix(40)
        let weights = bodyWeights.filter { $0.kind == MeasurementKind.weight.rawValue && $0.deletedAt == nil }
            .sorted { $0.measuredAt > $1.measuredAt }.prefix(10)
            .map { ["date": day($0.measuredAt), "weight": fmt($0.value)] }
        return [
            "today": day(now),
            "unit": unit.symbol,
            "profile": profile,
            "totalWorkouts": finished.count,
            "engine": [
                "insights": snapshot.insights.map(insight),
                "recovery": snapshot.recovery.map { ["bodyPart": $0.bodyPart.rawValue, "hoursSince": $0.hoursSince, "status": $0.status.rawValue] },
                "targets": targets,
            ] as [String: Any],
            "recentWorkouts": recent.map(workout),
            "records": records(finished),
            "bodyWeight": Array(weights),
        ]
    }

    /// Best estimated 1RM per exercise for the 15 exercises done most often.
    private func records(_ finished: [Workout]) -> [[String: Any]] {
        struct Best { var sessions = 0; var e1rm = 0.0; var set = ""; var date = "" }
        var bests: [String: Best] = [:]
        for w in finished {
            for item in w.liveExercises {
                let sets = item.liveSets.filter { $0.completed && $0.setType != .warmup && ($0.weightKg ?? 0) > 0 && ($0.reps ?? 0) > 0 }
                guard !sets.isEmpty else { continue }
                let key = name(item.exerciseId)
                var best = bests[key] ?? Best()
                best.sessions += 1
                for s in sets {
                    let value = estimatedOneRepMax(weightKg: s.weightKg!, reps: s.reps!)
                    if value > best.e1rm {
                        best.e1rm = value
                        best.set = "\(fmt(s.weightKg!)) × \(s.reps!)"
                        best.date = day(w.startedAt)
                    }
                }
                bests[key] = best
            }
        }
        return bests.sorted { $0.value.sessions > $1.value.sessions }.prefix(15).map { exercise, b in
            ["exercise": exercise, "bestSet": b.set, "estimated1RM": fmt(b.e1rm), "date": b.date]
        }
    }
}

/// The library Buddy may pick from when designing a routine.
func exerciseLibrary(_ exercises: [Exercise], vietnamese: Bool) -> [[String: Any]] {
    exercises.filter { $0.deletedAt == nil }.sorted { $0.id < $1.id }.map { e in
        ["id": e.id, "name": vietnamese ? (e.nameVi ?? e.name) : e.name, "bodyPart": e.bodyPart, "category": e.category]
    }
}
