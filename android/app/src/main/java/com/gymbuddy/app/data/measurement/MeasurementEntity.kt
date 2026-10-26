package com.gymbuddy.app.data.measurement

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One body measurement; [kind] and [unit] use the keys of [com.gymbuddy.app.domain.MeasurementKind]. */
@Entity(tableName = "measurements", indices = [Index("kind", "measuredAt")])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val value: Double,
    val unit: String,
    val measuredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
