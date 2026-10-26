import Foundation
import SwiftData
import SwiftUI
import UniformTypeIdentifiers

/// `imported` workouts were added; `skipped` were already on this device (same start time and name).
struct CsvImportResult: Equatable {
    var imported: Int
    var skipped: Int
}

/// Exports finished workouts to, and imports them from, CSV in Strong's layout.
@MainActor
enum CsvService {
    private static var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }

    /// Every finished workout's completed sets.
    static func export(unit: WeightUnit, in context: ModelContext) throws -> (text: String, workouts: Int) {
        let finished = #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil }
        let workouts = try context.fetch(FetchDescriptor(predicate: finished, sortBy: [SortDescriptor(\.startedAt)]))
        let exercises = try context.fetch(FetchDescriptor<Exercise>())
        let names = Dictionary(exercises.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
        let formatter = dateFormatter
        let rows = workouts.map { workout in
            CsvWorkout(
                date: formatter.string(from: workout.startedAt),
                name: workout.name,
                durationSec: workout.endedAt.map { Int($0.timeIntervalSince(workout.startedAt)) },
                note: workout.note,
                exercises: workout.liveExercises.compactMap { item in
                    let sets = item.liveSets.filter(\.completed).map { set in
                        CsvSet(setType: set.setTypeRaw, weightKg: set.weightKg, reps: set.reps,
                               distanceM: set.distanceM, seconds: set.durationSec, rpe: set.rpe)
                    }
                    return sets.isEmpty ? nil : CsvExercise(name: names[item.exerciseId] ?? item.exerciseId, note: item.note, sets: sets)
                }
            )
        }
        return (exportCsv(rows, unit: unit), rows.count)
    }

    /// Imports workouts; exercises are matched by name, and missing ones are created as custom exercises.
    /// Returns nil when the text is not a workout CSV export.
    static func importText(_ text: String, defaultUnit: WeightUnit, in context: ModelContext) throws -> CsvImportResult? {
        guard let workouts = importCsv(text, defaultUnit: defaultUnit) else { return nil }
        let live = try context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.deletedAt == nil }))
        // Built-in exercises win over custom ones with the same name.
        var byName: [String: Exercise] = [:]
        for exercise in live.sorted(by: { !$0.isCustom && $1.isCustom }) where byName[exercise.name.lowercased()] == nil {
            byName[exercise.name.lowercased()] = exercise
        }
        let existing = try context.fetch(FetchDescriptor<Workout>(predicate: #Predicate { $0.deletedAt == nil }))
        var seen = Set(existing.map { "\($0.startedAt.timeIntervalSince1970)|\($0.name)" })
        let formatter = dateFormatter
        var result = CsvImportResult(imported: 0, skipped: 0)
        let now = Date()

        for csv in workouts {
            guard let startedAt = formatter.date(from: csv.date) else { continue }
            let key = "\(startedAt.timeIntervalSince1970)|\(csv.name)"
            if seen.contains(key) {
                result.skipped += 1
                continue
            }
            seen.insert(key)
            let workout = Workout(name: csv.name, startedAt: startedAt)
            let endedAt = startedAt.addingTimeInterval(TimeInterval(csv.durationSec ?? 0))
            workout.endedAt = endedAt
            workout.note = csv.note
            workout.createdAt = now
            workout.updatedAt = now
            context.insert(workout)
            for (index, entry) in csv.exercises.enumerated() {
                let exercise: Exercise
                if let known = byName[entry.name.lowercased()] {
                    exercise = known
                } else {
                    exercise = Exercise(
                        id: UUID().uuidString, name: entry.name, category: guessCategory(entry).rawValue,
                        bodyPart: BodyPart.other.rawValue, isCustom: true
                    )
                    context.insert(exercise)
                    byName[entry.name.lowercased()] = exercise
                }
                let item = WorkoutExercise(exerciseId: exercise.id, sortOrder: index, now: now)
                item.note = entry.note
                item.workout = workout
                context.insert(item)
                for (order, values) in entry.sets.enumerated() {
                    let set = WorkoutSet(
                        sortOrder: order, weightKg: values.weightKg, reps: values.reps,
                        durationSec: values.seconds, distanceM: values.distanceM, now: now
                    )
                    set.setTypeRaw = SetType(rawValue: values.setType)?.rawValue ?? SetType.normal.rawValue
                    set.rpe = values.rpe
                    set.completed = true
                    set.completedAt = endedAt
                    set.workoutExercise = item
                    context.insert(set)
                }
            }
            result.imported += 1
        }
        try context.save()
        return result
    }

    /// Strong names exercises like "Bench Press (Dumbbell)"; otherwise go by what the sets record.
    private static func guessCategory(_ exercise: CsvExercise) -> ExerciseCategory {
        let name = exercise.name.lowercased()
        if name.contains("(barbell)") { return .barbell }
        if name.contains("(dumbbell)") { return .dumbbell }
        if name.contains("(machine)") { return .machine }
        if name.contains("(cable)") { return .cable }
        if exercise.sets.contains(where: { $0.distanceM != nil }) { return .cardio }
        if exercise.sets.contains(where: { $0.weightKg != nil }) { return .barbell }
        if exercise.sets.contains(where: { $0.reps != nil }) { return .repsOnly }
        if exercise.sets.contains(where: { $0.seconds != nil }) { return .duration }
        return .barbell
    }
}

/// A CSV file for `fileExporter`.
struct CsvDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.commaSeparatedText]
    var text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = configuration.file.regularFileContents.map { String(decoding: $0, as: UTF8.self) } ?? ""
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
