package com.gymbuddy.app.domain

import androidx.annotation.StringRes
import com.gymbuddy.app.R

enum class BodyPart(val key: String, @StringRes val label: Int) {
    CHEST("chest", R.string.body_part_chest),
    BACK("back", R.string.body_part_back),
    LEGS("legs", R.string.body_part_legs),
    SHOULDERS("shoulders", R.string.body_part_shoulders),
    ARMS("arms", R.string.body_part_arms),
    CORE("core", R.string.body_part_core),
    OLYMPIC("olympic", R.string.body_part_olympic),
    FULL_BODY("full_body", R.string.body_part_full_body),
    CARDIO("cardio", R.string.body_part_cardio),
    OTHER("other", R.string.body_part_other);

    companion object {
        fun fromKey(key: String): BodyPart = entries.firstOrNull { it.key == key } ?: OTHER
    }
}
