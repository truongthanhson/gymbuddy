import Charts
import SwiftData
import SwiftUI

/// The Measure tab: every kind with its latest value.
struct MeasurementsView: View {
    @Query(filter: #Predicate<BodyMeasurement> { $0.deletedAt == nil }, sort: \BodyMeasurement.measuredAt, order: .reverse)
    private var measurements: [BodyMeasurement]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg

    private var latest: [String: BodyMeasurement] {
        var result: [String: BodyMeasurement] = [:]
        for measurement in measurements where result[measurement.kind] == nil {
            result[measurement.kind] = measurement
        }
        return result
    }

    var body: some View {
        let latest = latest
        NavigationStack {
            List(MeasurementKind.allCases) { kind in
                NavigationLink {
                    MeasurementDetailView(kind: kind)
                } label: {
                    row(kind, latest: latest[kind.rawValue])
                }
            }
            .navigationTitle("Measure")
        }
    }

    private func row(_ kind: MeasurementKind, latest: BodyMeasurement?) -> some View {
        HStack {
            VStack(alignment: .leading) {
                Text(kind.label)
                if let latest {
                    Text(latest.measuredAt, format: .dateTime.day().month().year())
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(verbatim: latest.map { kind.format($0.value, weightUnit) } ?? "—")
                .font(.headline)
        }
    }
}

/// History and chart of one kind, with adding and deleting entries.
struct MeasurementDetailView: View {
    let kind: MeasurementKind

    @Environment(\.modelContext) private var context
    @Query private var entries: [BodyMeasurement]
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var adding = false
    @State private var typed: Double?

    init(kind: MeasurementKind) {
        self.kind = kind
        let key = kind.rawValue
        _entries = Query(
            filter: #Predicate<BodyMeasurement> { $0.kind == key && $0.deletedAt == nil },
            sort: \BodyMeasurement.measuredAt,
            order: .reverse
        )
    }

    var body: some View {
        List {
            if entries.count >= 2 {
                Section { chart.frame(height: 220) }
            }
            ForEach(entries) { entry in
                HStack {
                    Text(verbatim: kind.format(entry.value, weightUnit))
                    Spacer()
                    Text(entry.measuredAt, format: .dateTime.day().month().year())
                        .foregroundStyle(.secondary)
                }
            }
            .onDelete(perform: delete)
        }
        .overlay {
            if entries.isEmpty {
                ContentUnavailableView("No entries yet. Tap + to log one.", systemImage: "ruler")
            }
        }
        .navigationTitle(kind.label)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    typed = entries.first.map { kind.display($0.value, weightUnit) }
                    adding = true
                } label: {
                    Label("Add measurement", systemImage: "plus")
                }
            }
        }
        .alert(kind.label, isPresented: $adding) {
            TextField("Value", value: $typed, format: .number)
                .keyboardType(.decimalPad)
            Button("Save", action: save)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(verbatim: kind.symbol(weightUnit))
        }
    }

    private var chart: some View {
        let unit = weightUnit
        let kind = kind
        return Chart(entries) { entry in
            let value = kind.display(entry.value, unit)
            LineMark(x: .value("Date", entry.measuredAt), y: .value("Value", value))
            PointMark(x: .value("Date", entry.measuredAt), y: .value("Value", value))
        }
        .chartYScale(domain: .automatic(includesZero: false))
        .chartYAxisLabel(kind.symbol(unit))
    }

    private func save() {
        guard let typed, typed > 0 else { return }
        let measurement = BodyMeasurement(kind: kind.rawValue, value: kind.toStored(typed, weightUnit), unit: kind.unit.rawValue)
        context.insert(measurement)
        try? context.save()
        if kind == .weight {
            HealthService.saveBodyWeight(id: measurement.id, kg: measurement.value, at: measurement.measuredAt)
        }
        SyncService.shared.requestSync()
    }

    /// Soft delete, so the delete reaches other devices through sync.
    private func delete(_ offsets: IndexSet) {
        let now = Date()
        for index in offsets {
            entries[index].deletedAt = now
            entries[index].updatedAt = now
        }
        try? context.save()
        SyncService.shared.requestSync()
    }
}
