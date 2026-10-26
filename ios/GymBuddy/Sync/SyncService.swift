import Foundation
import Observation
import SwiftData

/// Two-way sync with Supabase, last write wins on updatedAt; mirrors the Android SyncManager
/// (see the sync migration in supabase/migrations).
/// Push: every row changed since the last push, parents before children.
/// Pull: every row the server changed since the last pull (by its own clock), applied when newer than the local row.
@MainActor
@Observable
final class SyncService {
    static let shared = SyncService()

    private(set) var isRunning = false
    private(set) var lastSyncedAt: Date?
    private(set) var lastError: String?

    /// The app's main context; set once at launch.
    @ObservationIgnored var context: ModelContext?

    private let pageSize = 500
    /// A transaction that began earlier can commit later, so each pull starts a little before the saved position.
    private let overlap: TimeInterval = 60

    private enum Keys {
        static let prefix = "sync."
        static let lastSynced = "sync.lastSynced"
        static let pushedUntil = "sync.pushedUntil"
        static let pulledPrefix = "sync.pulledUntil."
    }

    private init() {
        lastSyncedAt = UserDefaults.standard.object(forKey: Keys.lastSynced) as? Date
    }

    /// Starts a sync in the background when signed in; does nothing if one is already running.
    func requestSync() {
        guard SupabaseConfig.current.isConfigured, AuthService.shared.session != nil, !isRunning else { return }
        Task { try? await sync() }
    }

    /// Pushes then pulls; waits for a sync that is already running to finish first.
    func sync() async throws {
        guard let context else { return }
        while isRunning { try await Task.sleep(for: .milliseconds(200)) }
        isRunning = true
        lastError = nil
        defer { isRunning = false }
        do {
            guard let token = try await AuthService.shared.accessToken() else {
                throw SupabaseError(message: String(localized: "Not signed in"))
            }
            try await push(token: token, context: context)
            try await pull(token: token, context: context)
            let now = Date()
            UserDefaults.standard.set(now, forKey: Keys.lastSynced)
            lastSyncedAt = now
        } catch {
            lastError = error.localizedDescription
            throw error
        }
    }

    /// Removes the account's rows from this device and forgets the sync position (used on sign out).
    func clearLocalData() throws {
        guard let context else { return }
        for workout in try context.fetch(FetchDescriptor<Workout>()) { context.delete(workout) }
        for template in try context.fetch(FetchDescriptor<WorkoutTemplate>()) { context.delete(template) }
        for folder in try context.fetch(FetchDescriptor<TemplateFolder>()) { context.delete(folder) }
        for measurement in try context.fetch(FetchDescriptor<BodyMeasurement>()) { context.delete(measurement) }
        for exercise in try context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.isCustom })) {
            context.delete(exercise)
        }
        try context.save()
        let defaults = UserDefaults.standard
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(Keys.prefix) {
            defaults.removeObject(forKey: key)
        }
        lastSyncedAt = nil
        lastError = nil
    }

    private func push(token: String, context: ModelContext) async throws {
        let startedAt = Date()
        let since = UserDefaults.standard.object(forKey: Keys.pushedUntil) as? Date ?? .distantPast
        for table in SyncTables.all {
            let rows = try table.changed(context, since)
            for start in stride(from: 0, to: rows.count, by: pageSize) {
                let body = try JSONSerialization.data(withJSONObject: Array(rows[start..<min(start + pageSize, rows.count)]))
                _ = try await SupabaseHTTP.request(
                    "POST",
                    "/rest/v1/\(table.name)?on_conflict=id",
                    body: body,
                    accessToken: token,
                    headers: ["Prefer": "resolution=merge-duplicates,return=minimal"]
                )
            }
        }
        UserDefaults.standard.set(startedAt, forKey: Keys.pushedUntil)
    }

    private func pull(token: String, context: ModelContext) async throws {
        for table in SyncTables.all {
            let key = Keys.pulledPrefix + table.name
            var after = UserDefaults.standard.string(forKey: key)
                .flatMap { Timestamp.parse($0) }
                .map { Timestamp.format($0.addingTimeInterval(-overlap)) }
            while true {
                var path = "/rest/v1/\(table.name)?select=*&order=synced_at.asc&limit=\(pageSize)"
                if let after { path += "&synced_at=gt.\(Self.queryEscaped(after))" }
                let data = try await SupabaseHTTP.request("GET", path, accessToken: token)
                guard let rows = try JSONSerialization.jsonObject(with: data) as? [SyncRow], !rows.isEmpty else { break }
                try table.apply(context, rows)
                try context.save()
                after = rows.last?["synced_at"] as? String
                UserDefaults.standard.set(after, forKey: key)
                if rows.count < pageSize { break }
            }
        }
    }

    /// "+" in a timezone offset must be escaped in a query string.
    private static func queryEscaped(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "+&=")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
