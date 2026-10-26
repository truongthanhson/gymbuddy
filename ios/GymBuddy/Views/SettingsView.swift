import SwiftUI

struct SettingsView: View {
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @AppStorage(SettingsKey.defaultRestSeconds) private var restSeconds = defaultRestSeconds
    @Environment(\.openURL) private var openURL
    @Environment(\.modelContext) private var context
    @AppStorage(SettingsKey.healthEnabled) private var healthEnabled = false
    @State private var exportDocument: CsvDocument?
    @State private var exporting = false
    @State private var importing = false
    @State private var exportedCount = 0
    @State private var csvMessage = ""
    @State private var showingCsvMessage = false

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        NavigationStack {
            Form {
                AccountSection()
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
                Section("Tools") {
                    NavigationLink("Plate calculator") { PlateCalculatorView() }
                    NavigationLink("1RM calculator") { OneRepMaxCalculatorView() }
                }
                dataSection
                Section("About") {
                    LabeledContent("GymBuddy", value: version)
                }
            }
            .navigationTitle("Settings")
            .alert(csvMessage, isPresented: $showingCsvMessage) {
                Button("Close", role: .cancel) {}
            }
        }
    }

    private var dataSection: some View {
        Section {
            if HealthService.isAvailable {
                Toggle("Apple Health", isOn: $healthEnabled)
                    // Turning it on asks for write access first and turns back off if it is refused.
                    .onChange(of: healthEnabled) { _, on in
                        guard on else { return }
                        Task { healthEnabled = await HealthService.requestAuthorization() }
                    }
            }
            Button("Export workouts (CSV)", action: export)
                .fileExporter(
                    isPresented: $exporting,
                    document: exportDocument,
                    contentType: .commaSeparatedText,
                    defaultFilename: "gymbuddy-workouts"
                ) { result in
                    switch result {
                    case .success: show(String(localized: "Workouts exported: \(exportedCount)"))
                    case .failure: show(String(localized: "The file could not be read or written."))
                    }
                }
            Button("Import workouts (CSV)") { importing = true }
                // Some providers report CSV files as plain text.
                .fileImporter(isPresented: $importing, allowedContentTypes: [.commaSeparatedText, .plainText]) { result in
                    importFile(result)
                }
        } header: {
            Text("Data")
        } footer: {
            Text("Apple Health gets each finished workout and body weight. Exports use the same columns as Strong, and imports accept GymBuddy and Strong CSV exports.")
        }
    }

    private func show(_ message: String) {
        csvMessage = message
        showingCsvMessage = true
    }

    private func export() {
        do {
            let export = try CsvService.export(unit: weightUnit, in: context)
            exportDocument = CsvDocument(text: export.text)
            exportedCount = export.workouts
            exporting = true
        } catch {
            show(String(localized: "The file could not be read or written."))
        }
    }

    private func importFile(_ result: Result<URL, Error>) {
        do {
            let url = try result.get()
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let text = try String(contentsOf: url, encoding: .utf8)
            guard let imported = try CsvService.importText(text, defaultUnit: weightUnit, in: context) else {
                show(String(localized: "This file is not a workout CSV export."))
                return
            }
            var message = String(localized: "Workouts imported: \(imported.imported)")
            if imported.skipped > 0 {
                message += "\n" + String(localized: "Already on this device, skipped: \(imported.skipped)")
            }
            show(message)
            if imported.imported > 0 { SyncService.shared.requestSync() }
        } catch {
            show(String(localized: "The file could not be read or written."))
        }
    }
}
