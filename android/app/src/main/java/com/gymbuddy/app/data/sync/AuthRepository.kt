package com.gymbuddy.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class Session(
    val userId: String,
    val email: String?,
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds. */
    val expiresAt: Long,
)

/** Result of sign up: either signed in, or the project wants the email address confirmed first. */
enum class SignUpResult { SIGNED_IN, CONFIRM_EMAIL }

@Serializable
private data class Credentials(val email: String, val password: String)

@Serializable
private data class IdTokenRequest(
    val provider: String,
    @SerialName("id_token") val idToken: String,
    val nonce: String? = null,
)

@Serializable
private data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class AuthUser(val id: String, val email: String? = null)

@Serializable
private data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthUser? = null,
)

/** Email + password accounts through Supabase Auth. The session is kept in app-private preferences. */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val http: SupabaseHttp,
) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLock = Mutex()
    private val state = MutableStateFlow(readSession())

    val session: StateFlow<Session?> = state.asStateFlow()

    suspend fun signIn(email: String, password: String) {
        val response = post("/auth/v1/token?grant_type=password", json.encodeToString(Credentials(email.trim(), password)))
        store(response) ?: throw SupabaseException("No session returned")
    }

    suspend fun signUp(email: String, password: String): SignUpResult {
        val response = post("/auth/v1/signup", json.encodeToString(Credentials(email.trim(), password)))
        return if (store(response) != null) SignUpResult.SIGNED_IN else SignUpResult.CONFIRM_EMAIL
    }

    /**
     * Signs in with an ID token from Google or Apple. [nonce] is the raw nonce whose SHA-256 was given to the provider.
     * Supabase creates the account on first sign-in.
     */
    suspend fun signInWithIdToken(provider: String, idToken: String, nonce: String?) {
        val response = post("/auth/v1/token?grant_type=id_token", json.encodeToString(IdTokenRequest(provider, idToken, nonce)))
        store(response) ?: throw SupabaseException("No session returned")
    }

    /** A valid access token, refreshed when it is about to expire; null when signed out or the session is gone. */
    suspend fun accessToken(): String? = refreshLock.withLock {
        val current = state.value ?: return null
        if (current.expiresAt - 60 > nowSeconds()) return current.accessToken
        try {
            val response = post("/auth/v1/token?grant_type=refresh_token", json.encodeToString(RefreshRequest(current.refreshToken)))
            store(response)?.accessToken
        } catch (e: SupabaseException) {
            // 4xx: the refresh token was revoked or expired, so the user has to sign in again.
            if (e.status in 400..499) clear()
            throw e
        }
    }

    /** Forgets the session on this device; the caller decides what happens to local data. */
    fun clear() {
        prefs.edit().clear().apply()
        state.value = null
    }

    private suspend fun post(path: String, body: String): AuthResponse =
        json.decodeFromString(http.request("POST", path, body))

    private fun store(response: AuthResponse): Session? {
        val access = response.accessToken ?: return null
        val refresh = response.refreshToken ?: return null
        val user = response.user ?: return null
        val session = Session(user.id, user.email, access, refresh, nowSeconds() + (response.expiresIn ?: 3600))
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAt)
            .apply()
        state.value = session
        return session
    }

    private fun readSession(): Session? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Session(userId, prefs.getString(KEY_EMAIL, null), access, refresh, prefs.getLong(KEY_EXPIRES_AT, 0))
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
