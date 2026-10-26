import SwiftData
import SwiftUI

/// Create (`exercise` nil) or edit a custom exercise. `onCreated` gets the new exercise's id.
struct ExerciseEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let exercise: Exercise?
    var onCreated: (String) -> Void = { _ in }

    @State private var name = ""
    @State private var bodyPart: BodyPart = .chest
    @State private var category: ExerciseCategory = .barbell
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                Picker("Body part", selection: $bodyPart) {
                    ForEach(BodyPart.allCases) { part in
                        Text(part.label).tag(part)
                    }
                }
                Picker("Category", selection: $category) {
                    ForEach(ExerciseCategory.allCases) { option in
                        Text(option.label).tag(option)
                    }
                }
                if exercise != nil {
                    Section {
                        Button("Delete", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(exercise == nil ? Text("New exercise") : Text("Edit exercise"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .confirmationDialog("Delete exercise?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let exercise { ExerciseService.delete(exercise, in: context) }
                    dismiss()
                }
            } message: {
                Text("Past workouts keep their sets for this exercise.")
            }
            .onAppear {
                guard let exercise else { return }
                name = exercise.name
                bodyPart = BodyPart(key: exercise.bodyPart)
                category = ExerciseCategory(key: exercise.category)
            }
        }
    }

    private func save() {
        if let exercise {
            ExerciseService.update(exercise, name: name, bodyPart: bodyPart, category: category, in: context)
        } else {
            let created = ExerciseService.createCustom(name: name, bodyPart: bodyPart, category: category, in: context)
            onCreated(created.id)
        }
        dismiss()
    }
}
