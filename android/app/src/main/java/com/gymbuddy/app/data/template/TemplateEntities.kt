package com.gymbuddy.app.data.template

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.workout.SetType

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "templates", indices = [Index("folderId")])
data class TemplateEntity(
    @PrimaryKey val id: String,
    val folderId: String? = null,
    val name: String,
    val note: String? = null,
    val sortOrder: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val restSeconds: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "template_sets",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateExerciseId")],
)
data class TemplateSetEntity(
    @PrimaryKey val id: String,
    val templateExerciseId: String,
    val sortOrder: Int,
    val setType: SetType = SetType.NORMAL,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val targetDurationSec: Int? = null,
    val targetDistanceM: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class TemplateExerciseWithSets(
    @Embedded val templateExercise: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?,
    @Relation(parentColumn = "id", entityColumn = "templateExerciseId")
    val allSets: List<TemplateSetEntity>,
) {
    val sets: List<TemplateSetEntity>
        get() = allSets.filter { it.deletedAt == null }.sortedBy { it.sortOrder }
}

data class TemplateWithExercises(
    @Embedded val template: TemplateEntity,
    @Relation(entity = TemplateExerciseEntity::class, parentColumn = "id", entityColumn = "templateId")
    val allExercises: List<TemplateExerciseWithSets>,
) {
    val exercises: List<TemplateExerciseWithSets>
        get() = allExercises.filter { it.templateExercise.deletedAt == null }
            .sortedBy { it.templateExercise.sortOrder }
}
