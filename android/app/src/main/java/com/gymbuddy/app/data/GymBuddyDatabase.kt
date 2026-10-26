package com.gymbuddy.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.sync.SyncDao
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.measurement.MeasurementDao
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateDao
import com.gymbuddy.app.data.template.TemplateEntity
import com.gymbuddy.app.data.template.TemplateExerciseEntity
import com.gymbuddy.app.data.template.TemplateSetEntity
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutSetEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        FolderEntity::class,
        TemplateEntity::class,
        TemplateExerciseEntity::class,
        TemplateSetEntity::class,
        MeasurementEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GymBuddyDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun templateDao(): TemplateDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun syncDao(): SyncDao
}
