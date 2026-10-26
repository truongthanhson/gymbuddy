import SwiftUI

/// Which plates to load on each side of the bar for a target weight, in the user's unit.
struct PlateCalculatorView: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var target: Double?
    @State private var bar: Double?

    var body: some View {
        Form {
            Section {
                weightField("Target weight", value: $target)
                weightField("Bar", value: $bar)
            }
            if let target {
                result(plateLoad(target: target, bar: bar ?? 0, plates: weightUnit.defaultPlates))
            }
        }
        .navigationTitle("Plate calculator")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if bar == nil { bar = weightUnit.defaultBar } }
        .onChange(of: weightUnit) { _, unit in bar = unit.defaultBar }
    }

    private func weightField(_ label: LocalizedStringKey, value: Binding<Double?>) -> some View {
        LabeledContent(label) {
            HStack {
                TextField(label, value: value, format: .number)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                Text(verbatim: weightUnit.symbol).foregroundStyle(.secondary)
            }
        }
    }

    private func amount(_ value: Double) -> String { "\(formatWeight(value)) \(weightUnit.symbol)" }

    private func result(_ load: PlateLoad) -> some View {
        let counts: [(plate: Double, count: Int)] = Dictionary(grouping: load.perSide, by: { $0 })
            .map { (plate: $0.key, count: $0.value.count) }
            .sorted { $0.plate > $1.plate }
        return Section {
            if counts.isEmpty {
                Text("Just the bar")
            } else {
                ForEach(counts, id: \.plate) { item in
                    LabeledContent(amount(item.plate), value: "× \(item.count)")
                }
            }
            LabeledContent("Loaded", value: amount(load.loaded))
            if abs(load.remainder) >= 0.01 {
                Text("\(amount(abs(load.remainder))) off the target with these plates")
                    .foregroundStyle(.red)
            }
        } header: {
            Text("Plates per side")
        }
    }
}

/// Estimated one-rep max from any set, plus what that predicts for 1–12 reps.
struct OneRepMaxCalculatorView: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @State private var weight: Double?
    @State private var reps: Int?

    var body: some View {
        Form {
            Section {
                LabeledContent("Weight") {
                    HStack {
                        TextField("Weight", value: $weight, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                        Text(verbatim: weightUnit.symbol).foregroundStyle(.secondary)
                    }
                }
                LabeledContent("Reps") {
                    TextField("Reps", value: $reps, format: .number)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
            }
            if let weight, let reps, reps > 0 {
                // The formula is unit-free, so the typed value is used as is.
                table(oneRepMax: estimatedOneRepMax(weightKg: weight, reps: reps))
            }
        }
        .navigationTitle("1RM calculator")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func amount(_ value: Double) -> String {
        "\(formatWeight((value * 100).rounded() / 100)) \(weightUnit.symbol)"
    }

    @ViewBuilder
    private func table(oneRepMax: Double) -> some View {
        Section {
            LabeledContent("Estimated 1RM") {
                Text(verbatim: amount(oneRepMax)).font(.title2.bold())
            }
        }
        Section {
            ForEach(1...12, id: \.self) { count in
                let value = repMaxWeight(oneRepMax: oneRepMax, reps: count)
                HStack {
                    Text("\(count) reps")
                    Spacer()
                    Text(verbatim: "\(Int((value / oneRepMax * 100).rounded()))%").foregroundStyle(.secondary)
                    Text(verbatim: amount(value)).frame(minWidth: 90, alignment: .trailing)
                }
            }
        }
    }
}
