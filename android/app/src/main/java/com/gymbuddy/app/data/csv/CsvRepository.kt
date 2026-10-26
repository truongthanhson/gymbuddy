package com.gymbuddy.app.data.csv

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.domain.CsvExercise
import com.gymbuddy.app.domain.CsvSet
import com.gymbuddy.app.domain.CsvWorkout
import com.gymbuddy.app.domain.ExerciseCategory
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.categoryFromName
import com.gymbuddy.app.domain.exportCsv
import com.gymbuddy.app.domain.guessBodyPart
import com.gymbuddy.app.domain.importCsv
import com.gymbuddy.app.domain.libraryName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [imported] workouts were added; [skipped] were already on this device (same start time and name). */
data class ImportResult(val imported: Int, val skipped: Int)

class CsvFormatException : Exception("Not a workout CSV export")

/** Exports finished workouts to, and imports them from, a CSV file in Strong's layout. */
@Singleton
class CsvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GymBuddyDatabase,
) {
    private val workoutDao = db.workoutDao()
    private val exerciseDao = db.exerciseDao()

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Writes every finished workout's completed sets to [uri]; returns the number of workouts. */
    suspend fun export(uri: Uri, unit: WeightUnit): Int = withContext(Dispatchers.IO) {
        val format = dateFormat()
        val workouts = workoutDao.getFinished().map { item ->
            val workout = item.workout
            CsvWorkout(
                date = format.format(Date(workout.startedAt)),
                name = workout.name,
                durationSec = workout.endedAt?.let { ((it - workout.startedAt) / 1000).toInt() },
                note = workout.note,
                exercises = item.exercises.mapNotNull { entry ->
                    val sets = entry.sets.filter { it.completed }.map { set ->
                        CsvSet(set.setType.name.lowercase(), set.weightKg, set.reps, set.distanceM, set.durationSec, set.rpe)
                    }
                    if (sets.isEmpty()) null else CsvExercise(entry.exercise?.name ?: entry.workoutExercise.exerciseId, entry.workoutExercise.note, sets)
                },
            )
        }
        val text = exportCsv(workouts, unit)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Cannot write to $uri")
        workouts.size
    }

    /** Reads workouts from [uri]; exercises are matched by name, and missing ones are created as custom exercises. */
    suspend fun import(uri: Uri, defaultUnit: WeightUnit): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Cannot read $uri")
        val workouts = importCsv(text, defaultUnit) ?: throw CsvFormatException()
        val format = dateFormat()
        var imported = 0
        var skipped = 0
        db.withTransaction {
            val live = exerciseDao.getAllLive()
            val byName = live
                // associateBy keeps the last of equal names, so built-in exercises go last to win over custom ones.
                .sortedByDescending { it.isCustom }
                .associateBy { it.name.lowercase() }
                .toMutableMap()
            val library = live.filterNot { it.isCustom }.map { it.name to it.bodyPart }
            for (workout in workouts) {
                val startedAt = runCatching { format.parse(workout.date)?.time }.getOrNull() ?: continue
                if (workoutDao.countMatching(startedAt, workout.name) > 0) {
                    skipped++
                    continue
                }
                insert(workout, startedAt, byName, library)
                imported++
            }
        }
        ImportResult(imported, skipped)
    }

    private suspend fun insert(
        workout: CsvWorkout,
        startedAt: Long,
        byName: MutableMap<String, ExerciseEntity>,
        library: List<Pair<String, String>>,
    ) {
        val now = System.currentTimeMillis()
        val endedAt = startedAt + (workout.durationSec ?: 0) * 1000L
        val workoutId = UUID.randomUUID().toString()
        workoutDao.upsertWorkout(
            WorkoutEntity(
                id = workoutId, name = workout.name, note = workout.note,
                startedAt = startedAt, endedAt = endedAt, createdAt = now, updatedAt = now,
            ),
        )
        workout.exercises.forEachIndexed { index, exercise ->
            val exerciseId = (byName[exercise.name.lowercase()] ?: byName[libraryName(exercise.name).lowercase()])?.id
                ?: createExercise(exercise, library, now).also { byName[exercise.name.lowercase()] = it }.id
            val workoutExerciseId = UUID.randomUUID().toString()
            workoutDao.upsertWorkoutExercise(
                WorkoutExerciseEntity(
                    id = workoutExerciseId, workoutId = workoutId, exerciseId = exerciseId, sortOrder = index,
                    note = exercise.note, createdAt = now, updatedAt = now,
                ),
            )
            workoutDao.upsertSets(
                exercise.sets.mapIndexed { order, set ->
                    WorkoutSetEntity(
                        id = UUID.randomUUID().toString(), workoutExerciseId = workoutExerciseId, sortOrder = order,
                        setType = SetType.entries.firstOrNull { it.name.equals(set.setType, ignoreCase = true) } ?: SetType.NORMAL,
                        weightKg = set.weightKg, reps = set.reps, durationSec = set.seconds, distanceM = set.distanceM,
                        rpe = set.rpe, completed = true, completedAt = endedAt, createdAt = now, updatedAt = now,
                    )
                },
            )
        }
    }

    private suspend fun createExercise(exercise: CsvExercise, library: List<Pair<String, String>>, now: Long): ExerciseEntity {
        val entity = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = exercise.name,
            nameVi = null,
            category = guessCategory(exercise).key,
            bodyPart = guessBodyPart(exercise.name, library),
            primaryMuscle = null,
            secondaryMuscles = emptyList(),
            defaultRestSeconds = null,
            isCustom = true,
            createdAt = now,
            updatedAt = now,
        )
        exerciseDao.upsert(entity)
        return entity
    }

    /** Strong names exercises like "Bench Press (Dumbbell)"; otherwise go by what the sets record. */
    private fun guessCategory(exercise: CsvExercise): ExerciseCategory {
        categoryFromName(exercise.name)?.let { key -> ExerciseCategory.entries.firstOrNull { it.key == key }?.let { return it } }
        return when {
            exercise.sets.any { it.distanceM != null } -> ExerciseCategory.CARDIO
            exercise.sets.any { it.weightKg != null } -> ExerciseCategory.BARBELL
            exercise.sets.any { it.reps != null } -> ExerciseCategory.REPS_ONLY
            exercise.sets.any { it.seconds != null } -> ExerciseCategory.DURATION
            else -> ExerciseCategory.BARBELL
        }
    }
}
