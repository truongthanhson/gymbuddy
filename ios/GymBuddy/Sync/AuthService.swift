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

    /// Signs in with an ID token from Apple or Google. `nonce` is the raw nonce whose SHA-256 was given to the provider.
    /// Supabase creates the account on first sign-in.
    func signInWithIdToken(provider: String, idToken: String, nonce: String?) async throws {
        var body = ["provider": provider, "id_token": idToken]
        if let nonce { body["nonce"] = nonce }
        let response = try await post("/auth/v1/token?grant_type=id_token", body)
        guard store(response) != nil else { throw SupabaseError(message: "No session returned") }
    }

    /// Where Supabase's hosted sign-in page sends the user back; must be listed in the project's redirect URLs.
    static let oauthCallbackScheme = "gymbuddy"

    /// Supabase's hosted sign-in page for `provider`, which redirects to `gymbuddy://auth-callback`.
    func oauthURL(provider: String) -> URL? {
        var components = URLComponents(string: SupabaseConfig.current.url + "/auth/v1/authorize")
        components?.queryItems = [
            URLQueryItem(name: "provider", value: provider),
            URLQueryItem(name: "redirect_to", value: "\(Self.oauthCallbackScheme)://auth-callback"),
        ]
        return components?.url
    }

    /// Finishes the hosted sign-in: the callback carries the tokens in its fragment, and the user comes from /user.
    func completeOAuth(callback: URL) async throws {
        var fields: [String: String] = [:]
        for pair in (callback.fragment ?? "").split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1).map { String($0).removingPercentEncoding ?? String($0) }
            if parts.count == 2 { fields[parts[0]] = parts[1] }
        }
        if let message = fields["error_description"] ?? fields["error"] { throw SupabaseError(message: message) }
        guard let access = fields["access_token"], let refresh = fields["refresh_token"] else {
            throw SupabaseError(message: "No session returned")
        }
        let data = try await SupabaseHTTP.request("GET", "/auth/v1/user", accessToken: access)
        let user = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        var response: [String: Any] = ["access_token": access, "refresh_token": refresh, "user": user]
        if let expiresIn = fields["expires_in"].flatMap(Double.init) { response["expires_in"] = NSNumber(value: expiresIn) }
        guard store(response) != nil else { throw SupabaseError(message: "No session returned") }
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
