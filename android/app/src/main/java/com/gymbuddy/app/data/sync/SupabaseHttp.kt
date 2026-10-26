package com.gymbuddy.app.data.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A failed request; [status] is the HTTP status, or 0 when the server could not be reached. */
class SupabaseException(message: String, val status: Int = 0) : Exception(message)

/** Minimal JSON-over-HTTPS client for the Supabase REST (PostgREST) and Auth (GoTrue) endpoints. */
@Singleton
class SupabaseHttp @Inject constructor(private val config: SupabaseConfig) {

    /** Sends [body] (JSON text) and returns the response body; throws [SupabaseException] on any failure. */
    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        accessToken: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val connection = try {
            URL(config.url + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw SupabaseException(e.message ?: "Network error")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", config.anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: config.anonKey}")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) throw SupabaseException(errorMessage(text) ?: "HTTP $status", status)
            text
        } catch (e: IOException) {
            throw SupabaseException(e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    /** GoTrue and PostgREST use different error shapes. */
    private fun errorMessage(text: String): String? = runCatching {
        val obj = Json.parseToJsonElement(text) as JsonObject
        listOf("error_description", "msg", "message", "error")
            .firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.contentOrNull }
    }.getOrNull()
}
