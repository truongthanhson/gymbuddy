import Charts
import SwiftUI

struct ChartPoint: Identifiable, Equatable {
    let date: Date
    let value: Double
    var id: Date { date }
}

/// A time-series line that rises into place, follows the finger with a value bubble, and zooms to 1M/3M/6M/1Y.
/// `points` are oldest first. The header shows the touched value (or the latest one) and the change since the start
/// of the range, coloured by `higherIsBetter` (nil keeps it neutral, e.g. body weight).
struct InteractiveLineChart: View {
    let points: [ChartPoint]
    let formatValue: (Double) -> String
    var higherIsBetter: Bool?
    var height: CGFloat = 220
    var showsRanges = true

    @State private var range: ChartRange = .all
    @State private var selectedDate: Date?
    @State private var revealed = false

    private var ranges: [ChartRange] { ChartRange.available(points, date: \.date) }

    private var shown: [ChartPoint] {
        (ranges.contains(range) ? range : .all).filter(points, date: \.date)
    }

    private func selectedIndex(in shown: [ChartPoint]) -> Int? {
        guard let selectedDate else { return nil }
        return nearestIndex(shown.map(\.date.timeIntervalSince1970), to: selectedDate.timeIntervalSince1970)
    }

    var body: some View {
        let shown = shown
        let selected = selectedIndex(in: shown)
        VStack(alignment: .leading, spacing: 10) {
            if !shown.isEmpty {
                header(shown, focus: selected ?? shown.count - 1)
            }
            chart(shown, selected: selected)
                .frame(height: height)
            if showsRanges && ranges.count > 1 {
                Picker("Range", selection: $range.animation(.smooth)) {
                    ForEach(ranges) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
            }
        }
        .sensoryFeedback(.selection, trigger: selected)
        .onAppear {
            withAnimation(.spring(duration: 0.9, bounce: 0.2).delay(0.05)) { revealed = true }
        }
    }

    private func header(_ shown: [ChartPoint], focus: Int) -> some View {
        let point = shown[focus]
        let delta = point.value - shown[0].value
        let deltaColor: Color = switch higherIsBetter {
        case nil: .secondary
        case _ where delta == 0: .secondary
        case let better?: (delta > 0) == better ? .green : .red
        }
        return VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: formatValue(point.value))
                .font(.title.bold())
                .fontDesign(.rounded)
                .monospacedDigit()
                .contentTransition(.numericText(value: point.value))
            HStack(spacing: 6) {
                Text(point.date, format: .dateTime.day().month().year())
                if focus > 0 {
                    Text(verbatim: (delta > 0 ? "▲ " : delta < 0 ? "▼ " : "") + formatValue(abs(delta)))
                        .foregroundStyle(deltaColor)
                        .fontWeight(.medium)
                    Text("since \(shown[0].date.formatted(date: .abbreviated, time: .omitted))")
                }
            }
            .font(.caption)
            .monospacedDigit()
            .foregroundStyle(.secondary)
        }
        .animation(.snappy, value: point)
    }

    private func chart(_ shown: [ChartPoint], selected: Int?) -> some View {
        let low = shown.map(\.value).min() ?? 0
        let gradient = LinearGradient(
            colors: [Color.accentColor.opacity(0.3), Color.accentColor.opacity(0)],
            startPoint: .top,
            endPoint: .bottom
        )
        return Chart {
            ForEach(shown) { point in
                // Before the reveal every point sits on the lowest value, so the line rises into place.
                let y = revealed ? point.value : low
                AreaMark(x: .value("Date", point.date), yStart: .value("Base", low), yEnd: .value("Value", y))
                    .interpolationMethod(.monotone)
                    .foregroundStyle(gradient)
                LineMark(x: .value("Date", point.date), y: .value("Value", y))
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    .foregroundStyle(Color.accentColor)
                if shown.count <= 60 || selected.map({ shown[$0].id == point.id }) == true {
                    PointMark(x: .value("Date", point.date), y: .value("Value", y))
                        .symbolSize(selected.map { shown[$0].id == point.id } == true ? 140 : 28)
                        .foregroundStyle(Color.accentColor)
                }
            }
            if let selected {
                let point = shown[selected]
                RuleMark(x: .value("Date", point.date))
                    .foregroundStyle(Color.secondary.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .annotation(position: .top, spacing: 0, overflowResolution: .init(x: .fit(to: .chart), y: .fit(to: .chart))) {
                        tooltip(point)
                    }
            }
        }
        .chartYScale(domain: .automatic(includesZero: false))
        .chartXSelection(value: $selectedDate)
        .chartYAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                AxisValueLabel()
            }
        }
    }

    private func tooltip(_ point: ChartPoint) -> some View {
        VStack(spacing: 2) {
            Text(verbatim: formatValue(point.value))
                .font(.callout.bold())
                .monospacedDigit()
            Text(point.date, format: .dateTime.day().month().year())
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
        .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
    }
}
