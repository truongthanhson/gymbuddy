package com.gymbuddy.app.data.template

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM templates WHERE deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplateWithExercises(id: String): TemplateWithExercises?

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    fun observeTemplate(id: String): Flow<TemplateWithExercises?>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: String): TemplateEntity?

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM templates")
    suspend fun maxTemplateOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folders")
    suspend fun maxFolderOrder(): Int

    @Query("SELECT * FROM templates WHERE folderId = :folderId AND deletedAt IS NULL")
    suspend fun templatesInFolder(folderId: String): List<TemplateEntity>

    @Upsert suspend fun upsertFolder(folder: FolderEntity)
    @Upsert suspend fun upsertTemplate(template: TemplateEntity)
    @Upsert suspend fun upsertTemplates(templates: List<TemplateEntity>)
    @Upsert suspend fun upsertTemplateExercises(items: List<TemplateExerciseEntity>)
    @Upsert suspend fun upsertTemplateSets(sets: List<TemplateSetEntity>)
}
