package com.gymbuddy.app.data.coach

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How Buddy talks: the person picks one in Buddy's settings. */
enum class BuddyTone(val key: String) { GENTLE("gentle"), BALANCED("balanced"), STRICT("strict") }

enum class TrainingGoal(val key: String) { GENERAL("general"), STRENGTH("strength"), MUSCLE("muscle"), FAT_LOSS("fat_loss") }

enum class Experience(val key: String) { BEGINNER("beginner"), INTERMEDIATE("intermediate"), ADVANCED("advanced") }

data class BuddySettings(
    /** The person agreed to send their training summary to Buddy AI. */
    val aiConsent: Boolean = false,
    val tone: BuddyTone = BuddyTone.BALANCED,
    val goal: TrainingGoal = TrainingGoal.GENERAL,
    val daysPerWeek: Int = DEFAULT_WORKOUTS_PER_WEEK,
    val experience: Experience = Experience.INTERMEDIATE,
    /** Free text: injuries or movements to avoid. */
    val limitations: String = "",
) {
    /** The goal profile as Buddy AI reads it. */
    fun profileJson(): JsonObject = buildJsonObject {
        put("goal", goal.key)
        put("daysPerWeek", daysPerWeek)
        put("experience", experience.key)
        if (limitations.isNotBlank()) put("injuriesOrLimitations", limitations.trim())
    }
}

/** Buddy's own settings, kept apart from the app settings. */
@Singleton
class BuddyPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<BuddySettings> = state.asStateFlow()

    fun update(change: (BuddySettings) -> BuddySettings) {
        val next = change(state.value)
        prefs.edit()
            .putBoolean(KEY_CONSENT, next.aiConsent)
            .putString(KEY_TONE, next.tone.key)
            .putString(KEY_GOAL, next.goal.key)
            .putInt(KEY_DAYS, next.daysPerWeek)
            .putString(KEY_EXPERIENCE, next.experience.key)
            .putString(KEY_LIMITATIONS, next.limitations)
            .apply()
        state.value = next
    }

    private fun read() = BuddySettings(
        aiConsent = prefs.getBoolean(KEY_CONSENT, false),
        tone = BuddyTone.entries.firstOrNull { it.key == prefs.getString(KEY_TONE, null) } ?: BuddyTone.BALANCED,
        goal = TrainingGoal.entries.firstOrNull { it.key == prefs.getString(KEY_GOAL, null) } ?: TrainingGoal.GENERAL,
        daysPerWeek = prefs.getInt(KEY_DAYS, DEFAULT_WORKOUTS_PER_WEEK).coerceIn(1, 7),
        experience = Experience.entries.firstOrNull { it.key == prefs.getString(KEY_EXPERIENCE, null) } ?: Experience.INTERMEDIATE,
        limitations = prefs.getString(KEY_LIMITATIONS, "").orEmpty(),
    )

    private companion object {
        const val KEY_CONSENT = "ai_consent"
        const val KEY_TONE = "tone"
        const val KEY_GOAL = "goal"
        const val KEY_DAYS = "days_per_week"
        const val KEY_EXPERIENCE = "experience"
        const val KEY_LIMITATIONS = "limitations"
    }
}
