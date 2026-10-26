package com.gymbuddy.app.data.coach

import android.content.Context
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.health.HealthConnectManager
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.template.DraftExercise
import com.gymbuddy.app.data.template.DraftSet
import com.gymbuddy.app.data.template.TemplateDraft
import com.gymbuddy.app.data.template.TemplateRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
data class RoutineExercise(val exerciseId: String, val sets: Int, val repsLow: Int, val repsHigh: Int, val restSeconds: Int)

@Serializable
data class RoutineDay(val name: String, val exercises: List<RoutineExercise>)

/** A routine Buddy AI designed; the person decides whether to save it as templates. */
@Serializable
data class Routine(val name: String, val days: List<RoutineDay>)

data class RoutineProposal(val routine: Routine, val text: String)

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
    private val templates: TemplateRepository,
    private val health: HealthConnectManager,
) {
    private val routineJson = Json { ignoreUnknownKeys = true }

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
        val snapshot = CoachRepository.snapshot(history, buddy.daysPerWeek, now, buddy.cycleStart)
        val builder = CoachContext(history, snapshot, settings.settings.value.weightUnit, vietnamese)
        val bodyWeights = db.measurementDao().observeAll().first()
        val healthDays = if (buddy.shareHealth) runCatching {
            if (health.hasBuddyReadPermissions()) health.recentSleepAndSteps() else null
        }.getOrNull() else null
        return builder.build(buddy.profileJson(), bodyWeights, now) {
            healthDays?.let { days ->
                putJsonArray("health") {
                    days.forEach { d ->
                        addJsonObject {
                            put("date", d.date.toString())
                            d.sleepHours?.let { put("sleepHours", Math.round(it * 10) / 10.0) }
                            d.steps?.let { put("steps", it) }
                        }
                    }
                }
            }
            this.extra(builder)
        }
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

    /** Asks Buddy AI for a routine fitting [request]; the server checks it only uses exercises from the library. */
    suspend fun designRoutine(request: String): RoutineProposal {
        val library = library()
        val ctx = context { put("library", library) }
        val result = api.report("routine", plan, prefs.settings.value, locale, ctx, ref = null, message = request)
        val routine = result["routine"]?.let { routineJson.decodeFromJsonElement(Routine.serializer(), it) }
            ?: throw CoachException(CoachError.SERVER)
        return RoutineProposal(routine, result["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    /** Saves the routine as a folder with one template per day. Returns the folder id. */
    suspend fun saveRoutine(routine: Routine): String {
        val folderId = templates.createFolder(routine.name)
        for (day in routine.days) {
            templates.saveDraft(
                TemplateDraft(
                    name = day.name,
                    folderId = folderId,
                    exercises = day.exercises.map { e ->
                        DraftExercise(
                            exerciseId = e.exerciseId,
                            restSeconds = e.restSeconds,
                            note = if (e.repsLow == e.repsHigh) "${e.repsLow}" else "${e.repsLow}–${e.repsHigh}",
                            sets = List(e.sets) { DraftSet(targetReps = e.repsLow) },
                        )
                    },
                ),
            )
        }
        return folderId
    }

    /** All exercises Buddy may use when designing a routine. */
    suspend fun library() = exerciseLibrary(exercises.getAllLive(), vietnamese)
}
