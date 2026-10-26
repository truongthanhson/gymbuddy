import SwiftData
import SwiftUI

struct ExercisesView: View {
    @State private var creating = false
    @State private var selected: Exercise?

    var body: some View {
        NavigationStack {
            ExerciseBrowser(onTap: { selected = $0 })
            .navigationTitle("Exercises")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: {
                        Label("New exercise", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $creating) { ExerciseEditorView(exercise: nil) }
            .navigationDestination(item: $selected) { ExerciseDetailView(exercise: $0) }
        }
    }
}

/// Search, body-part chips and the exercise list; used by the Exercises tab and the exercise picker.
struct ExerciseBrowser: View {
    @Query(
        filter: #Predicate<Exercise> { $0.deletedAt == nil },
        sort: \Exercise.name
    )
    private var exercises: [Exercise]

    @State private var query = ""
    @State private var bodyPart: BodyPart?

    private let isSelected: (Exercise) -> Bool
    private let onTap: ((Exercise) -> Void)?

    init(isSelected: @escaping (Exercise) -> Bool = { _ in false }, onTap: ((Exercise) -> Void)? = nil) {
        self.isSelected = isSelected
        self.onTap = onTap
    }

    private var filtered: [Exercise] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return exercises.filter { exercise in
            (bodyPart == nil || exercise.bodyPart == bodyPart?.rawValue)
                && (q.isEmpty
                    || exercise.name.localizedStandardContains(q)
                    || (exercise.nameVi?.localizedStandardContains(q) ?? false))
        }
    }

    var body: some View {
        List {
            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        chip(Text("All"), selected: bodyPart == nil) { bodyPart = nil }
                        ForEach(BodyPart.allCases) { part in
                            chip(Text(part.label), selected: bodyPart == part) { bodyPart = part }
                        }
                    }
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
            }
            ForEach(filtered) { exercise in
                row(exercise)
            }
        }
        .appBackground()
        .overlay {
            if filtered.isEmpty {
                ContentUnavailableView("No exercises found", systemImage: "magnifyingglass")
            }
        }
        .searchable(text: $query, prompt: Text("Search exercises"))
    }

    @ViewBuilder
    private func row(_ exercise: Exercise) -> some View {
        let content = HStack(spacing: Spacing.md) {
            VStack(alignment: .leading) {
                Text(verbatim: exercise.displayName).font(.body.weight(.medium))
                Group {
                    if exercise.isCustom {
                        Text(BodyPart(key: exercise.bodyPart).label) + Text(verbatim: " · ") + Text("Custom")
                    } else {
                        Text(BodyPart(key: exercise.bodyPart).label)
                    }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            Spacer()
            if isSelected(exercise) {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.primary)
            }
        }
        if let onTap {
            Button { onTap(exercise) } label: { content.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            content
        }
    }

    private func chip(_ label: Text, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { label }
            .buttonStyle(.bordered)
            .tint(selected ? .accentColor : .secondary)
    }
}
