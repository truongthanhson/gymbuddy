package com.gymbuddy.app.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateRepository
import com.gymbuddy.app.data.template.TemplateWithExercises
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val folders: List<FolderEntity> = emptyList(),
    val templates: List<TemplateWithExercises> = emptyList(),
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repository: TemplateRepository) : ViewModel() {

    val state: StateFlow<TemplatesUiState> =
        combine(repository.observeFolders(), repository.observeTemplates()) { folders, templates ->
            TemplatesUiState(folders, templates)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    /** [onResult] gets false when another workout is already in progress. */
    fun startWorkout(templateId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.startWorkout(templateId) != null) }
    }

    fun duplicate(templateId: String, copySuffix: String) = launch { repository.duplicateTemplate(templateId, copySuffix) }
    fun delete(templateId: String) = launch { repository.deleteTemplate(templateId) }
    fun move(templateId: String, folderId: String?) = launch { repository.moveTemplate(templateId, folderId) }
    fun createFolder(name: String) = launch { repository.createFolder(name) }
    fun renameFolder(folderId: String, name: String) = launch { repository.renameFolder(folderId, name) }
    fun deleteFolder(folderId: String) = launch { repository.deleteFolder(folderId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
