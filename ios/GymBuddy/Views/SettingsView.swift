import SwiftUI

struct SettingsView: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @AppStorage(SettingsKey.defaultRestSeconds) private var restSeconds = defaultRestSeconds
    @Environment(\.openURL) private var openURL

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Units") {
                    Picker("Weight", selection: $weightUnit) {
                        ForEach(WeightUnit.allCases) { unit in
                            Text(verbatim: unit.symbol).tag(unit)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    Picker("Default rest timer", selection: $restSeconds) {
                        ForEach(restTimeOptions, id: \.self) { option in
                            Text(verbatim: restLabel(option)).tag(option)
                        }
                    }
                    // iOS keeps a per-app language in the app's page of the Settings app.
                    Button("Language") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                } header: {
                    Text("General")
                } footer: {
                    Text("The default rest timer is used when an exercise has no rest time of its own. The app language is chosen in the Settings app.")
                }
                Section("About") {
                    LabeledContent("GymBuddy", value: version)
                }
            }
            .navigationTitle("Settings")
        }
    }
}
