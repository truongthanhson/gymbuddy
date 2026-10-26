import Foundation

// CSV export and import in Strong's column layout; must match shared/test-vectors/csv.json (and Android).

/// `setType` is "normal", "warmup", "drop" or "failure"; `weightKg` is always kg.
struct CsvSet: Equatable, Sendable {
    var setType = "normal"
    var weightKg: Double?
    var reps: Int?
    var distanceM: Double?
    var seconds: Int?
    var rpe: Double?
}

struct CsvExercise: Equatable, Sendable {
    var name: String
    var note: String?
    var sets: [CsvSet]
}

/// `date` is local time as "yyyy-MM-dd HH:mm:ss", the way Strong writes it.
struct CsvWorkout: Equatable, Sendable {
    var date: String
    var name: String
    var durationSec: Int?
    var note: String?
    var exercises: [CsvExercise]
}

private let exportHeader = [
    "Date", "Workout Name", "Duration", "Exercise Name", "Set Order", "Weight", "Reps",
    "Distance", "Seconds", "Notes", "Workout Notes", "RPE", "Weight Unit",
]

/// One CSV line; fields with commas, quotes or line breaks are quoted.
func csvLine(_ fields: [String]) -> String {
    fields.map { field in
        field.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" || $0 == "\r\n" })
            ? "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
            : field
    }.joined(separator: ",")
}

/// Rows of a CSV text (RFC 4180, either line ending); blank lines are skipped.
func parseCsv(_ text: String, delimiter: Unicode.Scalar = ",") -> [[String]] {
    // Unicode scalars, so "\r\n" is two characters as on Android.
    let chars = Array(text.unicodeScalars)
    var rows: [[String]] = []
    var row: [String] = []
    var field = String.UnicodeScalarView()
    var quoted = false
    var i = chars.first == "\u{FEFF}" ? 1 : 0
    func endRow() {
        row.append(String(field))
        field = String.UnicodeScalarView()
        if row.count > 1 || !row[0].isEmpty { rows.append(row) }
        row = []
    }
    while i < chars.count {
        let c = chars[i]
        let next: Unicode.Scalar? = i + 1 < chars.count ? chars[i + 1] : nil
        if quoted && c == "\"" && next == "\"" {
            field.append("\"")
            i += 1
        } else if c == "\"" {
            quoted.toggle()
        } else if quoted {
            field.append(c)
        } else if c == delimiter {
            row.append(String(field))
            field = String.UnicodeScalarView()
        } else if c == "\r" && next == "\n" {
            endRow()
            i += 1
        } else if c == "\n" || c == "\r" {
            endRow()
        } else {
            field.append(c)
        }
        i += 1
    }
    if !field.isEmpty || !row.isEmpty { endRow() }
    return rows
}

/// Newer Strong exports separate fields with ";": whichever of ";" and "," the header line has more of, outside quotes.
func csvDelimiter(_ text: String) -> Unicode.Scalar {
    var quoted = false
    var commas = 0
    var semicolons = 0
    for c in text.unicodeScalars {
        if c == "\"" {
            quoted.toggle()
        } else if quoted {
            continue
        } else if c == "\n" || c == "\r" {
            break
        } else if c == "," {
            commas += 1
        } else if c == ";" {
            semicolons += 1
        }
    }
    return semicolons > commas ? ";" : ","
}

/// "1h 5m" or "45m".
func formatCsvDuration(_ seconds: Int) -> String {
    let hours = seconds / 3600
    let minutes = seconds % 3600 / 60
    return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m"
}

/// Reads "1h 5m", "45m", "30s" or a plain (possibly decimal) number of seconds.
func parseCsvDuration(_ text: String) -> Int? {
    let trimmed = text.trimmingCharacters(in: .whitespaces)
    if trimmed.isEmpty { return nil }
    if let plain = Double(trimmed), plain.isFinite { return Int(plain) }
    var total = 0
    var digits = ""
    var sawPart = false
    for c in trimmed where c != " " {
        if c.isASCII, c.isNumber {
            digits.append(c)
        } else if let value = Int(digits), let factor = ["h": 3600, "m": 60, "s": 1][String(c)] {
            total += value * factor
            digits = ""
            sawPart = true
        } else {
            return nil
        }
    }
    return sawPart && digits.isEmpty ? total : nil
}

private func number(_ value: Double?) -> String { value.map(formatWeight) ?? "" }

/// Workouts as CSV, one row per set, weights in `unit`.
func exportCsv(_ workouts: [CsvWorkout], unit: WeightUnit) -> String {
    var text = csvLine(exportHeader) + "\n"
    for workout in workouts {
        let duration = workout.durationSec.map(formatCsvDuration) ?? ""
        for exercise in workout.exercises {
            var normal = 0
            for set in exercise.sets {
                let order: String
                switch set.setType {
                case "warmup": order = "W"
                case "drop": order = "D"
                case "failure": order = "F"
                default:
                    normal += 1
                    order = String(normal)
                }
                let fields: [String] = [
                    workout.date, workout.name, duration, exercise.name, order,
                    number(set.weightKg.map(unit.display)), set.reps.map(String.init) ?? "",
                    number(set.distanceM), set.seconds.map(String.init) ?? "",
                    exercise.note ?? "", workout.note ?? "", number(set.rpe), unit.symbol,
                ]
                text += csvLine(fields) + "\n"
            }
        }
    }
    return text
}

