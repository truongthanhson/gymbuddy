import Foundation
import SwiftData

/// How one Supabase table maps to the local SwiftData models (column names as in supabase/migrations).
/// `changed` lists rows edited since a date as JSON objects; `apply` writes pulled rows that are newer than the local ones.
struct SyncTable {
    let name: String
    let changed: @MainActor (ModelContext, Date) throws -> [[String: Any]]
    let apply: @MainActor (ModelContext, [[String: Any]]) throws -> Void
}

typealias SyncRow = [String: Any]

// MARK: - Column helpers

private func ts(_ date: Date) -> String { Timestamp.format(date) }
private func tsOrNull(_ date: Date?) -> Any { date.map { Timestamp.format($0) } ?? NSNull() }
private func orNull(_ value: Any?) -> Any { value ?? NSNull() }
private func key(_ id: UUID) -> String { id.uuidString.lowercased() }
private func keyOrNull(_ id: UUID?) -> Any { id.map { $0.uuidString.lowercased() } ?? NSNull() }

private extension Dictionary where Key == String, Value == Any {
    func string(_ column: String) -> String? { self[column] as? String }
    func uuid(_ column: String) -> UUID? { string(column).flatMap(UUID.init(uuidString:)) }
    func date(_ column: String) -> Date? { string(column).flatMap(Timestamp.parse) }
    func int(_ column: String) -> Int? { (self[column] as? NSNumber)?.intValue }
    func double(_ column: String) -> Double? { (self[column] as? NSNumber)?.doubleValue }
    func bool(_ column: String) -> Bool { (self[column] as? Bool) ?? false }
}

/// True when the pulled row should replace the local one (last write wins).
private func isNewer(_ row: SyncRow, than local: Date?) -> Bool {
    guard let remote = row.date("updated_at") else { return false }
    guard let local else { return true }
    return remote > local
}

// MARK: - Tables, parents before children

@MainActor
enum SyncTables {
    static var all: [SyncTable] {
        [customExercises, folders, templates, templateExercises, templateSets, workouts, workoutExercises, workoutSets]
    }

