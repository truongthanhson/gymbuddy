package com.gymbuddy.app.domain

/** Routines a free user can keep; Pro removes the limit, like Strong. Matches iOS. */
const val FREE_TEMPLATE_LIMIT = 3

/** What GymBuddy Pro unlocks. Everything else, including logging, history and sync, stays free. */
enum class ProFeature { UNLIMITED_TEMPLATES, EXERCISE_CHARTS, BODY_MEASUREMENTS }

fun canAddTemplate(existing: Int, isPro: Boolean): Boolean = isPro || existing < FREE_TEMPLATE_LIMIT

/** Body weight stays free; the other measurements need Pro. */
fun measurementNeedsPro(kind: MeasurementKind): Boolean = kind != MeasurementKind.WEIGHT

/** Store product ids, the same on Google Play and the App Store. */
object ProProducts {
    const val MONTHLY = "gymbuddy_pro_monthly"
    const val YEARLY = "gymbuddy_pro_yearly"
    const val LIFETIME = "gymbuddy_pro_lifetime"
    val subscriptions = listOf(MONTHLY, YEARLY)
    val all = subscriptions + LIFETIME
}
