package com.gymbuddy.app.data.coach

import android.content.Context
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.workout.WorkoutDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Monday to Sunday in local time; [toMillis] is the next Monday's start. */
data class ReportWeek(val monday: LocalDate, val fromMillis: Long, val toMillis: Long)

data class WeeklyReport(val week: ReportWeek, val text: String)

/** The last full week before the one [nowMillis] is in. */
fun lastFullWeek(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ReportWeek {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monday = thisMonday.minusWeeks(1)
    return ReportWeek(monday, monday.atStartOfDay(zone).toInstant().toEpochMilli(), thisMonday.atStartOfDay(zone).toInstant().toEpochMilli())
}

/** Buddy AI: builds the training summary and asks the coach function. */
@Singleton
class BuddyAi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: CoachApi,
    private val workouts: WorkoutDao,
    private val exercises: ExerciseDao,
    private val db: GymBuddyDatabase,
    private val pro: ProRepository,
    private val prefs: BuddyPrefs,
    private val settings: SettingsRepository,
) {
    val isAvailable: Boolean get() = api.isAvailable

    private val vietnamese: Boolean get() = context.resources.configuration.locales[0].language == "vi"

    private val locale: String get() = if (vietnamese) "vi" else "en"

    val plan: CoachPlan
        get() = when {
            !pro.isPro.value -> CoachPlan.FREE
            pro.isLifetimeOnly.value -> CoachPlan.LIFETIME
            else -> CoachPlan.PRO
        }

    /** The summary Buddy reads, plus whatever the task adds. */
    suspend fun context(extra: JsonObjectBuilder.(CoachContext) -> Unit = {}): JsonObject {
        val history = workouts.observeHistory().first()
        val buddy = prefs.settings.value
        val now = System.currentTimeMillis()
        val snapshot = CoachRepository.snapshot(history, buddy.daysPerWeek, now)
        val builder = CoachContext(history, snapshot, settings.settings.value.weightUnit, vietnamese)
        val bodyWeights = db.measurementDao().observeAll().first()
        return builder.build(buddy.profileJson(), bodyWeights, now) { this.extra(builder) }
    }

    fun chat(message: String, context: JsonObject): Flow<ChatEvent> =
        api.chat(plan, prefs.settings.value, locale, context, message)

    suspend fun history(): List<CoachMessage> = api.history()

    suspend fun clearHistory() = api.clearHistory()

    /** Buddy's note on a finished workout; cached on the server per workout. */
    suspend fun workoutNote(workoutId: String): String {
        val workout = workouts.getWorkoutWithExercises(workoutId) ?: throw CoachException(CoachError.SERVER)
        val ctx = context { put("workout", it.workout(workout)) }
        val result = api.report("summary", plan, prefs.settings.value, locale, ctx, ref = workoutId)
        return result["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /** Buddy's report on the last full week (Monday to Sunday); cached on the server per week. */
    suspend fun weeklyReport(): WeeklyReport {
        val week = lastFullWeek(System.currentTimeMillis())
        val ctx = context { put("week", it.week(week.fromMillis, week.toMillis)) }
        val result = api.report("weekly", plan, prefs.settings.value, locale, ctx, ref = week.monday.toString())
        return WeeklyReport(week, result["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    /** All exercises Buddy may use when designing a routine. */
    suspend fun library() = exerciseLibrary(exercises.getAllLive(), vietnamese)
}
