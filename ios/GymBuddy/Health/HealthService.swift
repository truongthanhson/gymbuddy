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

    // Buddy may read sleep and steps when the person shares them in Buddy's settings.

    private static var buddyReadTypes: Set<HKObjectType> { [HKCategoryType(.sleepAnalysis), HKQuantityType(.stepCount)] }

    /// Asks for read access to sleep and steps. iOS never says whether reading was allowed, so this only reports errors.
    static func requestBuddyReadAuthorization() async -> Bool {
        guard isAvailable else { return false }
        do {
            try await HKHealthStore().requestAuthorization(toShare: [], read: buddyReadTypes)
            return true
        } catch {
            return false
        }
    }

    /// Sleep (hours, by the day the sleep ended) and steps for each of the last `days` days, oldest first.
    /// Days without data, or without read access, come back as nil.
    static func recentSleepAndSteps(days: Int = 7, now: Date = .now) async -> [DayHealth] {
        guard isAvailable else { return [] }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        guard let first = calendar.date(byAdding: .day, value: -(days - 1), to: today),
              let end = calendar.date(byAdding: .day, value: 1, to: today) else { return [] }
        let store = HKHealthStore()

        var steps: [Date: Double] = [:]
        let stepsQuery = HKStatisticsCollectionQueryDescriptor(
            predicate: .quantitySample(type: HKQuantityType(.stepCount), predicate: HKQuery.predicateForSamples(withStart: first, end: end)),
            options: .cumulativeSum,
            anchorDate: first,
            intervalComponents: DateComponents(day: 1)
        )
        if let collection = try? await stepsQuery.result(for: store) {
            collection.enumerateStatistics(from: first, to: end) { stats, _ in
                if let sum = stats.sumQuantity() { steps[calendar.startOfDay(for: stats.startDate)] = sum.doubleValue(for: .count()) }
            }
        }

        var sleepMinutes: [Date: Double] = [:]
        let sleepQuery = HKSampleQueryDescriptor(
            predicates: [.categorySample(
                type: HKCategoryType(.sleepAnalysis),
                predicate: HKQuery.predicateForSamples(withStart: first.addingTimeInterval(-86400), end: end)
            )],
            sortDescriptors: []
        )
        let asleep = HKCategoryValueSleepAnalysis.allAsleepValues.map(\.rawValue)
        for sample in (try? await sleepQuery.result(for: store)) ?? [] where asleep.contains(sample.value) {
            let day = calendar.startOfDay(for: sample.endDate)
            guard day >= first, day <= today else { continue }
            sleepMinutes[day, default: 0] += sample.endDate.timeIntervalSince(sample.startDate) / 60
        }

        return (0..<days).compactMap { i in
            guard let day = calendar.date(byAdding: .day, value: i, to: first) else { return nil }
            return DayHealth(date: day, sleepHours: sleepMinutes[day].map { $0 / 60 }, steps: steps[day].map { Int($0) })
        }
    }
}

/// One day of the health data Buddy may read.
struct DayHealth: Sendable {
    let date: Date
    let sleepHours: Double?
    let steps: Int?
}
