package com.gymbuddy.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.domain.BodyPart
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

data class ExercisesUiState(
    val query: String = "",
    val bodyPart: BodyPart? = null,
    val exercises: List<ExerciseEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisesViewModel @Inject constructor(dao: ExerciseDao) : ViewModel() {

    private val query = MutableStateFlow("")
    private val bodyPart = MutableStateFlow<BodyPart?>(null)

    val uiState: StateFlow<ExercisesUiState> =
        combine(query, bodyPart) { q, bp -> q to bp }
            .flatMapLatest { (q, bp) ->
                dao.observe(q.trim(), bp?.key).map { ExercisesUiState(q, bp, it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onBodyPartSelected(value: BodyPart?) {
        bodyPart.value = value
    }
}
