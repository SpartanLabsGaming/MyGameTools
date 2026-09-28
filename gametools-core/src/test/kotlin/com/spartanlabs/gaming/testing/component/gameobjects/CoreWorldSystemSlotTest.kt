package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers [CoreWorldSystemSlot]'s ordering contract. */
@OptIn(ExperimentalGameToolsApi::class)
class CoreWorldSystemSlotTest {

    @Test
    fun `PHYSICS orders before ZONE`() {
        assertTrue(CoreWorldSystemSlot.PHYSICS.order < CoreWorldSystemSlot.ZONE.order)
    }

    @Test
    fun `every library-defined slot has a pairwise-distinct order`() {
        val entries = CoreWorldSystemSlot.entries
        assertEquals(entries.map { it.order }.distinct().size, entries.size)
    }
}
