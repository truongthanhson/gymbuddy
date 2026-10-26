import Foundation
import XCTest
@testable import GymBuddy

/// Runs the cross-platform test vectors in shared/test-vectors.
final class SharedVectorsTests: XCTestCase {
    private func vectors<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }

    func testEpleyMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let weightKg: Double; let reps: Int; let expected: Double }
            let cases: [Case]
        }
        for c in try vectors("one_rep_max", as: File.self).cases {
            XCTAssertEqual(estimatedOneRepMax(weightKg: c.weightKg, reps: c.reps), c.expected,
                           accuracy: 0.01, "\(c.weightKg) x \(c.reps)")
        }
    }

    func testWeightConversionMatchesSharedVectors() throws {
        struct File: Decodable {
            struct Case: Decodable { let kg: Double; let lb: Double }
            let cases: [Case]
        }
        for c in try vectors("units", as: File.self).cases {
            XCTAssertEqual(WeightUnit.lb.fromKg(c.kg), c.lb, accuracy: 0.01)
            XCTAssertEqual(WeightUnit.lb.toKg(c.lb), c.kg, accuracy: 0.01)
        }
    }
}
