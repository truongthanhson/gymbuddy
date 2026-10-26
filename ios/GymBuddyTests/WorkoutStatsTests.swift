import Foundation
import XCTest
@testable import GymBuddy

final class WorkoutStatsTests: XCTestCase {
    private func vectors<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }

    func testVolumeAndBestSetMatchSharedVectors() throws {
        struct File: Decodable {
            struct SetCase: Decodable { let weightKg: Double?; let reps: Int?; let completed: Bool }
            struct Case: Decodable { let name: String; let sets: [SetCase]; let volumeKg: Double; let bestSetIndex: Int? }
            let cases: [Case]
        }
        for c in try vectors("workout_stats", as: File.self).cases {
            let sets = c.sets.map { SetValues(weightKg: $0.weightKg, reps: $0.reps, completed: $0.completed) }
            XCTAssertEqual(volumeKg(sets), c.volumeKg, accuracy: 0.01, c.name)
            XCTAssertEqual(bestSetIndex(sets), c.bestSetIndex, c.name)
        }
    }

    func testElapsedFormatMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let seconds: Int; let text: String }
            let cases: [Case]
        }
        for c in try vectors("elapsed", as: File.self).cases {
            XCTAssertEqual(formatElapsed(c.seconds), c.text)
        }
    }

    func testWeightFormatDropsTrailingZeros() {
        XCTAssertEqual(formatWeight(100), "100")
        XCTAssertEqual(formatWeight(102.5), "102.5")
        XCTAssertEqual(formatWeight(2.25), "2.25")
    }
}
