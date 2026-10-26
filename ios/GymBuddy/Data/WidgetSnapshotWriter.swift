import Foundation
import SwiftData
import WidgetKit

/// Copies what the home screen widget shows into the shared app group and asks WidgetKit to redraw.
@MainActor
enum WidgetSnapshotWriter {
    static func update(in context: ModelContext, now: Date = .now) {
        let since = now.addingTimeInterval(-8 * 86_400)
        let recent = #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil && $0.startedAt >= since }
        let finished = #Predicate<Workout> { $0.endedAt != nil && $0.deletedAt == nil }
        var latest = FetchDescriptor(predicate: finished, sortBy: [SortDescriptor(\.startedAt, order: .reverse)])
        latest.fetchLimit = 1
        guard let starts = try? context.fetch(FetchDescriptor(predicate: recent)).map(\.startedAt),
              let lastFetch = try? context.fetch(latest)
        else { return }
        let last = lastFetch.first
        WidgetSnapshot(recentStarts: starts, lastName: last?.name, lastStartedAt: last?.startedAt).save()
        WidgetCenter.shared.reloadAllTimelines()
    }
}
