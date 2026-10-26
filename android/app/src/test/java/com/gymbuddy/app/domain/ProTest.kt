package com.gymbuddy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProTest {
    @Test
    fun `free users keep a few routines`() {
        assertTrue(canAddTemplate(existing = 0, isPro = false))
        assertTrue(canAddTemplate(existing = FREE_TEMPLATE_LIMIT - 1, isPro = false))
        assertFalse(canAddTemplate(existing = FREE_TEMPLATE_LIMIT, isPro = false))
        assertTrue(canAddTemplate(existing = 100, isPro = true))
    }

    @Test
    fun `body weight stays free`() {
        assertFalse(measurementNeedsPro(MeasurementKind.WEIGHT))
        assertEquals(MeasurementKind.entries.size - 1, MeasurementKind.entries.count(::measurementNeedsPro))
    }

    @Test
    fun `store ids match iOS`() {
        assertEquals(listOf("gymbuddy_pro_monthly", "gymbuddy_pro_yearly", "gymbuddy_pro_lifetime"), ProProducts.all)
    }
}
