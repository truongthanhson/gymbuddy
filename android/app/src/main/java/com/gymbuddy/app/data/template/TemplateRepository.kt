package com.gymbuddy.app.data.template

import androidx.room.withTransaction
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** A template being edited; saved in one go so the editor can be cancelled. */
data class TemplateDraft(
    val id: String? = null,
    val name: String = "",
    val note: String? = null,
    val folderId: String? = null,
    val exercises: List<DraftExercise> = emptyList(),
)

data class DraftExercise(
    val id: String = newId(),
    val exerciseId: String,
    val restSeconds: Int? = null,
    val sets: List<DraftSet> = listOf(DraftSet()),
)

data class DraftSet(
    val id: String = newId(),
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
)

private fun newId() = UUID.randomUUID().toString()

/** Routines (templates) and their folders. Every write bumps updatedAt and deletes are soft, for sync. */
@Singleton
class TemplateRepository @Inject constructor(private val db: GymBuddyDatabase) {
    private val dao = db.templateDao()
    private val workoutDao = db.workoutDao()

    fun observeTemplates(): Flow<List<TemplateWithExercises>> = dao.observeTemplates()
    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun loadDraft(templateId: String): TemplateDraft? {
        val t = dao.getTemplateWithExercises(templateId) ?: return null
        return TemplateDraft(
            id = t.template.id,
            name = t.template.name,
            note = t.template.note,
            folderId = t.template.folderId,
            exercises = t.exercises.map { e ->
                DraftExercise(
                    id = e.templateExercise.id,
                    exerciseId = e.templateExercise.exerciseId,
                    restSeconds = e.templateExercise.restSeconds,
                    sets = e.sets.map { DraftSet(it.id, it.targetWeightKg, it.targetReps) },
                )
            },
        )
    }

    /** Writes the draft; rows the draft no longer has are soft-deleted. Returns the template id. */
    suspend fun saveDraft(draft: TemplateDraft): String = db.withTransaction {
        val now = clock()
        val existing = draft.id?.let { dao.getTemplateWithExercises(it) }
        val templateId = draft.id ?: newId()
        dao.upsertTemplate(
            (existing?.template ?: TemplateEntity(
                id = templateId,
                name = draft.name,
                sortOrder = dao.maxTemplateOrder() + 1,
                createdAt = now,
                updatedAt = now,
            )).copy(name = draft.name, note = draft.note, folderId = draft.folderId, updatedAt = now),
        )

        val keptExerciseIds = draft.exercises.map { it.id }.toSet()
        val keptSetIds = draft.exercises.flatMap { e -> e.sets.map { it.id } }.toSet()
        val oldExercises = existing?.exercises.orEmpty()
        val createdAtById = oldExercises.associate { it.templateExercise.id to it.templateExercise.createdAt }
        val setCreatedAtById = oldExercises.flatMap { it.sets }.associate { it.id to it.createdAt }

        dao.upsertTemplateExercises(
            oldExercises.filter { it.templateExercise.id !in keptExerciseIds }
                .map { it.templateExercise.copy(deletedAt = now, updatedAt = now) } +
                draft.exercises.mapIndexed { index, e ->
                    TemplateExerciseEntity(
                        id = e.id,
                        templateId = templateId,
                        exerciseId = e.exerciseId,
                        sortOrder = index,
                        restSeconds = e.restSeconds,
                        createdAt = createdAtById[e.id] ?: now,
                        updatedAt = now,
                    )
                },
        )
        dao.upsertTemplateSets(
            oldExercises.flatMap { it.sets }.filter { it.id !in keptSetIds }
                .map { it.copy(deletedAt = now, updatedAt = now) } +
                draft.exercises.flatMap { e ->
                    e.sets.mapIndexed { index, s ->
                        TemplateSetEntity(
                            id = s.id,
                            templateExerciseId = e.id,
                            sortOrder = index,
                            targetWeightKg = s.targetWeightKg,
                            targetReps = s.targetReps,
                            createdAt = setCreatedAtById[s.id] ?: now,
                            updatedAt = now,
                        )
                    }
                },
        )
        templateId
    }

    suspend fun deleteTemplate(templateId: String) = updateTemplate(templateId) { it.copy(deletedAt = clock()) }

    suspend fun moveTemplate(templateId: String, folderId: String?) =
        updateTemplate(templateId) { it.copy(folderId = folderId) }

    suspend fun duplicateTemplate(templateId: String, copySuffix: String): String? {
        val draft = loadDraft(templateId) ?: return null
        return saveDraft(
            draft.copy(
                id = null,
                name = "${draft.name} $copySuffix",
                exercises = draft.exercises.map { e ->
                    e.copy(id = newId(), sets = e.sets.map { it.copy(id = newId()) })
                },
            ),
        )
    }

