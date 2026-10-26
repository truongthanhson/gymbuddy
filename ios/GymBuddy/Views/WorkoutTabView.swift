import SwiftData
import SwiftUI

struct WorkoutTabView: View {
    @Environment(\.modelContext) private var context
    @Query(
        filter: #Predicate<Workout> { $0.endedAt == nil && $0.deletedAt == nil },
        sort: \Workout.startedAt,
        order: .reverse
    )
    private var activeWorkouts: [Workout]

    var body: some View {
        NavigationStack {
            if let workout = activeWorkouts.first {
                ActiveWorkoutView(workout: workout)
            } else {
                StartWorkoutView()
            }
        }
    }
}

/// Editor sheet target: a new template, or an existing one by id.
struct TemplateEditorTarget: Identifiable {
    let templateId: UUID?
    var id: String { templateId?.uuidString ?? "new" }
}

private struct StartWorkoutView: View {
    // Built outside the @Query attribute: inline sort arrays time out the Swift type checker.
    nonisolated static var templatesDescriptor: FetchDescriptor<WorkoutTemplate> {
        let notDeleted = #Predicate<WorkoutTemplate> { $0.deletedAt == nil }
        let sort: [SortDescriptor<WorkoutTemplate>] = [SortDescriptor(\.sortOrder), SortDescriptor(\.name)]
        return FetchDescriptor(predicate: notDeleted, sortBy: sort)
    }
    nonisolated static var foldersDescriptor: FetchDescriptor<TemplateFolder> {
        let notDeleted = #Predicate<TemplateFolder> { $0.deletedAt == nil }
        let sort: [SortDescriptor<TemplateFolder>] = [SortDescriptor(\.sortOrder), SortDescriptor(\.name)]
        return FetchDescriptor(predicate: notDeleted, sortBy: sort)
    }

    @Environment(\.modelContext) private var context
    @Query(StartWorkoutView.templatesDescriptor) private var templates: [WorkoutTemplate]
    @Query(StartWorkoutView.foldersDescriptor) private var folders: [TemplateFolder]
    @Query private var exercises: [Exercise]

    @State private var editing: TemplateEditorTarget?
    @State private var newFolder = false
    @State private var folderName = ""

    private var namesById: [String: Exercise] {
        Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private var looseTemplates: [WorkoutTemplate] {
        let known = Set(folders.map(\.id))
        return templates.filter { $0.folderId == nil || !known.contains($0.folderId!) }
    }

    var body: some View {
        List {
            Section("Quick start") {
                Button("Start an empty workout") {
                    RestTimer.requestAuthorization()
                    WorkoutService.startEmptyWorkout(name: String(localized: "Workout"), in: context)
                }
            }
            if templates.isEmpty {
                Section("Templates") {
                    Text("No templates yet. Create one, or save a finished workout as a template.")
                        .foregroundStyle(.secondary)
                }
            }
            if !looseTemplates.isEmpty {
                Section("My templates") {
                    ForEach(looseTemplates) { row($0) }
                }
            }
            ForEach(folders) { folder in
                Section {
                    ForEach(templates.filter { $0.folderId == folder.id }) { row($0) }
                } header: {
                    FolderHeader(folder: folder)
                }
            }
        }
        .navigationTitle("Workout")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("New template", systemImage: "doc.badge.plus") { editing = TemplateEditorTarget(templateId: nil) }
                    Button("New folder", systemImage: "folder.badge.plus") {
                        folderName = ""
                        newFolder = true
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(item: $editing) { target in
            TemplateEditorView(templateId: target.templateId)
        }
        .alert("New folder", isPresented: $newFolder) {
            TextField("Folder name", text: $folderName)
            Button("OK") {
                let name = folderName.trimmingCharacters(in: .whitespaces)
                if !name.isEmpty { TemplateService.createFolder(named: name, in: context) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func row(_ template: WorkoutTemplate) -> some View {
        TemplateRow(
            template: template,
            folders: folders,
            summary: template.liveExercises.map { namesById[$0.exerciseId]?.displayName ?? $0.exerciseId }.joined(separator: ", "),
            onEdit: { editing = TemplateEditorTarget(templateId: template.id) }
        )
    }
}

private struct TemplateRow: View {
    @Environment(\.modelContext) private var context
    let template: WorkoutTemplate
    let folders: [TemplateFolder]
    let summary: String
    let onEdit: () -> Void

    @State private var confirmStart = false
    @State private var confirmDelete = false

    var body: some View {
        Button { confirmStart = true } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: template.name).font(.headline)
                Text(verbatim: summary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                if let lastUsed = template.lastUsedAt {
                    Text("Last used \(lastUsed.formatted(date: .abbreviated, time: .omitted))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Never used").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Edit", systemImage: "pencil", action: onEdit)
            Button("Duplicate", systemImage: "plus.square.on.square") {
                TemplateService.duplicate(template, copySuffix: String(localized: "(copy)"), in: context)
            }
            Menu("Move to folder") {
                Button("No folder") { TemplateService.move(template, to: nil, in: context) }
                ForEach(folders) { folder in
                    Button(folder.name) { TemplateService.move(template, to: folder.id, in: context) }
                }
            }
            Button("Delete", systemImage: "trash", role: .destructive) { confirmDelete = true }
        }
        .confirmationDialog(template.name, isPresented: $confirmStart, titleVisibility: .visible) {
            Button("Start workout") {
                RestTimer.requestAuthorization()
                TemplateService.startWorkout(from: template, in: context)
            }
            Button("Edit", action: onEdit)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(verbatim: summary)
        }
        .alert("Delete template?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) { TemplateService.delete(template, in: context) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(verbatim: template.name)
        }
    }
}

private struct FolderHeader: View {
    @Environment(\.modelContext) private var context
    let folder: TemplateFolder
    @State private var renaming = false
    @State private var name = ""

    var body: some View {
        HStack {
            Text(verbatim: folder.name)
            Spacer()
            Menu {
                Button("Rename folder") {
                    name = folder.name
                    renaming = true
                }
                Button("Delete folder", role: .destructive) { TemplateService.delete(folder, in: context) }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
        .alert("Rename folder", isPresented: $renaming) {
            TextField("Folder name", text: $name)
            Button("OK") {
                let trimmed = name.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty { TemplateService.rename(folder, to: trimmed, in: context) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
