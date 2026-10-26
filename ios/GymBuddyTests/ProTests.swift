import XCTest
@testable import GymBuddy

final class ProTests: XCTestCase {
    func testFreeUsersKeepAFewRoutines() {
        XCTAssertTrue(canAddTemplate(existing: 0, isPro: false))
        XCTAssertTrue(canAddTemplate(existing: freeTemplateLimit - 1, isPro: false))
        XCTAssertFalse(canAddTemplate(existing: freeTemplateLimit, isPro: false))
        XCTAssertTrue(canAddTemplate(existing: 100, isPro: true))
    }

    func testBodyWeightStaysFree() {
        XCTAssertFalse(measurementNeedsPro(.weight))
        XCTAssertEqual(MeasurementKind.allCases.filter(measurementNeedsPro).count, MeasurementKind.allCases.count - 1)
    }

    func testStoreIdsMatchAndroid() {
        XCTAssertEqual(ProProducts.all, ["gymbuddy_pro_monthly", "gymbuddy_pro_yearly", "gymbuddy_pro_lifetime"])
    }
}
