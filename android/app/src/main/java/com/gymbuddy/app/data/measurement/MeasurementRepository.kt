package com.gymbuddy.app.data.measurement

import com.gymbuddy.app.data.GymBuddyDatabase
import com.gymbuddy.app.data.health.HealthConnectManager
import com.gymbuddy.app.domain.MeasurementKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepository @Inject constructor(db: GymBuddyDatabase, private val health: HealthConnectManager) {
    private val dao = db.measurementDao()

    val measurements = dao.observeAll()

    /** [value] is in the stored unit (kg for body weight). */
    suspend fun add(kind: MeasurementKind, value: Double, measuredAt: Long) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            MeasurementEntity(
                id = id,
                kind = kind.key,
                value = value,
                unit = kind.unit.key,
                measuredAt = measuredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (kind == MeasurementKind.WEIGHT) health.onBodyWeight(id, value, measuredAt, now)
    }

    /** Soft delete, so the delete reaches other devices through sync. */
    suspend fun delete(id: String) {
        val existing = dao.byId(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsert(existing.copy(deletedAt = now, updatedAt = now))
    }
}
