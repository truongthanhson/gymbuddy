package com.gymbuddy.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
