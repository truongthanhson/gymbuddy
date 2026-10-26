import XCTest
@testable import GymBuddy

final class PlateSettingsTests: XCTestCase {
    func testBarAndPlatesFallBackToDefaultsAndRoundTrip() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "PlateSettingsTests"))
        defaults.removePersistentDomain(forName: "PlateSettingsTests")

        XCTAssertEqual(WeightUnit.kg.bar(in: defaults), 20)
        XCTAssertEqual(WeightUnit.lb.plates(in: defaults), WeightUnit.lb.defaultPlates)

        WeightUnit.kg.setBar(15, in: defaults)
        WeightUnit.kg.setPlates([1.25, 20, 2.5, 20], in: defaults)
        XCTAssertEqual(WeightUnit.kg.bar(in: defaults), 15)
        XCTAssertEqual(WeightUnit.kg.plates(in: defaults), [20, 2.5, 1.25])
        // The other unit keeps its own values.
        XCTAssertEqual(WeightUnit.lb.bar(in: defaults), 45)
    }
}
