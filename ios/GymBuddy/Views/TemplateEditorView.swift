import SwiftData
import SwiftUI

/// Creates or edits a template on a draft, so Cancel leaves the template untouched.
struct TemplateEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @AppStorage(SettingsKey.weightUnit) private var weightUnit: WeightUnit = .kg
    @Query(filter: #Predicate<TemplateFolder> { $0.deletedAt == nil }, sort: \TemplateFolder.sortOrder)
    private var folders: [TemplateFolder]
    @Query private var exercises: [Exercise]

    let templateId: UUID?
    @State private var draft = TemplateDraft()
    @State private var loaded = false
    @State private var showPicker = false

    private var namesById: [String: Exercise] {
        Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Template name", text: $draft.name)
                    if !folders.isEmpty {
                        Picker("Folder", selection: $draft.folderId) {
                            Text("No folder").tag(UUID?.none)
                            ForEach(folders) { folder in
                                Text(verbatim: folder.name).tag(UUID?.some(folder.id))
                            }
                        }
                    }
                }
                ForEach($draft.exercises) { $exercise in
                    Section {
                        HStack {
                            Text("Set").frame(width: 32)
                            Text(verbatim: weightUnit.symbol).frame(maxWidth: .infinity)
                            Text("Reps").frame(maxWidth: .infinity)
                            Spacer().frame(width: 24)
                        }
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                        ForEach(Array($exercise.sets.enumerated()), id: \.element.id) { index, $set in
                            HStack {
                                Text(verbatim: "\(index + 1)").frame(width: 32)
                                TextField(weightUnit.symbol, value: $set.targetWeightKg.weight(in: weightUnit), format: .number)
                                    .keyboardType(.decimalPad)
                                    .multilineTextAlignment(.center)
                                    .textFieldStyle(.roundedBorder)
                                TextField("Reps", value: $set.targetReps, format: .number)
                                    .keyboardType(.numberPad)
                                    .multilineTextAlignment(.center)
                                    .textFieldStyle(.roundedBorder)
                                Button {
                                    exercise.sets.removeAll { $0.id == set.id }
                                } label: {
                                    Image(systemName: "xmark.circle")
                                }
                                .buttonStyle(.plain)
                                .foregroundStyle(.secondary)
                                .frame(width: 24)
                            }
                        }
                        Button("Add set") {
                            let last = exercise.sets.last
                            exercise.sets.append(.init(targetWeightKg: last?.targetWeightKg, targetReps: last?.targetReps))
                        }
                    } header: {
                        HStack {
                            Text(verbatim: namesById[exercise.exerciseId]?.displayName ?? exercise.exerciseId)
                                .font(.headline)
                                .foregroundStyle(Color.accentColor)
                                .textCase(nil)
                            Spacer()
                            Button("Remove exercise", systemImage: "trash", role: .destructive) {
                                let id = exercise.id
                                draft.exercises.removeAll { $0.id == id }
                            }
                            .labelStyle(.iconOnly)
                        }
                    }
                }
                Section {
                    Button("Add exercises") { showPicker = true }
                }
            }
            .navigationTitle(templateId == nil ? "New template" : "Edit template")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        TemplateService.save(draft, in: context)
                        dismiss()
                    }
                    .disabled(!draft.canSave)
                }
            }
            .sheet(isPresented: $showPicker) {
                ExercisePickerView { ids in
                    draft.exercises.append(contentsOf: ids.map { TemplateDraft.ExerciseDraft(exerciseId: $0) })
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                if let templateId, let template = TemplateService.template(id: templateId, in: context) {
                    draft = TemplateService.draft(for: template)
                }
            }
        }
    }
}
