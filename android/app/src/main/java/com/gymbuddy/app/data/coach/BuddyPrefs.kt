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
    /** Training reminders and the Monday weekly report notification. */
    val reminders: Boolean = false,
    /** Start of the 4-week training cycle (three build weeks, then a deload), or null without one. */
    val cycleStart: Long? = null,
    /** Send the last week's sleep and steps from Health Connect along with the training summary. */
    val shareHealth: Boolean = false,
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
            .putBoolean(KEY_REMINDERS, next.reminders)
            .putLong(KEY_CYCLE_START, next.cycleStart ?: 0L)
            .putBoolean(KEY_SHARE_HEALTH, next.shareHealth)
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
        reminders = prefs.getBoolean(KEY_REMINDERS, false),
        cycleStart = prefs.getLong(KEY_CYCLE_START, 0L).takeIf { it > 0 },
        shareHealth = prefs.getBoolean(KEY_SHARE_HEALTH, false),
    )

    /** When the last training reminder was shown. */
    var lastReminderAt: Long
        get() = prefs.getLong(KEY_LAST_REMINDER, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_REMINDER, value).apply()

    /** The week (its Monday) whose report was last announced. */
    var lastWeeklyNotice: String
        get() = prefs.getString(KEY_LAST_WEEKLY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_WEEKLY, value).apply()

    private companion object {
        const val KEY_CONSENT = "ai_consent"
        const val KEY_TONE = "tone"
        const val KEY_GOAL = "goal"
        const val KEY_DAYS = "days_per_week"
        const val KEY_EXPERIENCE = "experience"
        const val KEY_LIMITATIONS = "limitations"
        const val KEY_REMINDERS = "reminders"
        const val KEY_CYCLE_START = "cycle_start"
        const val KEY_SHARE_HEALTH = "share_health"
        const val KEY_LAST_REMINDER = "last_reminder_at"
        const val KEY_LAST_WEEKLY = "last_weekly_notice"
    }
}
