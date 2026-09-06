package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/** Covers [StatMod]'s construction defaults. */
class StatModTest {

    @Test
    fun `a mod defaults to a multiplicative, additively-stacking factor`() {
        val mod = StatMod("haste", 0.1)

        assertEquals(StatMod.Type.MULTIPLICATIVE, mod.type)
        assertEquals(StatMod.StackingType.ADDITIVE, mod.stackingType)
    }

    @Test
    fun `name, value, type and stacking are carried verbatim`() {
        val mod = StatMod("root", 3.0, StatMod.Type.ADDITIVE, StatMod.StackingType.SETTING)

        assertEquals("root", mod.name)
        assertEquals(3.0, mod.value)
        assertEquals(StatMod.Type.ADDITIVE, mod.type)
        assertEquals(StatMod.StackingType.SETTING, mod.stackingType)
    }
}
