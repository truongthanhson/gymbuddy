package com.gymbuddy.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.exercise.ExerciseEntity

@Database(entities = [ExerciseEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class GymBuddyDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
}
