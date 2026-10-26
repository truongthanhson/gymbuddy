package com.gymbuddy.app.data.exercise

import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.ExerciseCategory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Custom exercises; built-in ones come from [ExerciseSeeder] and are read-only. */
@Singleton
class ExerciseRepository @Inject constructor(private val dao: ExerciseDao) {

    suspend fun createCustom(name: String, bodyPart: BodyPart, category: ExerciseCategory): String {
        val now = clock()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            ExerciseEntity(
                id = id,
                name = name.trim(),
                nameVi = null,
                category = category.key,
                bodyPart = bodyPart.key,
                primaryMuscle = null,
                secondaryMuscles = emptyList(),
                defaultRestSeconds = null,
                isCustom = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun updateCustom(id: String, name: String, bodyPart: BodyPart, category: ExerciseCategory) =
        updateCustom(id) { it.copy(name = name.trim(), bodyPart = bodyPart.key, category = category.key) }

    /** Soft delete: past workouts still show the exercise. */
    suspend fun deleteCustom(id: String) = updateCustom(id) { it.copy(deletedAt = clock()) }

    private suspend fun updateCustom(id: String, transform: (ExerciseEntity) -> ExerciseEntity) {
        val current = dao.get(id)?.takeIf { it.isCustom } ?: return
        dao.upsert(transform(current).copy(updatedAt = clock()))
    }

    private fun clock() = System.currentTimeMillis()
}
