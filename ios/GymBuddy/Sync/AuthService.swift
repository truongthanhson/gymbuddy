import Foundation
import Observation
import Security

struct AuthSession: Codable, Equatable, Sendable {
    var userId: String
    var email: String?
    var accessToken: String
    var refreshToken: String
    var expiresAt: Date
}

/// Email + password accounts through Supabase Auth. The session is kept in the keychain.
@MainActor
@Observable
final class AuthService {
    static let shared = AuthService()

    enum SignUpResult { case signedIn, confirmEmail }

    private(set) var session: AuthSession?
    @ObservationIgnored private var refreshTask: Task<String?, Error>?

    private init() {
        session = Keychain.load()
    }

    func signIn(email: String, password: String) async throws {
        let response = try await post("/auth/v1/token?grant_type=password", ["email": trimmed(email), "password": password])
        guard store(response) != nil else { throw SupabaseError(message: "No session returned") }
    }

    func signUp(email: String, password: String) async throws -> SignUpResult {
        let response = try await post("/auth/v1/signup", ["email": trimmed(email), "password": password])
        return store(response) != nil ? .signedIn : .confirmEmail
    }

    /// A valid access token, refreshed when it is about to expire; nil when signed out.
    func accessToken() async throws -> String? {
        guard let current = session else { return nil }
        if current.expiresAt.timeIntervalSinceNow > 60 { return current.accessToken }
        if let refreshTask { return try await refreshTask.value }
        let task = Task<String?, Error> {
            defer { refreshTask = nil }
            do {
                let response = try await post("/auth/v1/token?grant_type=refresh_token", ["refresh_token": current.refreshToken])
                return store(response)?.accessToken
            } catch let error as SupabaseError {
                // 4xx: the refresh token was revoked or expired, so the user has to sign in again.
                if (400..<500).contains(error.status) { clear() }
                throw error
            }
        }
        refreshTask = task
        return try await task.value
    }

    /// Forgets the session on this device; the caller decides what happens to local data.
    func clear() {
        Keychain.delete()
        session = nil
    }

    private func trimmed(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func post(_ path: String, _ body: [String: String]) async throws -> [String: Any] {
        let data = try await SupabaseHTTP.request("POST", path, body: try JSONSerialization.data(withJSONObject: body))
        return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    private func store(_ response: [String: Any]) -> AuthSession? {
        guard let access = response["access_token"] as? String,
              let refresh = response["refresh_token"] as? String,
              let user = response["user"] as? [String: Any],
              let userId = user["id"] as? String
        else { return nil }
        let expiresIn = (response["expires_in"] as? NSNumber)?.doubleValue ?? 3600
        let session = AuthSession(
            userId: userId,
            email: user["email"] as? String,
            accessToken: access,
            refreshToken: refresh,
            expiresAt: Date().addingTimeInterval(expiresIn)
        )
        Keychain.save(session)
        self.session = session
        return session
    }
}

/// One keychain item holding the session as JSON.
private enum Keychain {
    private static var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.gymbuddy.app.auth",
            kSecAttrAccount as String: "session",
        ]
    }

    static func load() -> AuthSession? {
        var item: CFTypeRef?
        var search = query
        search[kSecReturnData as String] = true
        search[kSecMatchLimit as String] = kSecMatchLimitOne
        guard SecItemCopyMatching(search as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(AuthSession.self, from: data)
    }

    static func save(_ session: AuthSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        delete()
        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(item as CFDictionary, nil)
    }

    static func delete() {
        SecItemDelete(query as CFDictionary)
    }
}
