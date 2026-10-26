package com.gymbuddy.app.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** "Continue with Google": Credential Manager gets a Google ID token, which Supabase exchanges for a session. */
@Singleton
class GoogleSignIn @Inject constructor(
    private val config: SupabaseConfig,
    private val auth: AuthRepository,
) {
    val isAvailable: Boolean get() = config.isConfigured && config.googleWebClientId.isNotBlank()

    /** Shows Google's account picker from [activityContext]; false when the user closed it. */
    suspend fun signIn(activityContext: Context): Boolean {
        // Google signs the hash of the nonce into the token; Supabase checks it against the raw nonce.
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(config.googleWebClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()
        val result = try {
            CredentialManager.create(activityContext)
                .getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (e: GetCredentialCancellationException) {
            return false
        } catch (e: GetCredentialException) {
            throw SupabaseException(e.message ?: "Google sign-in failed")
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SupabaseException("Google sign-in failed")
        }
        val idToken = runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }
            .getOrElse { throw SupabaseException("Google sign-in failed") }
        auth.signInWithIdToken("google", idToken, rawNonce)
        return true
    }
}
