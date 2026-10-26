import SwiftData
import SwiftUI

// Buddy AI's chat, consent screen and settings. Strings live in Buddy.xcstrings.

private func buddy(_ value: String.LocalizationValue) -> String { String(localized: value, table: "Buddy") }

extension CoachError {
    var message: String {
        switch self {
        case .notConfigured: buddy("Buddy AI needs the GymBuddy server, which isn't set up in this build yet. Buddy's tips on the Workout tab work without it.")
        case .signedOut: buddy("Your session expired. Sign in again in Settings.")
        case .quota: buddy("You've used this month's Buddy AI messages.")
        case .offline: buddy("No connection. Buddy's tips still work offline.")
        case .refused: buddy("Buddy can't help with that one. Try asking about your training.")
        case .server: buddy("Buddy couldn't answer just now. Try again in a moment.")
        }
    }
}

/// Anything unexpected (a malformed answer, say) shows as a server error.
func coachError(_ error: Error) -> CoachError { error as? CoachError ?? .server }

private struct ChatLine: Identifiable {
    let id = UUID()
    let fromBuddy: Bool
    var text: String
}

struct BuddyChatView: View {
    @Environment(\.modelContext) private var modelContext
    @AppStorage(BuddyKey.aiConsent) private var consent = false
    @State private var lines: [ChatLine] = []
    @State private var loadingHistory = false
    @State private var historyLoaded = false
    @State private var streaming = false
    @State private var error: CoachError?
    @State private var input = ""
    @State private var showSettings = false

    private var auth: AuthService { .shared }

