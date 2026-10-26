import Foundation
import XCTest
@testable import GymBuddy

final class RestTimerTests: XCTestCase {
    private struct File: Decodable {
        struct Resolve: Decodable { let workoutExerciseRest: Int?; let exerciseDefault: Int?; let expected: Int }
        struct Remaining: Decodable { let startSeconds: Int; let elapsedMillis: Int; let expected: Int }
        struct Adjust: Decodable {
            let startSeconds: Int
            let elapsedMillis: Int
            let deltaSeconds: Int
            let expectedRemaining: Int?
            let expectedTotal: Int?
        }
        let resolve: [Resolve]
        let remaining: [Remaining]
        let adjust: [Adjust]
    }

    private func vectors() throws -> File {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "rest_timer", withExtension: "json"))
        return try JSONDecoder().decode(File.self, from: Data(contentsOf: url))
    }

    private let start = Date(timeIntervalSince1970: 0)

    private func after(_ millis: Int) -> Date {
        start.addingTimeInterval(TimeInterval(millis) / 1000)
    }

    func testResolveMatchesSharedVectors() throws {
        for c in try vectors().resolve {
            XCTAssertEqual(
                resolveRestSeconds(workoutExerciseRest: c.workoutExerciseRest, exerciseDefault: c.exerciseDefault),
                c.expected
            )
        }
    }

    func testRemainingMatchesSharedVectors() throws {
        for c in try vectors().remaining {
            let state = try XCTUnwrap(RestTimerState.start(seconds: c.startSeconds, now: start))
            XCTAssertEqual(state.remainingSeconds(at: after(c.elapsedMillis)), c.expected, "\(c)")
        }
    }

    func testAdjustMatchesSharedVectors() throws {
        for c in try vectors().adjust {
            let now = after(c.elapsedMillis)
            let state = try XCTUnwrap(RestTimerState.start(seconds: c.startSeconds, now: start))
            let adjusted = state.adjusted(by: c.deltaSeconds, now: now)
            XCTAssertEqual(adjusted?.remainingSeconds(at: now), c.expectedRemaining, "\(c)")
            XCTAssertEqual(adjusted?.totalSeconds, c.expectedTotal, "\(c)")
        }
    }

    func testZeroSecondsMeansNoTimer() {
        XCTAssertNil(RestTimerState.start(seconds: 0))
    }
}
