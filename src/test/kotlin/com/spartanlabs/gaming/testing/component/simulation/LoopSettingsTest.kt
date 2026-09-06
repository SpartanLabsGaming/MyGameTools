package com.spartanlabs.gaming.testing.component.simulation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.simulation.LoopSettings
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
//endregion

/** Covers [LoopSettings] validating [LoopSettings.tickRateHz] and clamping [LoopSettings.maxCatchUpTicks]. */
class LoopSettingsTest {

    @Test
    fun `a non-positive tick rate is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { LoopSettings(tickRateHz = 0.0) }
        assertFailsWith<IllegalArgumentException> { LoopSettings(tickRateHz = -5.0) }
    }

    @Test
    fun `a non-finite tick rate is rejected`() {
        assertFailsWith<IllegalArgumentException> { LoopSettings(tickRateHz = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { LoopSettings(tickRateHz = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `a non-positive tick rate is rejected when assigned later`() {
        val settings = LoopSettings(tickRateHz = 20.0)
        assertFailsWith<IllegalArgumentException> { settings.tickRateHz = 0.0 }
        assertEquals(20.0, settings.tickRateHz, "the rejected assignment must not have taken effect")
    }

    @Test
    fun `maxCatchUpTicks is clamped to at least one`() {
        assertEquals(1, LoopSettings(maxCatchUpTicks = 0).maxCatchUpTicks)
        assertEquals(1, LoopSettings(maxCatchUpTicks = -10).maxCatchUpTicks)

        val settings = LoopSettings(maxCatchUpTicks = 5)
        settings.maxCatchUpTicks = 0
        assertEquals(1, settings.maxCatchUpTicks)
    }

    @Test
    fun `valid values are kept as given`() {
        val settings = LoopSettings(tickRateHz = 30.0, maxCatchUpTicks = 8)
        assertEquals(30.0, settings.tickRateHz)
        assertEquals(8, settings.maxCatchUpTicks)
    }
}
