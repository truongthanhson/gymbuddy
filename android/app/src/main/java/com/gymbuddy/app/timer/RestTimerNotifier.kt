package com.gymbuddy.app.timer

import android.Manifest
import android.app.AlarmManager
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
import com.gymbuddy.app.MainActivity
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.RestTimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the countdown notification and schedules the "rest is over" alarm. */
@Singleton
class RestTimerNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val notifications = NotificationManagerCompat.from(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, context.getString(R.string.channel_rest_countdown), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_rest_done), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            },
        )
    }

    fun show(state: RestTimerState, label: String) {
        if (canNotify()) {
            val notification = NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.rest_timer))
                .setContentText(label)
                .setWhen(state.endsAtMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .build()
            notifications.notify(ID_COUNTDOWN, notification)
        }
        scheduleAlarm(state.endsAtMillis)
    }

    /** Clears everything: used when the timer is skipped. */
    fun cancel() {
        alarms.cancel(alarmIntent())
        notifications.cancel(ID_COUNTDOWN)
        notifications.cancel(ID_DONE)
    }

    fun cancelCountdown() = notifications.cancel(ID_COUNTDOWN)

    fun showDone() {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.rest_over))
            .setContentText(context.getString(R.string.rest_over_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(ID_DONE, notification)
    }

    private fun scheduleAlarm(atMillis: Long) {
        val intent = alarmIntent()
        // Exact alarms need the user's permission from Android 12; without it the alarm may land a little late.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestTimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_COUNTDOWN = "rest_timer"
        const val CHANNEL_DONE = "rest_timer_done"
        const val ID_COUNTDOWN = 1001
        const val ID_DONE = 1002
    }
}
