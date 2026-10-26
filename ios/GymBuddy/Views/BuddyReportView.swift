import SwiftData
import SwiftUI

private func buddy(_ value: String.LocalizationValue) -> String { String(localized: value, table: "Buddy") }

/// Buddy AI's look back at last week (Pro).
struct BuddyReportView: View {
    private enum Phase {
        case locked, needsBuddyAi, loading
        case ready(WeeklyReport)
        case failed(CoachError)
    }

    @Environment(\.modelContext) private var modelContext
    @AppStorage(BuddyKey.aiConsent) private var consent = false
    @State private var phase = Phase.loading

    var body: some View {
        List {
            switch phase {
            case .locked:
                Section {
                    Text(verbatim: buddy("With Pro, Buddy AI writes you a report every week: workouts against your goal, volume by muscle group, records, and one clear focus for next week."))
                    NavigationLink { PaywallView() } label: { Text(verbatim: buddy("Get Pro")) }
                }
            case .needsBuddyAi:
                Section {
                    Text(verbatim: buddy("The weekly report is written by Buddy AI. Turn it on first: open Buddy, sign in and agree to send your training summary."))
                    NavigationLink { BuddyChatView() } label: { Text(verbatim: buddy("Ask Buddy")) }
                }
            case .loading:
                HStack(spacing: 12) {
                    ProgressView()
                    Text(verbatim: buddy("Buddy is looking back at last week…")).foregroundStyle(.secondary)
                }
            case .failed(let error):
                Section {
                    Text(verbatim: error.message)
                    Button { Task { await load() } } label: { Text(verbatim: buddy("Try again")) }
                }
            case .ready(let report):
                Section {
                    Text((try? AttributedString(markdown: report.text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(report.text))
                } header: {
                    let end = report.week.to.addingTimeInterval(-1)
                    Text(verbatim: "\(report.week.from.formatted(date: .abbreviated, time: .omitted)) – \(end.formatted(date: .abbreviated, time: .omitted))")
                }
                Section {
                    NavigationLink { BuddyChatView() } label: { Text(verbatim: buddy("Talk it over with Buddy")) }
                }
            }
        }
        .navigationTitle(Text(verbatim: buddy("Weekly report")))
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        if BuddyAI.plan == .free {
            phase = .locked
            return
        }
        guard BuddyAI.isAvailable, consent, AuthService.shared.session != nil else {
            phase = .needsBuddyAi
            return
        }
        phase = .loading
        do {
            phase = .ready(try await BuddyAI.weeklyReport(in: modelContext))
        } catch {
            phase = .failed(coachError(error))
        }
    }
}
