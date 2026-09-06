package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.StatMod
import com.spartanlabs.gaming.gameobjects.compareTo
import com.spartanlabs.gaming.gameobjects.div
import com.spartanlabs.gaming.gameobjects.plus
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [ModularStat]'s arithmetic against plain numbers and its [StatMod] application rules. */
class ModularStatTest {

    //region ARITHMETIC
    @Test
    fun `arithmetic and comparison operate on the effective value`() {
        val stat = ModularStat(10.0)

        assertEquals(15.0, stat + 5.0)
        assertEquals(8.0, stat - 2.0)
        assertEquals(20.0, stat * 2.0)
        assertEquals(5.0, stat / 2.0)
        assertEquals(13.0, stat + ModularStat(3.0))
        assertEquals(0, stat.compareTo(10.0))
        assertTrue(stat > 5.0)
        assertFalse(stat > 20.0)
        assertEquals(10.0, +stat)
        assertEquals(-10.0, -stat)
        assertEquals("10.0", stat.toString())
    }

    @Test
    fun `a Double on the left interoperates with the stat`() {
        val stat = ModularStat(10.0)

        assertEquals(15.0, 5.0 + stat)
        assertEquals(4.0, 40.0 / stat)
        assertTrue(5.0 < stat)
        assertFalse(20.0 < stat)
    }
    //endregion

    //region MODIFIERS
    @Test
    fun `a flat additive mod shifts the value and can be removed`() {
        val stat = ModularStat(10.0)
        val mod = StatMod("armor", 5.0, StatMod.Type.ADDITIVE, StatMod.StackingType.ADDITIVE)

        stat.applyMod(mod)
        assertEquals(15.0, stat.value)

        stat.removeMod(mod)
        assertEquals(10.0, stat.value)
    }

    @Test
    fun `additively-stacking multiplicative mods sum into one bonus`() {
        val stat = ModularStat(10.0)

        stat.applyMod(StatMod("a", 0.2, StatMod.Type.MULTIPLICATIVE, StatMod.StackingType.ADDITIVE))
        stat.applyMod(StatMod("b", 0.3, StatMod.Type.MULTIPLICATIVE, StatMod.StackingType.ADDITIVE))

        assertEquals(15.0, stat.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `multiplicatively-stacking mods multiply together`() {
        val stat = ModularStat(10.0)

        stat.applyMod(StatMod("a", 1.5, StatMod.Type.MULTIPLICATIVE, StatMod.StackingType.MULTIPLICATIVE))
        stat.applyMod(StatMod("b", 2.0, StatMod.Type.MULTIPLICATIVE, StatMod.StackingType.MULTIPLICATIVE))

        assertEquals(30.0, stat.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an incrementing mod folds into the one already applied and removeMod undoes it`() {
        val stat = ModularStat(10.0)

        stat.applyMod(StatMod("bleed", 2.0, StatMod.Type.ADDITIVE, StatMod.StackingType.INCREMENTING))
        assertEquals(12.0, stat.value)

        stat.applyMod(StatMod("bleed", 3.0, StatMod.Type.ADDITIVE, StatMod.StackingType.INCREMENTING))
        assertEquals(15.0, stat.value)

        stat.removeMod(StatMod("bleed", 0.0, StatMod.Type.ADDITIVE, StatMod.StackingType.INCREMENTING))
        assertEquals(10.0, stat.value)
    }

    @Test
    fun `a setting mod replaces the value of the one already applied`() {
        val stat = ModularStat(10.0)

        stat.applyMod(StatMod("curse", 100.0, StatMod.Type.ADDITIVE, StatMod.StackingType.SETTING))
        assertEquals(110.0, stat.value)

        stat.applyMod(StatMod("curse", 5.0, StatMod.Type.ADDITIVE, StatMod.StackingType.SETTING))
        assertEquals(15.0, stat.value)
    }

    @Test
    fun `a NONE-stacking mod is inert`() {
        val stat = ModularStat(10.0)

        stat.applyMod(StatMod("ghost", 5.0, StatMod.Type.ADDITIVE, StatMod.StackingType.NONE))

        assertEquals(10.0, stat.value)
    }
    //endregion
}
