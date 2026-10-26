import SwiftUI

/// Account and sync rows of the settings screen.
struct AccountSection: View {
    @State private var signingIn = false
    @State private var confirmSignOut = false
    @State private var signOutError = ""
    @State private var showSignOutError = false

    private var auth: AuthService { .shared }
    private var sync: SyncService { .shared }

    var body: some View {
        Section("Account") {
            if !SupabaseConfig.current.isConfigured {
                Text("Sync is not set up in this build yet. Your data stays on this device.")
                    .foregroundStyle(.secondary)
            } else if let session = auth.session {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: session.email ?? "")
                    status.font(.caption).foregroundStyle(sync.lastError == nil ? Color.secondary : Color.red)
                }
                Button("Sync now") { Task { try? await sync.sync() } }
                    .disabled(sync.isRunning)
                Button("Sign out", role: .destructive) { confirmSignOut = true }
                    .disabled(sync.isRunning)
            } else {
                Button {
                    signingIn = true
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Sign in")
                        Text("Back up your workouts and use them on all your devices")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .sheet(isPresented: $signingIn) { SignInView() }
        .confirmationDialog("Sign out?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await signOut(force: false) } }
        } message: {
            Text("Your workouts stay in your account and are removed from this device.")
        }
        .alert("Could not sync", isPresented: $showSignOutError) {
            Button("Sign out anyway", role: .destructive) { Task { await signOut(force: true) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Changes that are not synced yet will be lost if you sign out now. (\(signOutError))")
        }
    }

    private var status: Text {
        if sync.isRunning { return Text("Syncing…") }
        if let error = sync.lastError { return Text("Sync failed: \(error)") }
        if let last = sync.lastSyncedAt { return Text("Last synced \(last, format: .relative(presentation: .named))") }
        return Text("Not synced yet")
    }

    /// Syncs first so nothing is lost; with `force`, signs out even when that sync fails.
    private func signOut(force: Bool) async {
        if !force {
            do {
                try await sync.sync()
            } catch {
                signOutError = error.localizedDescription
                showSignOutError = true
                return
            }
        }
        auth.clear()
        try? sync.clearLocalData()
    }
}

private struct SignInView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var confirmEmail = false

    private let minPassword = 6

    private var canSubmit: Bool {
        !busy && !email.trimmingCharacters(in: .whitespaces).isEmpty && password.count >= minPassword
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                } footer: {
                    Text("At least \(minPassword) characters")
                }
                if confirmEmail {
                    Text("Check your email and open the confirmation link, then sign in.")
                        .foregroundStyle(Color.accentColor)
                } else if let error {
                    Text(verbatim: error).foregroundStyle(.red)
                }
                Section {
                    Button("Sign in") { submit(signUp: false) }
                        .disabled(!canSubmit)
                    Button("Create account") { submit(signUp: true) }
                        .disabled(!canSubmit)
                }
            }
            .navigationTitle("Sign in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                if busy {
                    ToolbarItem(placement: .confirmationAction) { ProgressView() }
                }
            }
        }
    }

    private func submit(signUp: Bool) {
        busy = true
        error = nil
        confirmEmail = false
        Task {
            defer { busy = false }
            do {
                if signUp, try await AuthService.shared.signUp(email: email, password: password) == .confirmEmail {
                    confirmEmail = true
                    return
                }
                if !signUp { try await AuthService.shared.signIn(email: email, password: password) }
                SyncService.shared.requestSync()
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
