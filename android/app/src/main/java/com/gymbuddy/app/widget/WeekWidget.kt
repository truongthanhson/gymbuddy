package com.gymbuddy.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gymbuddy.app.MainActivity
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.domain.startOfWeekMillis
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun workoutDao(): WorkoutDao
}

private data class WeekSummary(val workoutsThisWeek: Int, val lastName: String?, val lastStartedAt: Long?)

/** Home screen widget: workouts this week and the latest one; tapping it opens the app. */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).workoutDao()
        val last = dao.getLastFinished()
        val summary = WeekSummary(
            workoutsThisWeek = dao.countFinishedSince(startOfWeekMillis(System.currentTimeMillis(), ZoneId.systemDefault())),
            lastName = last?.name,
            lastStartedAt = last?.startedAt,
        )
        provideContent { GlanceTheme { Content(context, summary) } }
    }

    @Composable
    private fun Content(context: Context, summary: WeekSummary) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                context.getString(R.string.widget_this_week),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Text(
                summary.workoutsThisWeek.toString(),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 36.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                context.resources.getQuantityString(R.plurals.widget_workouts, summary.workoutsThisWeek),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(8.dp))
            val lastText = if (summary.lastName != null && summary.lastStartedAt != null) {
                context.getString(
                    R.string.widget_last_workout,
                    summary.lastName,
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(summary.lastStartedAt)),
                )
            } else {
                context.getString(R.string.widget_no_workouts)
            }
            Text(lastText, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

/** Redraws the home screen widgets after workouts change. */
@Singleton
class WidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh() {
        scope.launch { runCatching { WeekWidget().updateAll(context) } }
    }
}
