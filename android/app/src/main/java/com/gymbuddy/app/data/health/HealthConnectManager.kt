package com.gymbuddy.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One day of the health data Buddy may read. */
data class DayHealth(val date: LocalDate, val sleepHours: Double?, val steps: Long?)

/**
 * Writes finished workouts and body weight to Health Connect when the user turned it on in settings.
 * Records carry our row id as clientRecordId, so writing the same row again replaces it instead of adding a copy.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val db: GymBuddyDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    /** Health Connect is installed (built in from Android 14) and usable. */
    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** What Buddy may read when the person shares sleep and steps with it. */
    val buddyReadPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    suspend fun hasBuddyReadPermissions(): Boolean =
        isAvailable && client.permissionController.getGrantedPermissions().containsAll(buddyReadPermissions)

    /** Sleep (hours, by the day the sleep ended) and steps for each of the last [days] days, oldest first. */
    suspend fun recentSleepAndSteps(days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): List<DayHealth> {
        val today = LocalDate.now(zone)
        val first = today.minusDays(days - 1L)
        val start = first.atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        val steps = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        val sleep = mutableMapOf<LocalDate, Long>()
        val sessions = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start.minusDays(1).atZone(zone).toInstant(), end.atZone(zone).toInstant())),
        ).records
        for (r in sessions) {
            val day = r.endTime.atZone(zone).toLocalDate()
            if (day < first || day > today) continue
            sleep.merge(day, Duration.between(r.startTime, r.endTime).toMinutes()) { a, b -> a + b }
        }
        return (0 until days).map { i ->
            val day = first.plusDays(i.toLong())
            DayHealth(day, sleep[day]?.let { it / 60.0 }, steps[day])
        }
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        isAvailable && client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Writes a finished workout as a strength training session; does nothing when turned off or not allowed. */
    fun onWorkoutFinished(workoutId: String) = whenEnabled {
        val workout = db.workoutDao().getWorkout(workoutId) ?: return@whenEnabled
        val endedAt = workout.endedAt ?: return@whenEnabled
        if (workout.deletedAt != null || endedAt <= workout.startedAt) return@whenEnabled
        val start = Instant.ofEpochMilli(workout.startedAt)
        val end = Instant.ofEpochMilli(endedAt)
        client.insertRecords(
            listOf(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = offsetAt(start),
                    endTime = end,
                    endZoneOffset = offsetAt(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = workout.name,
                    notes = workout.note,
                    metadata = Metadata(clientRecordId = workout.id, clientRecordVersion = workout.updatedAt),
                ),
            ),
        )
    }

    /** Writes one body weight entry (kg). */
    fun onBodyWeight(id: String, kg: Double, measuredAt: Long, version: Long) = whenEnabled {
        val time = Instant.ofEpochMilli(measuredAt)
        client.insertRecords(
            listOf(
                WeightRecord(
                    time = time,
                    zoneOffset = offsetAt(time),
                    weight = Mass.kilograms(kg),
                    metadata = Metadata(clientRecordId = id, clientRecordVersion = version),
                ),
            ),
        )
    }

    private fun offsetAt(instant: Instant) = ZoneId.systemDefault().rules.getOffset(instant)

    private fun whenEnabled(block: suspend () -> Unit) {
        if (!settings.settings.value.healthEnabled) return
        // Health data is a copy; a failed write must never break logging.
        scope.launch { runCatching { if (hasPermissions()) block() } }
    }
}
