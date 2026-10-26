import SwiftData
import SwiftUI

struct ExercisesView: View {
    @Query(
        filter: #Predicate<Exercise> { $0.deletedAt == nil },
        sort: \Exercise.name
    )
    private var exercises: [Exercise]

    @State private var query = ""
    @State private var bodyPart: BodyPart?

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
        NavigationStack {
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
                    VStack(alignment: .leading) {
                        Text(exercise.displayName)
                        Text(BodyPart(key: exercise.bodyPart).label)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .overlay {
                if filtered.isEmpty {
                    ContentUnavailableView("No exercises found", systemImage: "magnifyingglass")
                }
            }
            .searchable(text: $query, prompt: Text("Search exercises"))
            .navigationTitle("Exercises")
        }
    }

    private func chip(_ label: Text, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { label }
            .buttonStyle(.bordered)
            .tint(selected ? .accentColor : .secondary)
    }
}
