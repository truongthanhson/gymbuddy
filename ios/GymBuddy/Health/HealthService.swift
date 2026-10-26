import Foundation
import HealthKit

/// Writes finished workouts and body weight to Apple Health when the user turned it on in settings.
/// Samples carry our row id as the sync identifier, so writing the same row again replaces it instead of adding a copy.
/// Each call uses its own HKHealthStore, so no HealthKit object crosses actors.
enum HealthService {
    static var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    private static var isEnabled: Bool { UserDefaults.standard.bool(forKey: SettingsKey.healthEnabled) }

    private static var shareTypes: Set<HKSampleType> { [HKObjectType.workoutType(), HKQuantityType(.bodyMass)] }

    /// Asks for write access; true when the user allowed every type.
    static func requestAuthorization() async -> Bool {
        guard isAvailable else { return false }
        let store = HKHealthStore()
        do {
            try await store.requestAuthorization(toShare: shareTypes, read: [])
        } catch {
            return false
        }
        return shareTypes.allSatisfy { store.authorizationStatus(for: $0) == .sharingAuthorized }
    }

    /// Saves a finished workout as a strength training session.
    static func saveWorkout(id: UUID, name: String, start: Date, end: Date) {
        guard isEnabled, isAvailable, end > start else { return }
        Task.detached { await writeWorkout(id: id, name: name, start: start, end: end) }
    }

    /// Saves one body weight entry (kg).
    static func saveBodyWeight(id: UUID, kg: Double, at date: Date) {
        guard isEnabled, isAvailable else { return }
        Task.detached { await writeBodyWeight(id: id, kg: kg, at: date) }
    }

    // Health data is a copy; a failed write must never break logging, so errors are dropped.

    private static func writeWorkout(id: UUID, name: String, start: Date, end: Date) async {
        let store = HKHealthStore()
        guard store.authorizationStatus(for: HKObjectType.workoutType()) == .sharingAuthorized else { return }
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .traditionalStrengthTraining
        let builder = HKWorkoutBuilder(healthStore: store, configuration: configuration, device: .local())
        do {
            try await builder.beginCollection(at: start)
            try await builder.addMetadata([
                HKMetadataKeySyncIdentifier: id.uuidString,
                HKMetadataKeySyncVersion: Int(Date().timeIntervalSince1970),
                HKMetadataKeyWorkoutBrandName: name,
            ])
            try await builder.endCollection(at: end)
            _ = try await builder.finishWorkout()
        } catch {}
    }

    private static func writeBodyWeight(id: UUID, kg: Double, at date: Date) async {
        let store = HKHealthStore()
        let type = HKQuantityType(.bodyMass)
        guard store.authorizationStatus(for: type) == .sharingAuthorized else { return }
        let sample = HKQuantitySample(
            type: type,
            quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: kg),
            start: date,
            end: date,
            metadata: [HKMetadataKeySyncIdentifier: id.uuidString, HKMetadataKeySyncVersion: 1]
        )
        try? await store.save(sample)
    }
}
