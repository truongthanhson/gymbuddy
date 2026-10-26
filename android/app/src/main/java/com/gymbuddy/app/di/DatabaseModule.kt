package com.gymbuddy.app.di

import android.content.Context
import androidx.room.Room
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.exercise.ExerciseDao
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
        Room.databaseBuilder(context, GymBuddyDatabase::class.java, "gymbuddy.db").build()

    @Provides
    fun provideExerciseDao(db: GymBuddyDatabase): ExerciseDao = db.exerciseDao()
}