    var body: some View {
        Group {
            if !BuddyAI.isAvailable {
                message(buddy("Buddy AI needs the GymBuddy server, which isn't set up in this build yet. Buddy's tips on the Workout tab work without it."))
            } else if auth.session == nil {
                List {
                    Section { Text(verbatim: buddy("Sign in to your GymBuddy account to chat with Buddy. Your account keeps the conversation and your monthly allowance.")) }
                    AccountSection()
                }
            } else if !consent {
                consentView
            } else {
                chat
            }
        }
        .navigationTitle(Text(verbatim: buddy("Buddy")))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showSettings = true
                } label: {
                    Label { Text(verbatim: buddy("Buddy settings")) } icon: { Image(systemName: "slider.horizontal.3") }
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            BuddySettingsView(
                onClearHistory: { Task { await clearHistory() } },
                onTurnOff: {
                    consent = false
                    historyLoaded = false
                    lines = []
                    error = nil
                }
            )
        }
        .task(id: consent && auth.session != nil) { await loadHistory() }
    }

    private func message(_ text: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "sparkles").font(.largeTitle).foregroundStyle(.tint)
            Text(verbatim: text).multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var consentView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Label { Text(verbatim: buddy("Before you start")).font(.title2.bold()) } icon: { Image(systemName: "sparkles") }
                Text(verbatim: buddy("To answer, Buddy AI sends a summary of your training to GymBuddy's server and to Anthropic's Claude: your last 8 weeks of workouts, records, body weight and goal profile. Never your email, name, photos or location. Chats are saved to your account so Buddy remembers them; you can delete them at any time."))
                Text(verbatim: buddy("Buddy is not a doctor or physiotherapist. If something hurts, stop and ask a professional."))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    consent = true
                } label: {
                    Text(verbatim: buddy("I agree, turn on Buddy AI")).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(24)
        }
    }

    private var chat: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        if BuddyAI.plan == .free {
                            HStack {
                                Text(verbatim: buddy("Free plan: 5 messages a month. Pro chats without limits.")).font(.footnote)
                                Spacer()
                                NavigationLink { PaywallView() } label: { Text(verbatim: buddy("Get Pro")).font(.footnote.bold()) }
                            }
                            .padding(12)
                            .background(.tint.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                        }
                        if loadingHistory {
                            ProgressView().frame(maxWidth: .infinity)
                        } else if lines.isEmpty {
                            Text(verbatim: buddy("Buddy is not a doctor or physiotherapist. If something hurts, stop and ask a professional."))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            ForEach([buddy("What should I train this week?"), buddy("Why has my progress slowed down?"), buddy("Am I training too much or too little?")], id: \.self) { suggestion in
                                Button { send(suggestion) } label: { Text(verbatim: suggestion) }
                                    .buttonStyle(.bordered)
                            }
                        }
                        ForEach(lines) { line in bubble(line) }
                        if let error {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(verbatim: error.message)
                                if error == .quota, BuddyAI.plan != .pro {
                                    NavigationLink { PaywallView() } label: { Text(verbatim: buddy("Get Pro")) }
                                } else if error != .refused {
                                    Button { Task { self.error = nil; await loadHistory() } } label: { Text(verbatim: buddy("Try again")) }
                                }
                            }
                            .font(.subheadline)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                        }
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .padding()
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: lines.last?.text) { proxy.scrollTo("bottom", anchor: .bottom) }
                .onChange(of: lines.count) { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            Divider()
            HStack(alignment: .bottom) {
                TextField(buddy("Ask about your training…"), text: $input, axis: .vertical)
                    .lineLimit(1...5)
                    .textFieldStyle(.roundedBorder)
                Button {
                    send(input)
                } label: {
                    Label { Text(verbatim: buddy("Send")) } icon: { Image(systemName: "arrow.up.circle.fill") }
                        .labelStyle(.iconOnly)
                        .font(.title2)
                }
                .disabled(streaming || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()
        }
    }

    private func bubble(_ line: ChatLine) -> some View {
        HStack {
            if !line.fromBuddy { Spacer(minLength: 48) }
            Group {
                if line.text.isEmpty {
                    ProgressView()
                } else if line.fromBuddy, let markdown = try? AttributedString(markdown: line.text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
                    Text(markdown)
                } else {
                    Text(verbatim: line.text)
                }
            }
            .textSelection(.enabled)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .foregroundStyle(line.fromBuddy ? Color.primary : Color.white)
            .background(line.fromBuddy ? AnyShapeStyle(Color(.secondarySystemBackground)) : AnyShapeStyle(.tint), in: RoundedRectangle(cornerRadius: 16))
            if line.fromBuddy { Spacer(minLength: 48) }
        }
    }

    private func loadHistory() async {
        guard !historyLoaded, BuddyAI.isAvailable, consent, auth.session != nil else { return }
        historyLoaded = true
        loadingHistory = true
        defer { loadingHistory = false }
        do {
            lines = try await CoachAPI.history().map { ChatLine(fromBuddy: $0.role == "assistant", text: $0.content) }
        } catch {
            historyLoaded = false
            self.error = coachError(error)
        }
    }

    private func clearHistory() async {
        do {
            try await CoachAPI.clearHistory()
            lines = []
            error = nil
        } catch {
            self.error = coachError(error)
        }
    }

    private func send(_ message: String) {
        let text = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !streaming else { return }
        input = ""
        error = nil
        lines.append(ChatLine(fromBuddy: false, text: text))
        lines.append(ChatLine(fromBuddy: true, text: ""))
        streaming = true
        Task {
            defer { streaming = false }
            do {
                try await BuddyAI.chat(text, in: modelContext) { piece in
                    if !lines.isEmpty { lines[lines.count - 1].text += piece }
                }
            } catch {
                // Drop the unanswered question's empty reply; keep a partial one.
                if lines.last?.text.isEmpty == true { lines.removeLast() }
                self.error = coachError(error)
            }
        }
    }
}

