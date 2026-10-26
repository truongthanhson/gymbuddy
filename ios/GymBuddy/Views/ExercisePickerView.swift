import SwiftData
import SwiftUI

/// Multi-select exercise list; `onDone` receives the picked ids in the order they were tapped.
/// With `single`, tapping one exercise finishes right away, for "Replace exercise".
struct ExercisePickerView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selected: [String] = []
    @State private var creating = false
    var single = false
    let onDone: ([String]) -> Void

    private func finish(_ ids: [String]) {
        onDone(ids)
        dismiss()
    }

    var body: some View {
        NavigationStack {
            ExerciseBrowser(isSelected: { selected.contains($0.id) }) { exercise in
                if single {
                    finish([exercise.id])
                } else if let index = selected.firstIndex(of: exercise.id) {
                    selected.remove(at: index)
                } else {
                    selected.append(exercise.id)
                }
            }
            .navigationTitle(single ? "Replace exercise" : "Add exercises")
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
                if !single {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add (\(selected.count))") { finish(selected) }
                            .disabled(selected.isEmpty)
                    }
                }
            }
            .sheet(isPresented: $creating) {
                ExerciseEditorView(exercise: nil) { id in
                    if single { finish([id]) } else { selected.append(id) }
                }
            }
        }
    }
}
