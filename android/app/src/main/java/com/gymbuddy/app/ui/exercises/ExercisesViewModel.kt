package com.gymbuddy.app.ui.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.exercise.ExerciseRepository
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.ExerciseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExercisesUiState(
    val bodyPart: BodyPart? = null,
    val exercises: List<ExerciseEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisesViewModel @Inject constructor(
    dao: ExerciseDao,
    private val repository: ExerciseRepository,
) : ViewModel() {

    // Compose state, not a flow: the search field must read back what was typed right away, or the IME loses its place.
    var query by mutableStateOf("")
        private set
    private val bodyPart = MutableStateFlow<BodyPart?>(null)

    val uiState: StateFlow<ExercisesUiState> =
        combine(snapshotFlow { query.trim() }, bodyPart) { q, bp -> q to bp }
            .flatMapLatest { (q, bp) ->
                dao.observe(q, bp?.key).map { ExercisesUiState(bp, it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    fun onQueryChange(value: String) {
        query = value
    }

    fun onBodyPartSelected(value: BodyPart?) {
        bodyPart.value = value
    }

    /** [onCreated] gets the new exercise's id, e.g. to select it in the picker. */
    fun createCustom(name: String, bodyPart: BodyPart, category: ExerciseCategory, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(repository.createCustom(name, bodyPart, category)) }
    }
}
