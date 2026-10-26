package com.gymbuddy.app.data.coach

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymbuddy.app.MainActivity
import com.gymbuddy.app.R
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.sync.AuthRepository
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.domain.reminderDueAt
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Buddy's notifications: a nudge when the person falls behind their weekly goal, and the Monday weekly report. */
@Singleton
class BuddyReminders @Inject constructor(@ApplicationContext private val context: Context) {

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.buddy_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Runs the daily check around 09:00 while reminders are on. */
    fun apply(enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.with(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<BuddyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(id: Int, title: String, text: String) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission") // Checked by canNotify().
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL = "buddy"
        const val WORK = "buddy-reminders"
        const val ID_TRAIN = 3001
        const val ID_WEEKLY = 3002
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BuddyReminderEntryPoint {
    fun prefs(): BuddyPrefs
    fun workouts(): WorkoutDao
    fun pro(): ProRepository
    fun auth(): AuthRepository
    fun api(): CoachApi
    fun reminders(): BuddyReminders
}

class BuddyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, BuddyReminderEntryPoint::class.java)
        val prefs = deps.prefs()
        val settings = prefs.settings.value
        if (!settings.reminders) return Result.success()
        val reminders = deps.reminders()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        // Training reminder, at most once per gap.
        val due = reminderDueAt(deps.workouts().getLastFinished()?.startedAt, settings.daysPerWeek)
        if (due != null && now >= due && prefs.lastReminderAt < due) {
            reminders.notify(
                BuddyReminders.ID_TRAIN,
                applicationContext.getString(R.string.buddy_remind_title),
                applicationContext.getString(R.string.buddy_remind_text, settings.daysPerWeek),
            )
            prefs.lastReminderAt = now
        }

        // The weekly report, on Mondays, when Buddy AI can write it.
        val today = ZonedDateTime.now(zone)
        val weekly = deps.pro().isPro.value && settings.aiConsent && deps.api().isAvailable && deps.auth().session.value != null
        val week = lastFullWeek(now, zone).monday.toString()
        if (weekly && today.dayOfWeek == DayOfWeek.MONDAY && prefs.lastWeeklyNotice != week) {
            reminders.notify(
                BuddyReminders.ID_WEEKLY,
                applicationContext.getString(R.string.buddy_weekly_ready_title),
                applicationContext.getString(R.string.buddy_weekly_ready_text),
            )
            prefs.lastWeeklyNotice = week
        }
        return Result.success()
    }
}
