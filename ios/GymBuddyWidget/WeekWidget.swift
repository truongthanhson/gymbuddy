import SwiftUI
import WidgetKit

struct WeekEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

struct WeekProvider: TimelineProvider {
    func placeholder(in context: Context) -> WeekEntry {
        WeekEntry(date: .now, snapshot: WidgetSnapshot(recentStarts: [.now, .now], lastName: "Push", lastStartedAt: .now))
    }

    func getSnapshot(in context: Context, completion: @escaping (WeekEntry) -> Void) {
        completion(WeekEntry(date: .now, snapshot: WidgetSnapshot.load()))
    }

    /// Redraws at midnight so the week count and dates stay current without the app.
    func getTimeline(in context: Context, completion: @escaping (Timeline<WeekEntry>) -> Void) {
        let now = Date.now
        let midnight = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: now)) ?? now.addingTimeInterval(86_400)
        completion(Timeline(entries: [WeekEntry(date: now, snapshot: WidgetSnapshot.load())], policy: .after(midnight)))
    }
}

struct WeekWidgetView: View {
    let entry: WeekEntry

    var body: some View {
        let count = entry.snapshot?.workoutsThisWeek(now: entry.date) ?? 0
        VStack(alignment: .leading, spacing: 2) {
            Text("This week")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(verbatim: "\(count)")
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(Color.accentColor)
            Text("^[\(count) workout](inflect: true)")
                .font(.caption)
            Spacer(minLength: 4)
            if let name = entry.snapshot?.lastName, let date = entry.snapshot?.lastStartedAt {
                Text("Last: \(name), \(date.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            } else {
                Text("No workouts yet")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct WeekWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WeekWidget", provider: WeekProvider()) { entry in
            WeekWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("GymBuddy week")
        .description("Workouts this week and your latest one.")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct GymBuddyWidgetBundle: WidgetBundle {
    var body: some Widget {
        WeekWidget()
    }
}
