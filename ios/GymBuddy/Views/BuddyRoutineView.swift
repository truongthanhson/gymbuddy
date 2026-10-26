import SwiftData
import SwiftUI

private func buddy(_ value: String.LocalizationValue) -> String { String(localized: value, table: "Buddy") }

/// Buddy AI designs a routine from a request in the person's words; it becomes templates once they agree (Pro).
struct BuddyRoutineView: View {
    @Environment(\.modelContext) private var modelContext
    @AppStorage(BuddyKey.aiConsent) private var consent = false
    @Query private var exercises: [Exercise]
    @State private var request = ""
    @State private var designing = false
    @State private var proposal: RoutineProposal?
    @State private var error: CoachError?
    @State private var saved = false

    var body: some View {
        let names = Dictionary(exercises.map { ($0.id, $0.displayName) }, uniquingKeysWith: { first, _ in first })
        List {
            if BuddyAI.plan == .free {
                Section {
                    Text(verbatim: buddy("With Pro, Buddy AI designs a routine around your goal, schedule and history, using exercises from your library. You check it before it becomes templates."))
                    NavigationLink { PaywallView() } label: { Text(verbatim: buddy("Get Pro")) }
                }
            } else if !BuddyAI.isAvailable || !consent || AuthService.shared.session == nil {
                Section {
                    Text(verbatim: buddy("The weekly report is written by Buddy AI. Turn it on first: open Buddy, sign in and agree to send your training summary."))
                    NavigationLink { BuddyChatView() } label: { Text(verbatim: buddy("Ask Buddy")) }
                }
            } else {
                Section {
                    TextField(buddy("For example: 4 days a week, upper/lower, about an hour, no barbell squats"), text: $request, axis: .vertical)
                        .lineLimit(2...5)
                    Button {
                        Task { await design() }
                    } label: {
                        if designing {
                            HStack(spacing: 8) {
                                ProgressView()
                                Text(verbatim: buddy("Buddy is putting your routine together…"))
                            }
                        } else {
                            Text(verbatim: buddy("Design it"))
                        }
                    }
                    .disabled(designing || request.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                } footer: {
                    Text(verbatim: buddy("Tell Buddy what you want. It uses your goal profile, history and recovery, and only exercises from your library."))
                }
                if let error {
                    Section { Text(verbatim: error.message).foregroundStyle(.red) }
                }
                if let proposal {
                    Section {
                        Text(verbatim: proposal.routine.name).font(.headline)
                        if !proposal.text.isEmpty { Text(verbatim: proposal.text).font(.subheadline) }
                    }
                    ForEach(Array(proposal.routine.days.enumerated()), id: \.offset) { _, day in
                        Section(day.name) {
                            ForEach(Array(day.exercises.enumerated()), id: \.offset) { _, e in
                                let reps = e.repsLow == e.repsHigh ? "\(e.repsLow)" : "\(e.repsLow)–\(e.repsHigh)"
                                Text(verbatim: "\(e.sets) × \(reps) · \(names[e.exerciseId] ?? e.exerciseId)")
                            }
                        }
                    }
                    Section {
                        if saved {
                            Text(verbatim: buddy("Saved. You'll find it as a folder of templates on the Workout tab.")).foregroundStyle(.tint)
                        } else {
                            Button {
                                BuddyAI.saveRoutine(proposal.routine, in: modelContext)
                                saved = true
                            } label: { Text(verbatim: buddy("Save as templates")) }
                            Button { Task { await design() } } label: { Text(verbatim: buddy("Try another")) }
                                .disabled(designing)
                        }
                    }
                }
            }
        }
        .navigationTitle(Text(verbatim: buddy("Design a routine")))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func design() async {
        let text = request.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !designing else { return }
        designing = true
        error = nil
        saved = false
        defer { designing = false }
        do {
            proposal = try await BuddyAI.designRoutine(text, in: modelContext)
        } catch {
            self.error = coachError(error)
        }
    }
}
