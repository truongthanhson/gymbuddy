package com.gymbuddy.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ConfigFile(val url: String = "", val anonKey: String = "", val googleWebClientId: String = "")

/**
 * The Supabase project, from shared/supabase.json (copied into assets).
 * The anon key is public by design; row level security protects the data.
 * With an empty file the app works offline only.
 */
@Singleton
class SupabaseConfig @Inject constructor(@ApplicationContext context: Context) {

    private val file: ConfigFile = runCatching {
        context.assets.open("supabase.json").bufferedReader().use {
            Json { ignoreUnknownKeys = true }.decodeFromString<ConfigFile>(it.readText())
        }
    }.getOrDefault(ConfigFile())

    val url: String = file.url.trimEnd('/')
    val anonKey: String = file.anonKey

    /** The Google Cloud "Web application" OAuth client that Supabase's Google provider uses; empty hides Google sign-in. */
    val googleWebClientId: String = file.googleWebClientId

    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}
