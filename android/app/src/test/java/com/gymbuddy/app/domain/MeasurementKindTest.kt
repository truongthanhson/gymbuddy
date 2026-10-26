package com.gymbuddy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementKindTest {
    @Test
    fun bodyWeightIsStoredInKgAndShownInTheUserUnit() {
        val stored = MeasurementKind.WEIGHT.toStored(180.0, WeightUnit.LB)
        assertEquals(81.65, stored, 0.01)
        assertEquals(180.0, MeasurementKind.WEIGHT.display(stored, WeightUnit.LB), 1e-9)
        assertEquals("180 lb", MeasurementKind.WEIGHT.format(stored, WeightUnit.LB))
    }

    @Test
    fun otherKindsKeepTheirUnit() {
        assertEquals(38.0, MeasurementKind.NECK.toStored(38.0, WeightUnit.LB), 1e-9)
        assertEquals("38 cm", MeasurementKind.NECK.format(38.0, WeightUnit.LB))
        assertEquals("18.25 %", MeasurementKind.BODY_FAT.format(18.25, WeightUnit.KG))
    }

    @Test
    fun keysMatchTheServerSchema() {
        assertEquals(MeasurementKind.ARM_LEFT, MeasurementKind.fromKey("arm_left"))
        assertEquals(null, MeasurementKind.fromKey("unknown"))
    }
}