/// The unit of a weight header like "Weight (lbs)", or nil.
private func unitInHeader(_ header: String) -> WeightUnit? {
    let lower = header.lowercased()
    if lower.contains("lb") { return .lb }
    if lower.contains("kg") { return .kg }
    return nil
}

private let setOrders: Set<String> = ["W", "D", "F"]

/// Meters in one unit of a distance header like "Distance (km)"; meters when the header has no unit.
private func metersPerUnitInHeader(_ header: String) -> Double {
    let lower = header.lowercased()
    if lower.contains("km") { return 1000 }
    if lower.contains("mile") || lower.contains("(mi)") { return 1609.344 }
    if lower.contains("feet") || lower.contains("(ft)") { return 0.3048 }
    return 1
}

/// "yyyy-MM-dd HH:mm:ss" from that, an ISO "T" separator, or a time without seconds.
private func normalizedCsvDate(_ text: String) -> String {
    var chars = Array(text)
    if chars.count > 10, chars[10] == "T" { chars[10] = " " }
    let date = String(chars)
    let withoutSeconds = date.range(of: #"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$"#, options: .regularExpression) != nil
    return withoutSeconds ? date + ":00" : date
}

/// Workouts from a Strong (or GymBuddy) CSV export, in file order. Weights without a unit are read in `defaultUnit`.
/// Returns nil when the text has no Date, Workout Name and Exercise Name columns.
func importCsv(_ text: String, defaultUnit: WeightUnit) -> [CsvWorkout]? {
    let rows = parseCsv(text, delimiter: csvDelimiter(text))
    guard let header = rows.first?.map({ $0.trimmingCharacters(in: .whitespaces) }) else { return nil }
    func column(_ name: String) -> Int? { header.firstIndex { $0.caseInsensitiveCompare(name) == .orderedSame } }
    // Newer Strong exports put units in headers, like "Duration (sec)" or "Distance (meters)".
    func columnWithUnit(_ name: String) -> Int? {
        column(name) ?? header.firstIndex { $0.lowercased().hasPrefix(name.lowercased() + " (") }
    }
    guard let dateCol = column("Date"), let nameCol = column("Workout Name"), let exerciseCol = column("Exercise Name") else {
        return nil
    }
    let weightCol = header.firstIndex {
        $0.lowercased().hasPrefix("weight") && $0.caseInsensitiveCompare("Weight Unit") != .orderedSame
    }
    let headerUnit = weightCol.flatMap { unitInHeader(header[$0]) }
    let durationCol = columnWithUnit("Duration"), orderCol = column("Set Order"), repsCol = column("Reps")
    let distanceCol = columnWithUnit("Distance"), secondsCol = column("Seconds"), notesCol = column("Notes")
    let workoutNotesCol = column("Workout Notes"), rpeCol = column("RPE"), unitCol = column("Weight Unit")
    let durationInMinutes = durationCol.map { header[$0].lowercased().contains("(min") } ?? false
    let metersPerDistance = distanceCol.map { metersPerUnitInHeader(header[$0]) } ?? 1

    var workouts: [CsvWorkout] = []
    var workoutIndex: [String: Int] = [:]

    for row in rows.dropFirst() {
        func cell(_ col: Int?) -> String {
            guard let col, col < row.count else { return "" }
            return row[col].trimmingCharacters(in: .whitespaces)
        }
        func positive(_ col: Int?) -> Double? {
            Double(cell(col).replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil }
        }
        let date = normalizedCsvDate(cell(dateCol))
        let exerciseName = cell(exerciseCol)
        if date.isEmpty || exerciseName.isEmpty { continue }
        let order = cell(orderCol).uppercased()
        // Strong also writes rows that are not sets, like "Rest Timer"; sets are numbered or W, D or F.
        if !order.isEmpty, Int(order) == nil, !setOrders.contains(order) { continue }
        let name = cell(nameCol)
        let key = date + "\u{1F}" + name
        let w: Int
        if let existing = workoutIndex[key] {
            w = existing
        } else {
            w = workouts.count
            workoutIndex[key] = w
            var duration = parseCsvDuration(cell(durationCol))
            if durationInMinutes, Double(cell(durationCol)) != nil { duration = duration.map { $0 * 60 } }
            workouts.append(CsvWorkout(date: date, name: name, durationSec: duration, note: nil, exercises: []))
        }
        if workouts[w].note == nil, !cell(workoutNotesCol).isEmpty { workouts[w].note = cell(workoutNotesCol) }
        let e: Int
        if let existing = workouts[w].exercises.firstIndex(where: { $0.name == exerciseName }) {
            e = existing
        } else {
            e = workouts[w].exercises.count
            workouts[w].exercises.append(CsvExercise(name: exerciseName, note: nil, sets: []))
        }
        if workouts[w].exercises[e].note == nil, !cell(notesCol).isEmpty { workouts[w].exercises[e].note = cell(notesCol) }
        let unit = WeightUnit.allCases.first { $0.symbol.caseInsensitiveCompare(cell(unitCol)) == .orderedSame } ?? headerUnit ?? defaultUnit
        let setType: String
        switch order {
        case "W": setType = "warmup"
        case "D": setType = "drop"
        case "F": setType = "failure"
        default: setType = "normal"
        }
        workouts[w].exercises[e].sets.append(CsvSet(
            setType: setType,
            weightKg: positive(weightCol).map(unit.toKg),
            reps: positive(repsCol).map { Int($0) },
            distanceM: positive(distanceCol).map { $0 * metersPerDistance },
            seconds: positive(secondsCol).map { Int($0) },
            rpe: positive(rpeCol)
        ))
    }
    return workouts
}
