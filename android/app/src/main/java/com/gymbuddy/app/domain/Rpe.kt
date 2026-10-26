package com.gymbuddy.app.domain

/** RPE choices offered for a set, 6 to 10 in half steps, like Strong. */
val RPE_OPTIONS: List<Double> = (12..20).map { it / 2.0 }

/** "8" or "8.5". */
fun formatRpe(rpe: Double): String = formatWeight(rpe)
