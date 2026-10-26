import SwiftData
import SwiftUI

/// Multi-select exercise list; `onDone` receives the picked ids in the order they were tapped.
struct ExercisePickerView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selected: [String] = []
    @State private var creating = false
    let onDone: ([String]) -> Void

    var body: some View {
        NavigationStack {
            ExerciseBrowser(isSelected: { selected.contains($0.id) }) { exercise in
                if let index = selected.firstIndex(of: exercise.id) {
                    selected.remove(at: index)
                } else {
                    selected.append(exercise.id)
                }
            }
            .navigationTitle("Add exercises")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: {
                        Label("New exercise", systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selected.count))") {
                        onDone(selected)
                        dismiss()
                    }
                    .disabled(selected.isEmpty)
                }
            }
            .sheet(isPresented: $creating) {
                ExerciseEditorView(exercise: nil) { selected.append($0) }
            }
        }
    }
}