/// Tone and goal profile, plus deleting the chat and turning Buddy AI off.
struct BuddySettingsView: View {
    var onClearHistory: () -> Void = {}
    var onTurnOff: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @AppStorage(BuddyKey.aiConsent) private var consent = false
    @AppStorage(BuddyKey.tone) private var tone: BuddyTone = .balanced
    @AppStorage(BuddyKey.goal) private var goal: TrainingGoal = .general
    @AppStorage(BuddyKey.daysPerWeek) private var daysPerWeek = defaultWorkoutsPerWeek
    @AppStorage(BuddyKey.experience) private var experience: Experience = .intermediate
    @AppStorage(BuddyKey.limitations) private var limitations = ""
    @AppStorage(BuddyKey.reminders) private var reminders = false
    @Environment(\.modelContext) private var modelContext
    @State private var confirmClear = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(selection: $tone) {
                        ForEach(BuddyTone.allCases) { Text(verbatim: $0.label).tag($0) }
                    } label: { Text(verbatim: buddy("Tone")) }
                    Picker(selection: $goal) {
                        ForEach(TrainingGoal.allCases) { Text(verbatim: $0.label).tag($0) }
                    } label: { Text(verbatim: buddy("Goal")) }
                    Stepper(value: $daysPerWeek, in: 1...7) {
                        Text(verbatim: buddy("Workouts per week: \(daysPerWeek)"))
                    }
                    Picker(selection: $experience) {
                        ForEach(Experience.allCases) { Text(verbatim: $0.label).tag($0) }
                    } label: { Text(verbatim: buddy("Experience")) }
                    TextField(buddy("Injuries or movements to avoid"), text: $limitations, axis: .vertical)
                        .lineLimit(1...4)
                }
                Section {
                    Toggle(isOn: Binding(get: { reminders }, set: { on in
                        if !on {
                            reminders = false
                            BuddyReminders.reschedule(in: modelContext)
                            return
                        }
                        Task {
                            reminders = await BuddyReminders.requestAuthorization()
                            BuddyReminders.reschedule(in: modelContext)
                        }
                    })) {
                        Text(verbatim: buddy("Reminders"))
                    }
                } footer: {
                    Text(verbatim: buddy("A nudge when you fall behind your weekly goal, and your weekly report on Monday mornings (Pro)."))
                }
                if consent {
                    Section {
                        Button(role: .destructive) { confirmClear = true } label: { Text(verbatim: buddy("Delete chat history")) }
                        Button(role: .destructive) {
                            onTurnOff()
                            dismiss()
                        } label: { Text(verbatim: buddy("Turn off Buddy AI")) }
                    }
                }
            }
            .onChange(of: daysPerWeek) { BuddyReminders.reschedule(in: modelContext) }
            .navigationTitle(Text(verbatim: buddy("Buddy settings")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button { dismiss() } label: { Text(verbatim: buddy("Done")) }
                }
            }
            .confirmationDialog(Text(verbatim: buddy("Delete chat history")), isPresented: $confirmClear, titleVisibility: .visible) {
                Button(role: .destructive) {
                    onClearHistory()
                } label: { Text(verbatim: buddy("Delete chat history")) }
            }
        }
    }
}

extension BuddyTone {
    var label: String {
        switch self {
        case .gentle: buddy("Gentle")
        case .balanced: buddy("Balanced")
        case .strict: buddy("Strict")
        }
    }
}

extension TrainingGoal {
    var label: String {
        switch self {
        case .general: buddy("Stay fit")
        case .strength: buddy("Get stronger")
        case .muscle: buddy("Build muscle")
        case .fatLoss: buddy("Lose fat")
        }
    }
}

extension Experience {
    var label: String {
        switch self {
        case .beginner: buddy("Beginner")
        case .intermediate: buddy("Intermediate")
        case .advanced: buddy("Advanced")
        }
    }
}

/// Buddy AI's short note on a finished workout, in its summary. Pro only, and only once Buddy AI is on.
struct BuddyAiNoteSection: View {
    let workoutId: UUID
    @Environment(\.modelContext) private var modelContext
    @AppStorage(BuddyKey.aiConsent) private var consent = false
    @State private var note: String?
    @State private var failed: CoachError?

    var body: some View {
        if BuddyAI.isAvailable, consent, BuddyAI.plan != .free, AuthService.shared.session != nil {
            Section {
                if let note {
                    Text(markdownOrPlain(note))
                } else if let failed {
                    Text(verbatim: failed.message).foregroundStyle(.secondary)
                } else {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(verbatim: buddy("Buddy is reading your workout…")).foregroundStyle(.secondary)
                    }
                }
            } header: {
                Label { Text(verbatim: buddy("Buddy's note")) } icon: { Image(systemName: "sparkles") }
            }
            .task(id: workoutId) { await load() }
        }
    }

    private func markdownOrPlain(_ text: String) -> AttributedString {
        (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
    }

    private func load() async {
        guard note == nil else { return }
        let id = workoutId
        guard let workout = try? modelContext.fetch(FetchDescriptor<Workout>(predicate: #Predicate { $0.id == id })).first else {
            failed = .server
            return
        }
        do {
            note = try await BuddyAI.workoutNote(workout, in: modelContext)
        } catch {
            failed = coachError(error)
        }
    }
}
