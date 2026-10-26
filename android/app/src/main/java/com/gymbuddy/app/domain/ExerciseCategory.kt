package com.gymbuddy.app.domain

import androidx.annotation.StringRes
import com.gymbuddy.app.R

/** How an exercise is logged; keys match shared/exercises.json. */
enum class ExerciseCategory(val key: String, @StringRes val label: Int) {
    BARBELL("barbell", R.string.category_barbell),
    DUMBBELL("dumbbell", R.string.category_dumbbell),
    MACHINE("machine", R.string.category_machine),
    CABLE("cable", R.string.category_cable),
    WEIGHTED_BODYWEIGHT("weighted_bodyweight", R.string.category_weighted_bodyweight),
    ASSISTED_BODYWEIGHT("assisted_bodyweight", R.string.category_assisted_bodyweight),
    REPS_ONLY("reps_only", R.string.category_reps_only),
    CARDIO("cardio", R.string.category_cardio),
    DURATION("duration", R.string.category_duration);

    companion object {
        fun fromKey(key: String): ExerciseCategory = entries.firstOrNull { it.key == key } ?: BARBELL
    }
}
