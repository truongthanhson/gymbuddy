package com.gymbuddy.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.domain.formatTimestamp
import com.gymbuddy.app.domain.parseTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SyncStatus(
    val running: Boolean = false,
    /** Epoch millis of the last successful sync. */
    val lastSyncedAt: Long? = null,
    val error: String? = null,
)

/**
 * Two-way sync with Supabase, last write wins on updatedAt (see the sync migration in supabase/migrations).
 * Push: every row changed since the last push, parents before children.
 * Pull: every row the server changed since the last pull (by its own clock), applied when newer than the local row.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val config: SupabaseConfig,
    private val auth: AuthRepository,
    private val http: SupabaseHttp,
    private val db: GymBuddyDatabase,
) {
    private val dao = db.syncDao()
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val state = MutableStateFlow(SyncStatus(lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED, 0).takeIf { it > 0 }))

    val status: StateFlow<SyncStatus> = state.asStateFlow()

    private val tables: List<Table<*, *>> = listOf(
        Table("custom_exercises", CustomExerciseRow.serializer(), dao::changedCustomExercises, { it.toRow() },
            dao::versionsOfCustomExercises, { it.toEntity() }, dao::upsertCustomExercises),
        Table("folders", FolderRow.serializer(), dao::changedFolders, { it.toRow() },
            dao::versionsOfFolders, { it.toEntity() }, dao::upsertFolders),
        Table("templates", TemplateRow.serializer(), dao::changedTemplates, { it.toRow() },
            dao::versionsOfTemplates, { it.toEntity() }, dao::upsertTemplates),
        Table("template_exercises", TemplateExerciseRow.serializer(), dao::changedTemplateExercises, { it.toRow() },
            dao::versionsOfTemplateExercises, { it.toEntity() }, dao::upsertTemplateExercises),
        Table("template_sets", TemplateSetRow.serializer(), dao::changedTemplateSets, { it.toRow() },
            dao::versionsOfTemplateSets, { it.toEntity() }, dao::upsertTemplateSets),
        Table("workouts", WorkoutRow.serializer(), dao::changedWorkouts, { it.toRow() },
            dao::versionsOfWorkouts, { it.toEntity() }, dao::upsertWorkouts),
        Table("workout_exercises", WorkoutExerciseRow.serializer(), dao::changedWorkoutExercises, { it.toRow() },
            dao::versionsOfWorkoutExercises, { it.toEntity() }, dao::upsertWorkoutExercises),
        Table("workout_sets", WorkoutSetRow.serializer(), dao::changedWorkoutSets, { it.toRow() },
            dao::versionsOfWorkoutSets, { it.toEntity() }, dao::upsertWorkoutSets),
    )

    /** Starts a sync in the background when signed in; does nothing if one is already running. */
    fun requestSync() {
        if (!config.isConfigured || auth.session.value == null || lock.isLocked) return
        scope.launch { runCatching { sync() } }
    }

    /** Pushes then pulls. Throws [SupabaseException] when it fails; [status] carries the error too. */
    suspend fun sync() = lock.withLock {
        state.update { it.copy(running = true, error = null) }
        try {
            val token = auth.accessToken() ?: throw SupabaseException("Not signed in")
            push(token)
            pull(token)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNCED, now).apply()
            state.value = SyncStatus(lastSyncedAt = now)
        } catch (e: SupabaseException) {
            state.update { it.copy(running = false, error = e.message) }
            throw e
        }
    }

    /** Removes the account's rows from this device and forgets the sync position (used on sign out). */
    suspend fun clearLocalData() = lock.withLock {
        db.withTransaction {
            dao.deleteWorkoutSets()
            dao.deleteWorkoutExercises()
            dao.deleteWorkouts()
            dao.deleteTemplateSets()
            dao.deleteTemplateExercises()
            dao.deleteTemplates()
            dao.deleteFolders()
            dao.deleteCustomExercises()
        }
        prefs.edit().clear().apply()
        state.value = SyncStatus()
    }

    private suspend fun push(token: String) {
        val startedAt = System.currentTimeMillis()
        val since = prefs.getLong(KEY_PUSHED_UNTIL, 0)
        for (table in tables) table.push(since, token)
        prefs.edit().putLong(KEY_PUSHED_UNTIL, startedAt).apply()
    }

    private suspend fun pull(token: String) {
        for (table in tables) {
            val key = KEY_PULLED_PREFIX + table.name
            // Start a little before the saved position: a transaction that began earlier can commit later.
            var after = prefs.getString(key, null)?.let { formatTimestamp(parseTimestamp(it) - OVERLAP_MILLIS) }
            while (true) {
                val page = table.pull(after, token)
                if (page.isEmpty()) break
                after = page.last()
                prefs.edit().putString(key, after).apply()
                if (page.size < PAGE_SIZE) break
            }
        }
    }

    private inner class Table<E, R : SyncedRow>(
        val name: String,
        private val serializer: KSerializer<R>,
        private val changed: suspend (Long) -> List<E>,
        private val toRow: (E) -> R,
        private val versions: suspend (List<String>) -> List<RowVersion>,
        private val toEntity: (R) -> E,
        private val upsert: suspend (List<E>) -> Unit,
    ) {
        suspend fun push(since: Long, token: String) {
            changed(since).chunked(PAGE_SIZE).forEach { chunk ->
                http.request(
                    "POST",
                    "/rest/v1/$name?on_conflict=id",
                    json.encodeToString(ListSerializer(serializer), chunk.map(toRow)),
                    token,
                    mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
                )
            }
        }

        /** Pulls one page of rows the server changed after [after] and applies the newer ones; returns their synced_at. */
        suspend fun pull(after: String?, token: String): List<String> {
            val filter = after?.let { "&synced_at=gt.${URLEncoder.encode(it, "UTF-8")}" } ?: ""
            val text = http.request(
                "GET",
                "/rest/v1/$name?select=*&order=synced_at.asc&limit=$PAGE_SIZE$filter",
                accessToken = token,
            )
            val objects = json.parseToJsonElement(text) as JsonArray
            if (objects.isEmpty()) return emptyList()
            val rows = objects.map { json.decodeFromJsonElement(serializer, it) }
            val local = versions(rows.map { it.id }).associate { it.id to it.updatedAt }
            val newer = rows.filter { row -> local[row.id]?.let { parseTimestamp(row.updatedAt) > it } ?: true }
            if (newer.isNotEmpty()) db.withTransaction { upsert(newer.map(toEntity)) }
            return objects.map { (it as JsonObject).getValue("synced_at").jsonPrimitive.content }
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val OVERLAP_MILLIS = 60_000L
        const val KEY_PUSHED_UNTIL = "pushed_until"
        const val KEY_PULLED_PREFIX = "pulled_until_"
        const val KEY_LAST_SYNCED = "last_synced"
    }
}
