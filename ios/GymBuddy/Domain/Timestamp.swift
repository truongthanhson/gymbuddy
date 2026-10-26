import Foundation

/// The text format sync uses for timestamps, written by hand so it is exact and needs no shared formatter.
/// Sends UTC with milliseconds ("2026-10-26T05:55:16.123Z"); reads any fraction length and offset, dropping sub-millisecond digits.
enum Timestamp {
    static func format(_ date: Date) -> String {
        let millis = Int64((date.timeIntervalSince1970 * 1000).rounded(.down))
        let seconds = millis.floorDiv(1000)
        let days = seconds.floorDiv(86_400)
        let secondOfDay = Int(seconds - days * 86_400)
        let (year, month, day) = civil(fromDays: days)
        return String(
            format: "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            year, month, day, secondOfDay / 3600, secondOfDay % 3600 / 60, secondOfDay % 60, Int(millis - seconds * 1000)
        )
    }

    static func parse(_ text: String) -> Date? {
        let chars = Array(text)
        guard chars.count >= 19 else { return nil }
        func number(_ range: Range<Int>) -> Int? {
            range.upperBound <= chars.count ? Int(String(chars[range])) : nil
        }
        guard let year = number(0..<4), let month = number(5..<7), let day = number(8..<10),
              let hour = number(11..<13), let minute = number(14..<16), let second = number(17..<19)
        else { return nil }

        var index = 19
        var millis = 0
        if index < chars.count, chars[index] == "." {
            index += 1
            var digits = 0
            while index < chars.count, let digit = chars[index].wholeNumberValue {
                if digits < 3 { millis = millis * 10 + digit }
                digits += 1
                index += 1
            }
            while digits < 3 {
                millis *= 10
                digits += 1
            }
        }

        var offsetSeconds = 0
        if index < chars.count, chars[index] == "+" || chars[index] == "-" {
            let sign = chars[index] == "-" ? -1 : 1
            guard let hours = number(index + 1..<index + 3) else { return nil }
            let minutes = number(index + 4..<index + 6) ?? 0
            offsetSeconds = sign * (hours * 3600 + minutes * 60)
        }

        let days = Self.days(fromYear: year, month: month, day: day)
        let seconds = days * 86_400 + Int64(hour * 3600 + minute * 60 + second - offsetSeconds)
        return Date(timeIntervalSince1970: Double(seconds * 1000 + Int64(millis)) / 1000)
    }

    /// Days since 1970-01-01 (Howard Hinnant's days_from_civil).
    private static func days(fromYear year: Int, month: Int, day: Int) -> Int64 {
        let y = Int64(month <= 2 ? year - 1 : year)
        let era = y.floorDiv(400)
        let yearOfEra = y - era * 400
        let m = Int64(month)
        let dayOfYear = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + Int64(day) - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    private static func civil(fromDays days: Int64) -> (Int, Int, Int) {
        let z = days + 719_468
        let era = z.floorDiv(146_097)
        let dayOfEra = z - era * 146_097
        let yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146_096) / 365
        let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        let mp = (5 * dayOfYear + 2) / 153
        let day = dayOfYear - (153 * mp + 2) / 5 + 1
        let month = mp < 10 ? mp + 3 : mp - 9
        let year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0)
        return (Int(year), Int(month), Int(day))
    }
}

private extension Int64 {
    func floorDiv(_ divisor: Int64) -> Int64 {
        let q = self / divisor
        return (self % divisor != 0 && (self < 0) != (divisor < 0)) ? q - 1 : q
    }
}
