import Foundation

/// The Supabase project, from shared/supabase.json (bundled as a resource).
/// The anon key is public by design; row level security protects the data.
/// With an empty file the app works offline only.
struct SupabaseConfig: Sendable {
    let url: String
    let anonKey: String

    var isConfigured: Bool { !url.isEmpty && !anonKey.isEmpty }

    static let current: SupabaseConfig = {
        struct File: Decodable { var url = ""; var anonKey = "" }
        guard let fileURL = Bundle.main.url(forResource: "supabase", withExtension: "json"),
              let data = try? Data(contentsOf: fileURL),
              let file = try? JSONDecoder().decode(File.self, from: data)
        else { return SupabaseConfig(url: "", anonKey: "") }
        var url = file.url
        while url.hasSuffix("/") { url.removeLast() }
        return SupabaseConfig(url: url, anonKey: file.anonKey)
    }()
}

/// A failed request; `status` is the HTTP status, or 0 when the server could not be reached.
struct SupabaseError: LocalizedError {
    let message: String
    var status = 0

    var errorDescription: String? { message }
}

/// Minimal JSON-over-HTTPS client for the Supabase REST (PostgREST) and Auth (GoTrue) endpoints.
enum SupabaseHTTP {
    static func request(
        _ method: String,
        _ path: String,
        body: Data? = nil,
        accessToken: String? = nil,
        headers: [String: String] = [:]
    ) async throws -> Data {
        let config = SupabaseConfig.current
        guard let url = URL(string: config.url + path) else { throw SupabaseError(message: "Bad URL") }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = method
        request.setValue(config.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken ?? config.anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw SupabaseError(message: error.localizedDescription)
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw SupabaseError(message: errorMessage(data) ?? "HTTP \(status)", status: status)
        }
        return data
    }

    /// GoTrue and PostgREST use different error shapes.
    private static func errorMessage(_ data: Data) -> String? {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return ["error_description", "msg", "message", "error"].lazy.compactMap { object[$0] as? String }.first
    }
}