    static let customExercises = SyncTable(
        name: "custom_exercises",
        changed: { context, since in
            try context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.isCustom && $0.updatedAt >= since })).map { e -> SyncRow in
                [
                    "id": e.id, "name": e.name, "category": e.category, "body_part": e.bodyPart,
                    "primary_muscle": orNull(e.primaryMuscle), "secondary_muscles": e.secondaryMuscles,
                    "default_rest_seconds": orNull(e.defaultRestSeconds),
                    "created_at": ts(e.createdAt), "updated_at": ts(e.updatedAt), "deleted_at": tsOrNull(e.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let ids = rows.compactMap { $0.string("id") }
            let existing = try context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { ids.contains($0.id) }))
            let byId = Dictionary(existing.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            for row in rows {
                guard let id = row.string("id"), isNewer(row, than: byId[id]?.updatedAt) else { continue }
                let exercise = byId[id] ?? {
                    let created = Exercise(id: id, name: "", category: "", bodyPart: "", isCustom: true)
                    context.insert(created)
                    return created
                }()
                exercise.name = row.string("name") ?? ""
                exercise.category = row.string("category") ?? ""
                exercise.bodyPart = row.string("body_part") ?? ""
                exercise.primaryMuscle = row.string("primary_muscle")
                exercise.secondaryMuscles = row["secondary_muscles"] as? [String] ?? []
                exercise.defaultRestSeconds = row.int("default_rest_seconds")
                exercise.isCustom = true
                exercise.createdAt = row.date("created_at") ?? .now
                exercise.updatedAt = row.date("updated_at") ?? .now
                exercise.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let folders = SyncTable(
        name: "folders",
        changed: { context, since in
            try context.fetch(FetchDescriptor<TemplateFolder>(predicate: #Predicate { $0.updatedAt >= since })).map { f -> SyncRow in
                [
                    "id": key(f.id), "name": f.name, "sort_order": f.sortOrder,
                    "created_at": ts(f.createdAt), "updated_at": ts(f.updatedAt), "deleted_at": tsOrNull(f.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let byId = try SyncTables.folderMap(in: context, ids: rows.compactMap { $0.uuid("id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt) else { continue }
                let folder = byId[id] ?? {
                    let created = TemplateFolder(id: id, name: "", sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                folder.name = row.string("name") ?? ""
                folder.sortOrder = row.int("sort_order") ?? 0
                folder.createdAt = row.date("created_at") ?? .now
                folder.updatedAt = row.date("updated_at") ?? .now
                folder.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let templates = SyncTable(
        name: "templates",
        changed: { context, since in
            try context.fetch(FetchDescriptor<WorkoutTemplate>(predicate: #Predicate { $0.updatedAt >= since })).map { t -> SyncRow in
                [
                    "id": key(t.id), "folder_id": keyOrNull(t.folderId), "name": t.name, "note": orNull(t.note),
                    "sort_order": t.sortOrder, "last_used_at": tsOrNull(t.lastUsedAt),
                    "created_at": ts(t.createdAt), "updated_at": ts(t.updatedAt), "deleted_at": tsOrNull(t.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let byId = try SyncTables.templateMap(in: context, ids: rows.compactMap { $0.uuid("id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt) else { continue }
                let template = byId[id] ?? {
                    let created = WorkoutTemplate(id: id, name: "", sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                template.folderId = row.uuid("folder_id")
                template.name = row.string("name") ?? ""
                template.note = row.string("note")
                template.sortOrder = row.int("sort_order") ?? 0
                template.lastUsedAt = row.date("last_used_at")
                template.createdAt = row.date("created_at") ?? .now
                template.updatedAt = row.date("updated_at") ?? .now
                template.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let templateExercises = SyncTable(
        name: "template_exercises",
        changed: { context, since in
            try context.fetch(FetchDescriptor<TemplateExercise>(predicate: #Predicate { $0.updatedAt >= since })).compactMap { e -> SyncRow? in
                guard let parent = e.template else { return nil }
                return [
                    "id": key(e.id), "template_id": key(parent.id), "exercise_id": e.exerciseId,
                    "sort_order": e.sortOrder, "rest_seconds": orNull(e.restSeconds), "note": orNull(e.note),
                    "created_at": ts(e.createdAt), "updated_at": ts(e.updatedAt), "deleted_at": tsOrNull(e.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let byId = try SyncTables.templateExerciseMap(in: context, ids: rows.compactMap { $0.uuid("id") })
            let parents = try SyncTables.templateMap(in: context, ids: rows.compactMap { $0.uuid("template_id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt),
                      let parent = row.uuid("template_id").flatMap({ parents[$0] }) else { continue }
                let item = byId[id] ?? {
                    let created = TemplateExercise(id: id, exerciseId: "", sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                item.template = parent
                item.exerciseId = row.string("exercise_id") ?? ""
                item.sortOrder = row.int("sort_order") ?? 0
                item.restSeconds = row.int("rest_seconds")
                item.note = row.string("note")
                item.createdAt = row.date("created_at") ?? .now
                item.updatedAt = row.date("updated_at") ?? .now
                item.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let templateSets = SyncTable(
        name: "template_sets",
        changed: { context, since in
            try context.fetch(FetchDescriptor<TemplateSet>(predicate: #Predicate { $0.updatedAt >= since })).compactMap { s -> SyncRow? in
                guard let parent = s.templateExercise else { return nil }
                return [
                    "id": key(s.id), "template_exercise_id": key(parent.id), "sort_order": s.sortOrder,
                    "set_type": s.setTypeRaw, "target_weight_kg": orNull(s.targetWeightKg), "target_reps": orNull(s.targetReps),
                    "target_duration_sec": orNull(s.targetDurationSec), "target_distance_m": orNull(s.targetDistanceM),
                    "created_at": ts(s.createdAt), "updated_at": ts(s.updatedAt), "deleted_at": tsOrNull(s.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let ids = rows.compactMap { $0.uuid("id") }
            let existing = try context.fetch(FetchDescriptor<TemplateSet>(predicate: #Predicate { ids.contains($0.id) }))
            let byId = Dictionary(existing.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let parents = try SyncTables.templateExerciseMap(in: context, ids: rows.compactMap { $0.uuid("template_exercise_id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt),
                      let parent = row.uuid("template_exercise_id").flatMap({ parents[$0] }) else { continue }
                let set = byId[id] ?? {
                    let created = TemplateSet(id: id, sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                set.templateExercise = parent
                set.sortOrder = row.int("sort_order") ?? 0
                set.setTypeRaw = row.string("set_type") ?? SetType.normal.rawValue
                set.targetWeightKg = row.double("target_weight_kg")
                set.targetReps = row.int("target_reps")
                set.targetDurationSec = row.int("target_duration_sec")
                set.targetDistanceM = row.double("target_distance_m")
                set.createdAt = row.date("created_at") ?? .now
                set.updatedAt = row.date("updated_at") ?? .now
                set.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let workouts = SyncTable(
        name: "workouts",
        changed: { context, since in
            try context.fetch(FetchDescriptor<Workout>(predicate: #Predicate { $0.updatedAt >= since })).map { w -> SyncRow in
                [
                    "id": key(w.id), "template_id": keyOrNull(w.templateId), "name": w.name, "note": orNull(w.note),
                    "started_at": ts(w.startedAt), "ended_at": tsOrNull(w.endedAt),
                    "created_at": ts(w.createdAt), "updated_at": ts(w.updatedAt), "deleted_at": tsOrNull(w.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let byId = try SyncTables.workoutMap(in: context, ids: rows.compactMap { $0.uuid("id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt) else { continue }
                let workout = byId[id] ?? {
                    let created = Workout(id: id, name: "")
                    context.insert(created)
                    return created
                }()
                workout.templateId = row.uuid("template_id")
                workout.name = row.string("name") ?? ""
                workout.note = row.string("note")
                workout.startedAt = row.date("started_at") ?? .now
                workout.endedAt = row.date("ended_at")
                workout.createdAt = row.date("created_at") ?? .now
                workout.updatedAt = row.date("updated_at") ?? .now
                workout.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let workoutExercises = SyncTable(
        name: "workout_exercises",
        changed: { context, since in
            try context.fetch(FetchDescriptor<WorkoutExercise>(predicate: #Predicate { $0.updatedAt >= since })).compactMap { e -> SyncRow? in
                guard let parent = e.workout else { return nil }
                return [
                    "id": key(e.id), "workout_id": key(parent.id), "exercise_id": e.exerciseId,
                    "sort_order": e.sortOrder, "rest_seconds": orNull(e.restSeconds), "note": orNull(e.note),
                    "created_at": ts(e.createdAt), "updated_at": ts(e.updatedAt), "deleted_at": tsOrNull(e.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let byId = try SyncTables.workoutExerciseMap(in: context, ids: rows.compactMap { $0.uuid("id") })
            let parents = try SyncTables.workoutMap(in: context, ids: rows.compactMap { $0.uuid("workout_id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt),
                      let parent = row.uuid("workout_id").flatMap({ parents[$0] }) else { continue }
                let item = byId[id] ?? {
                    let created = WorkoutExercise(id: id, exerciseId: "", sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                item.workout = parent
                item.exerciseId = row.string("exercise_id") ?? ""
                item.sortOrder = row.int("sort_order") ?? 0
                item.restSeconds = row.int("rest_seconds")
                item.note = row.string("note")
                item.createdAt = row.date("created_at") ?? .now
                item.updatedAt = row.date("updated_at") ?? .now
                item.deletedAt = row.date("deleted_at")
            }
        }
    )

    static let workoutSets = SyncTable(
        name: "workout_sets",
        changed: { context, since in
            try context.fetch(FetchDescriptor<WorkoutSet>(predicate: #Predicate { $0.updatedAt >= since })).compactMap { s -> SyncRow? in
                guard let parent = s.workoutExercise else { return nil }
                return [
                    "id": key(s.id), "workout_exercise_id": key(parent.id), "sort_order": s.sortOrder,
                    "set_type": s.setTypeRaw, "weight_kg": orNull(s.weightKg), "reps": orNull(s.reps),
                    "duration_sec": orNull(s.durationSec), "distance_m": orNull(s.distanceM), "rpe": orNull(s.rpe),
                    "completed": s.completed, "completed_at": tsOrNull(s.completedAt),
                    "created_at": ts(s.createdAt), "updated_at": ts(s.updatedAt), "deleted_at": tsOrNull(s.deletedAt),
                ]
            }
        },
        apply: { context, rows in
            let ids = rows.compactMap { $0.uuid("id") }
            let existing = try context.fetch(FetchDescriptor<WorkoutSet>(predicate: #Predicate { ids.contains($0.id) }))
            let byId = Dictionary(existing.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let parents = try SyncTables.workoutExerciseMap(in: context, ids: rows.compactMap { $0.uuid("workout_exercise_id") })
            for row in rows {
                guard let id = row.uuid("id"), isNewer(row, than: byId[id]?.updatedAt),
                      let parent = row.uuid("workout_exercise_id").flatMap({ parents[$0] }) else { continue }
                let set = byId[id] ?? {
                    let created = WorkoutSet(id: id, sortOrder: 0)
                    context.insert(created)
                    return created
                }()
                set.workoutExercise = parent
                set.sortOrder = row.int("sort_order") ?? 0
                set.setTypeRaw = row.string("set_type") ?? SetType.normal.rawValue
                set.weightKg = row.double("weight_kg")
                set.reps = row.int("reps")
                set.durationSec = row.int("duration_sec")
                set.distanceM = row.double("distance_m")
                set.rpe = row.double("rpe")
                set.completed = row.bool("completed")
                set.completedAt = row.date("completed_at")
                set.createdAt = row.date("created_at") ?? .now
                set.updatedAt = row.date("updated_at") ?? .now
                set.deletedAt = row.date("deleted_at")
            }
        }
    )

    // MARK: - Lookups by id

    private static func folderMap(in context: ModelContext, ids: [UUID]) throws -> [UUID: TemplateFolder] {
        let found = try context.fetch(FetchDescriptor<TemplateFolder>(predicate: #Predicate { ids.contains($0.id) }))
        return Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func templateMap(in context: ModelContext, ids: [UUID]) throws -> [UUID: WorkoutTemplate] {
        let found = try context.fetch(FetchDescriptor<WorkoutTemplate>(predicate: #Predicate { ids.contains($0.id) }))
        return Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func templateExerciseMap(in context: ModelContext, ids: [UUID]) throws -> [UUID: TemplateExercise] {
        let found = try context.fetch(FetchDescriptor<TemplateExercise>(predicate: #Predicate { ids.contains($0.id) }))
        return Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func workoutMap(in context: ModelContext, ids: [UUID]) throws -> [UUID: Workout] {
        let found = try context.fetch(FetchDescriptor<Workout>(predicate: #Predicate { ids.contains($0.id) }))
        return Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func workoutExerciseMap(in context: ModelContext, ids: [UUID]) throws -> [UUID: WorkoutExercise] {
        let found = try context.fetch(FetchDescriptor<WorkoutExercise>(predicate: #Predicate { ids.contains($0.id) }))
        return Dictionary(found.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
