package com.gymbuddy.app.data.coach

import com.gymbuddy.app.data.sync.AuthRepository
import com.gymbuddy.app.data.sync.SupabaseConfig
import com.gymbuddy.app.data.sync.SupabaseException
import com.gymbuddy.app.data.sync.SupabaseHttp
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Why Buddy AI could not answer. */
enum class CoachError { NOT_CONFIGURED, SIGNED_OUT, QUOTA, OFFLINE, REFUSED, SERVER }

class CoachException(val error: CoachError) : Exception(error.name)

/** What the person's plan allows on the server; mirrors LIMITS in supabase/functions/coach. */
enum class CoachPlan(val key: String) { FREE("free"), PRO("pro"), LIFETIME("lifetime") }

@Serializable
data class CoachMessage(val role: String, val content: String)

sealed interface ChatEvent {
    data class Text(val delta: String) : ChatEvent
    data object Done : ChatEvent
}

/** Talks to the `coach` Edge Function; the Claude API key never leaves the server. */
@Singleton
class CoachApi @Inject constructor(
    private val config: SupabaseConfig,
    private val auth: AuthRepository,
    private val http: SupabaseHttp,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isAvailable: Boolean get() = config.isConfigured

    private suspend fun token(): String {
        if (!config.isConfigured) throw CoachException(CoachError.NOT_CONFIGURED)
        return auth.accessToken() ?: throw CoachException(CoachError.SIGNED_OUT)
    }

    private fun body(kind: String, plan: CoachPlan, settings: BuddySettings, locale: String, context: JsonObject, message: String?, ref: String?) =
        buildJsonObject {
            put("kind", kind)
            put("plan", plan.key)
            put("locale", locale)
            put("tone", settings.tone.key)
            put("context", context)
            message?.let { put("message", it) }
            ref?.let { put("ref", it) }
        }.toString()

    private fun errorFor(status: Int, text: String): CoachError {
        // SupabaseHttp hands over the parsed "error" field; the stream path the raw body.
        val code = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: text
        return when {
            status == 401 -> CoachError.SIGNED_OUT
            status == 429 || code == "quota" -> CoachError.QUOTA
            code == "refusal" -> CoachError.REFUSED
            else -> CoachError.SERVER
        }
    }

    /** A workout note, weekly report or routine; the server caches it under [ref]. */
    suspend fun report(
        kind: String,
        plan: CoachPlan,
        settings: BuddySettings,
        locale: String,
        context: JsonObject,
        ref: String?,
        message: String? = null,
    ): JsonObject {
        val token = token()
        val text = try {
            http.request("POST", "/functions/v1/coach", body(kind, plan, settings, locale, context, message, ref), accessToken = token)
        } catch (e: SupabaseException) {
            throw CoachException(if (e.status == 0) CoachError.OFFLINE else errorFor(e.status, e.message.orEmpty()))
        }
        return json.parseToJsonElement(text).jsonObject
    }

    /** Streams Buddy's answer to [message]; the server keeps the conversation. */
    fun chat(plan: CoachPlan, settings: BuddySettings, locale: String, context: JsonObject, message: String): Flow<ChatEvent> = flow {
        val token = token()
        val connection = try {
            URL(config.url + "/functions/v1/coach").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw CoachException(CoachError.OFFLINE)
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("apikey", config.anonKey)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.doOutput = true
            connection.outputStream.use { it.write(body("chat", plan, settings, locale, context, message, null).toByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw CoachException(errorFor(status, text))
            }
            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data: ")) continue
                    val event = json.parseToJsonElement(line.removePrefix("data: ")).jsonObject
                    when (event["type"]?.jsonPrimitive?.contentOrNull) {
                        "text" -> emit(ChatEvent.Text(event["text"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                        "done" -> emit(ChatEvent.Done)
                        "error" -> throw CoachException(
                            if (event["code"]?.jsonPrimitive?.contentOrNull == "refusal") CoachError.REFUSED else CoachError.SERVER,
                        )
                    }
                }
            }
        } catch (e: IOException) {
            throw CoachException(CoachError.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** The conversation so far, oldest first. */
    suspend fun history(): List<CoachMessage> {
        val token = token()
        val text = try {
            http.request("GET", "/rest/v1/coach_messages?select=role,content&order=created_at.desc&limit=100", accessToken = token)
        } catch (e: SupabaseException) {
            throw CoachException(if (e.status == 0) CoachError.OFFLINE else CoachError.SERVER)
        }
        return json.decodeFromString<List<CoachMessage>>(text).reversed()
    }

    suspend fun clearHistory() {
        val token = token()
        try {
            http.request("DELETE", "/rest/v1/coach_messages?id=not.is.null", accessToken = token)
        } catch (e: SupabaseException) {
            throw CoachException(if (e.status == 0) CoachError.OFFLINE else CoachError.SERVER)
        }
    }
}
