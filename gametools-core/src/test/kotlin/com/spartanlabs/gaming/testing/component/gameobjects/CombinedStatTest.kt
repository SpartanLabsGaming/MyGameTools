package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.CombinedStat
import com.spartanlabs.gaming.gameobjects.StatMod
import com.spartanlabs.gaming.gameobjects.compareTo
import com.spartanlabs.gaming.gameobjects.plus
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
//endregion

/** Covers [CombinedStat]'s construction guards, derived readouts, arithmetic, and modifier rescaling. */
class CombinedStatTest {

    //region CONSTRUCTION & READOUTS
    @Test
    fun `fractionOfMax reports the current amount against the max`() {
        val stat = CombinedStat(startingValue = 50.0, maxValue = 100.0)

        assertEquals(0.5, stat.fractionOfMax)
    }

    @Test
    fun `a stat built from a single number starts full at that value`() {
        val stat = CombinedStat(startingValue = 80.0)

        assertEquals(80.0, stat.current)
        assertEquals(80.0, stat.max.value)
        assertEquals(1.0, stat.fractionOfMax)
    }

    @Test
    fun `a non-positive max is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            CombinedStat(startingValue = 0.0, maxValue = 0.0)
        }
    }
    //endregion

    //region ARITHMETIC
    @Test
    fun `arithmetic and comparison operate on the current amount`() {
        val stat = CombinedStat(startingValue = 30.0, maxValue = 100.0)

        assertEquals(35.0, stat + 5.0)
        assertEquals(25.0, stat - 5.0)
        assertEquals(60.0, stat * 2.0)
        assertEquals(15.0, stat / 2.0)
        assertEquals(0, stat.compareTo(30.0))
        assertTrue(stat > 10.0)
        assertEquals(-30.0, -stat)
        assertEquals(35.0, 5.0 + stat)
        assertTrue(10.0 < stat)
    }
    //endregion

    //region MODIFIERS
    @Test
    fun `applying a mod scales the ceiling and keeps the current fraction`() {
        val stat = CombinedStat(startingValue = 50.0, maxValue = 100.0)
        val mod = StatMod("might", 1.0, StatMod.Type.MULTIPLICATIVE, StatMod.StackingType.ADDITIVE)

        stat.applyMod(mod)

        assertEquals(200.0, stat.max.value, absoluteTolerance = 1e-9)
        assertEquals(100.0, stat.current, absoluteTolerance = 1e-9)
        assertEquals(0.5, stat.fractionOfMax, absoluteTolerance = 1e-9)

        stat.removeMod(mod)

        assertEquals(100.0, stat.max.value, absoluteTolerance = 1e-9)
        assertEquals(50.0, stat.current, absoluteTolerance = 1e-9)
    }
    //endregion
}
