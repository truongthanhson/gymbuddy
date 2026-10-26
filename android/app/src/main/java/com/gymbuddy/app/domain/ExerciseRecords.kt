package com.gymbuddy.app.domain

/** One finished workout's sets of a single exercise. [startedAt] is the workout's start, epoch millis. */
data class ExerciseSession(val startedAt: Long, val sets: List<SetValues>)

/** A best value and the workout that first reached it. */
data class Record(val value: Double, val startedAt: Long)

data class ExerciseRecords(
    val estimatedOneRepMax: Record? = null,
    val maxWeight: Record? = null,
    /** Heaviest single set by weight × reps. */
    val maxSetVolume: Record? = null,
    val maxReps: Record? = null,
    /** Most weight × reps in one workout. */
    val maxSessionVolume: Record? = null,
)

/** One chart point per workout. */
data class SessionPoint(val startedAt: Long, val estimatedOneRepMax: Double, val maxWeight: Double, val volumeKg: Double)

/**
 * Records and chart points for an exercise; must match shared/test-vectors/exercise_records.json (and iOS).
 * Only completed sets with reps > 0 count; a record belongs to the first workout that reached it.
 */
fun exerciseProgress(sessions: List<ExerciseSession>): Pair<ExerciseRecords, List<SessionPoint>> {
    var records = ExerciseRecords()
    val points = mutableListOf<SessionPoint>()
    fun better(current: Record?, value: Double, at: Long) = if (current == null || value > current.value) Record(value, at) else current

    for (session in sessions.sortedBy { it.startedAt }) {
        val sets = session.sets.filter { it.completed && (it.reps ?: 0) > 0 }
        if (sets.isEmpty()) continue
        val at = session.startedAt
        val bestOneRepMax = sets.maxOf { estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) }
        val maxWeight = sets.maxOf { it.weightKg ?: 0.0 }
        val maxSetVolume = sets.maxOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        val maxReps = sets.maxOf { it.reps ?: 0 }.toDouble()
        val volume = volumeKg(sets)
        records = ExerciseRecords(
            estimatedOneRepMax = better(records.estimatedOneRepMax, bestOneRepMax, at),
            maxWeight = better(records.maxWeight, maxWeight, at),
            maxSetVolume = better(records.maxSetVolume, maxSetVolume, at),
            maxReps = better(records.maxReps, maxReps, at),
            maxSessionVolume = better(records.maxSessionVolume, volume, at),
        )
        points += SessionPoint(at, bestOneRepMax, maxWeight, volume)
    }
    return records to points
}
