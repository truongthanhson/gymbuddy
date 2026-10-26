import Foundation

/// Why Buddy AI could not answer.
enum CoachError: Error, Sendable {
    case notConfigured, signedOut, quota, offline, refused, server
}

/// What the person's plan allows on the server; mirrors LIMITS in supabase/functions/coach.
enum CoachPlan: String, Sendable {
    case free, pro, lifetime
}

struct CoachMessage: Decodable, Sendable {
    let role: String
    let content: String
}

/// Talks to the `coach` Edge Function; the Claude API key never leaves the server.
enum CoachAPI {
    static var isAvailable: Bool { SupabaseConfig.current.isConfigured }

    @MainActor
    private static func token() async throws -> String {
        guard isAvailable else { throw CoachError.notConfigured }
        guard let token = try? await AuthService.shared.accessToken() else { throw CoachError.signedOut }
        return token
    }

    private static func body(
        kind: String, plan: CoachPlan, settings: BuddySettings, locale: String,
        context: [String: Any], message: String?, ref: String?
    ) throws -> Data {
        var out: [String: Any] = ["kind": kind, "plan": plan.rawValue, "locale": locale, "tone": settings.tone.rawValue, "context": context]
        if let message { out["message"] = message }
        if let ref { out["ref"] = ref }
        return try JSONSerialization.data(withJSONObject: out)
    }

    private static func errorFor(status: Int, code: String) -> CoachError {
        // SupabaseHTTP hands over the parsed "error" field; the stream path the raw body.
        var code = code
        if let data = code.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let parsed = object["error"] as? String {
            code = parsed
        }
        if status == 401 { return .signedOut }
        if status == 429 || code == "quota" { return .quota }
        if code == "refusal" { return .refused }
        return .server
    }

    private static func mapped(_ error: Error) -> CoachError {
        if let coach = error as? CoachError { return coach }
        if let supabase = error as? SupabaseError {
            return supabase.status == 0 ? .offline : errorFor(status: supabase.status, code: supabase.message)
        }
        return .server
    }

    /// A workout note, weekly report or routine; the server caches it under `ref`.
    @MainActor
    static func report(
        kind: String, plan: CoachPlan, settings: BuddySettings, locale: String,
        context: [String: Any], ref: String?, message: String? = nil
    ) async throws -> [String: Any] {
        let token = try await token()
        do {
            let data = try await SupabaseHTTP.request(
                "POST", "/functions/v1/coach",
                body: body(kind: kind, plan: plan, settings: settings, locale: locale, context: context, message: message, ref: ref),
                accessToken: token
            )
            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw CoachError.server }
            return object
        } catch {
            throw mapped(error)
        }
    }

    /// Streams Buddy's answer to `message`, handing each piece of text to `onText`; the server keeps the conversation.
    @MainActor
    static func chat(
        plan: CoachPlan, settings: BuddySettings, locale: String, context: [String: Any], message: String,
        onText: (String) -> Void
    ) async throws {
        let token = try await token()
        let config = SupabaseConfig.current
        guard let url = URL(string: config.url + "/functions/v1/coach") else { throw CoachError.notConfigured }
        var request = URLRequest(url: url, timeoutInterval: 90)
        request.httpMethod = "POST"
        request.setValue(config.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = try body(kind: "chat", plan: plan, settings: settings, locale: locale, context: context, message: message, ref: nil)

        do {
            let (bytes, response) = try await URLSession.shared.bytes(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard (200..<300).contains(status) else {
                var text = ""
                for try await line in bytes.lines { text += line }
                throw errorFor(status: status, code: text)
            }
            for try await line in bytes.lines {
                guard line.hasPrefix("data: "),
                      let data = line.dropFirst(6).data(using: .utf8),
                      let event = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                else { continue }
                switch event["type"] as? String {
                case "text": onText(event["text"] as? String ?? "")
                case "done": return
                case "error": throw (event["code"] as? String) == "refusal" ? CoachError.refused : CoachError.server
                default: break
                }
            }
        } catch let error as CoachError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw CoachError.offline
        }
    }

    /// The conversation so far, oldest first.
    @MainActor
    static func history() async throws -> [CoachMessage] {
        let token = try await token()
        do {
            let data = try await SupabaseHTTP.request(
                "GET", "/rest/v1/coach_messages?select=role,content&order=created_at.desc&limit=100", accessToken: token
            )
            return try JSONDecoder().decode([CoachMessage].self, from: data).reversed()
        } catch {
            throw mapped(error)
        }
    }

    @MainActor
    static func clearHistory() async throws {
        let token = try await token()
        do {
            _ = try await SupabaseHTTP.request("DELETE", "/rest/v1/coach_messages?id=not.is.null", accessToken: token)
        } catch {
            throw mapped(error)
        }
    }
}
