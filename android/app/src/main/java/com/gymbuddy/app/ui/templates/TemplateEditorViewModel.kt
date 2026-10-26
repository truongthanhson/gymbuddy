package com.gymbuddy.app.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.template.DraftExercise
import com.gymbuddy.app.data.template.DraftSet
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateDraft
import com.gymbuddy.app.data.template.TemplateRepository
import com.gymbuddy.app.ui.TemplateEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TemplateRepository,
    exerciseDao: ExerciseDao,
) : ViewModel() {

    private val templateId = savedStateHandle.toRoute<TemplateEditorRoute>().templateId

    private val _draft = MutableStateFlow<TemplateDraft?>(if (templateId == null) TemplateDraft() else null)
    /** Null while an existing template loads. */
    val draft: StateFlow<TemplateDraft?> = _draft.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercisesById: StateFlow<Map<String, ExerciseEntity>> = exerciseDao.observe("", null)
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (templateId != null) {
            viewModelScope.launch { _draft.value = repository.loadDraft(templateId) ?: TemplateDraft() }
        }
    }

    fun setName(name: String) = edit { it.copy(name = name) }
    fun setFolder(folderId: String?) = edit { it.copy(folderId = folderId) }

    fun addExercises(exerciseIds: List<String>) = edit { d ->
        d.copy(exercises = d.exercises + exerciseIds.map { DraftExercise(exerciseId = it) })
    }

    fun removeExercise(id: String) = edit { d -> d.copy(exercises = d.exercises.filterNot { it.id == id }) }

    fun addSet(exerciseId: String) = editExercise(exerciseId) { e ->
        val last = e.sets.lastOrNull()
        e.copy(sets = e.sets + DraftSet(targetWeightKg = last?.targetWeightKg, targetReps = last?.targetReps))
    }

    fun removeSet(exerciseId: String, setId: String) = editExercise(exerciseId) { e ->
        e.copy(sets = e.sets.filterNot { it.id == setId })
    }

    fun setWeight(exerciseId: String, setId: String, value: Double?) =
        editSet(exerciseId, setId) { it.copy(targetWeightKg = value) }

    fun setReps(exerciseId: String, setId: String, value: Int?) =
        editSet(exerciseId, setId) { it.copy(targetReps = value) }

    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (d.name.isBlank() || d.exercises.isEmpty()) return
        viewModelScope.launch {
            repository.saveDraft(d.copy(name = d.name.trim()))
            onSaved()
        }
    }

    private fun edit(transform: (TemplateDraft) -> TemplateDraft) {
        _draft.update { it?.let(transform) }
    }

    private fun editExercise(id: String, transform: (DraftExercise) -> DraftExercise) = edit { d ->
        d.copy(exercises = d.exercises.map { if (it.id == id) transform(it) else it })
    }

    private fun editSet(exerciseId: String, setId: String, transform: (DraftSet) -> DraftSet) =
        editExercise(exerciseId) { e -> e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) }
}
