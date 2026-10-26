package com.gymbuddy.app.di

import android.content.Context
import androidx.room.Room
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.workout.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymBuddyDatabase =
        Room.databaseBuilder(context, GymBuddyDatabase::class.java, "gymbuddy.db")
            // Pre-release: no user data to keep yet. Replace with real migrations before the first release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideExerciseDao(db: GymBuddyDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutDao(db: GymBuddyDatabase): WorkoutDao = db.workoutDao()
}