    suspend fun createFolder(name: String) = db.withTransaction {
        val now = clock()
        dao.upsertFolder(FolderEntity(newId(), name, dao.maxFolderOrder() + 1, now, now))
    }

    suspend fun renameFolder(folderId: String, name: String) = db.withTransaction {
        val folder = dao.getFolder(folderId) ?: return@withTransaction
        dao.upsertFolder(folder.copy(name = name, updatedAt = clock()))
    }

    /** Deletes the folder; its templates move out of it rather than being deleted. */
    suspend fun deleteFolder(folderId: String) = db.withTransaction {
        val folder = dao.getFolder(folderId) ?: return@withTransaction
        val now = clock()
        dao.upsertTemplates(dao.templatesInFolder(folderId).map { it.copy(folderId = null, updatedAt = now) })
        dao.upsertFolder(folder.copy(deletedAt = now, updatedAt = now))
    }

    /** Starts a workout pre-filled with the template's exercises and target sets. Returns null if one is already running. */
    suspend fun startWorkout(templateId: String): String? = db.withTransaction {
        if (workoutDao.getActive() != null) return@withTransaction null
        val t = dao.getTemplateWithExercises(templateId) ?: return@withTransaction null
        val now = clock()
        val workoutId = newId()
        workoutDao.upsertWorkout(
            WorkoutEntity(
                id = workoutId,
                name = t.template.name,
                templateId = templateId,
                startedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        t.exercises.forEachIndexed { index, e ->
            val workoutExerciseId = newId()
            workoutDao.upsertWorkoutExercise(
                WorkoutExerciseEntity(
                    id = workoutExerciseId,
                    workoutId = workoutId,
                    exerciseId = e.templateExercise.exerciseId,
                    sortOrder = index,
                    restSeconds = e.templateExercise.restSeconds,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val sets: List<TemplateSetEntity?> = e.sets.ifEmpty { listOf(null) }
            workoutDao.upsertSets(
                sets.mapIndexed { setIndex, s ->
                    WorkoutSetEntity(
                        id = newId(),
                        workoutExerciseId = workoutExerciseId,
                        sortOrder = setIndex,
                        setType = s?.setType ?: SetType.NORMAL,
                        weightKg = s?.targetWeightKg,
                        reps = s?.targetReps,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
        dao.upsertTemplate(t.template.copy(lastUsedAt = now, updatedAt = now))
        workoutId
    }

    /** Replaces the template's exercises and targets with what was done in [workoutId] (completed sets only). */
    suspend fun updateFromWorkout(templateId: String, workoutId: String) {
        val draft = loadDraft(templateId) ?: return
        val workout = workoutDao.getWorkoutWithExercises(workoutId) ?: return
        val reuse = draft.exercises.groupBy { it.exerciseId }.mapValues { it.value.toMutableList() }
        saveDraft(
            draft.copy(
                exercises = workout.exercises.mapNotNull { e ->
                    val done = e.sets.filter { it.completed }
                    if (done.isEmpty()) return@mapNotNull null
                    // Keep the id of a matching template exercise so the row is updated instead of replaced.
                    val previous = reuse[e.workoutExercise.exerciseId]?.removeFirstOrNull()
                    DraftExercise(
                        id = previous?.id ?: newId(),
                        exerciseId = e.workoutExercise.exerciseId,
                        restSeconds = e.workoutExercise.restSeconds,
                        sets = done.mapIndexed { i, s ->
                            DraftSet(previous?.sets?.getOrNull(i)?.id ?: newId(), s.weightKg, s.reps)
                        },
                    )
                },
            ),
        )
    }

    /** Saves a finished workout as a new template. */
    suspend fun createFromWorkout(workoutId: String): String? {
        val workout = workoutDao.getWorkoutWithExercises(workoutId) ?: return null
        return saveDraft(
            TemplateDraft(
                name = workout.workout.name,
                exercises = workout.exercises.map { e ->
                    DraftExercise(
                        exerciseId = e.workoutExercise.exerciseId,
                        restSeconds = e.workoutExercise.restSeconds,
                        sets = e.sets.filter { it.completed }.ifEmpty { e.sets }.map { DraftSet(targetWeightKg = it.weightKg, targetReps = it.reps) },
                    )
                },
            ),
        )
    }

    private suspend fun updateTemplate(templateId: String, transform: (TemplateEntity) -> TemplateEntity) =
        db.withTransaction {
            val t = dao.getTemplate(templateId) ?: return@withTransaction
            dao.upsertTemplate(transform(t).copy(updatedAt = clock()))
        }

    private fun clock() = System.currentTimeMillis()
}
