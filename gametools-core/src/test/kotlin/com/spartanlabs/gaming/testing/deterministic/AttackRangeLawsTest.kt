package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.combat.Alive
import com.spartanlabs.gaming.gameobjects.combat.ModularStat
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. [Alive.isWithinAttackRangeOf] is a pure distance-vs-range
 * predicate; this pins it against [potentialAttacker]'s [Alive.attackRange] specifically, not
 * the receiver's own, plus the exact boundary.
 */
class AttackRangeLawsTest {

    /** Exposes the protected [Alive.isWithinAttackRangeOf] for direct testing. */
    private class ExposedAlive(x: Double) : Alive(Point(x, 0.0), Dimensions(10.0, 10.0), maxHealth = 100.0) {
        fun isInRangeOf(potentialAttacker: Alive) = this isWithinAttackRangeOf potentialAttacker
    }

    @Test
    fun `within the attacker's range is true`() {
        val attacker = ExposedAlive(0.0).apply { attackRange = ModularStat(100.0) }
        val target = ExposedAlive(50.0)

        assertTrue(target.isInRangeOf(attacker))
    }

    @Test
    fun `beyond the attacker's range is false`() {
        val attacker = ExposedAlive(0.0).apply { attackRange = ModularStat(100.0) }
        val target = ExposedAlive(150.0)

        assertFalse(target.isInRangeOf(attacker))
    }

    @Test
    fun `exactly at the attacker's range is true`() {
        val attacker = ExposedAlive(0.0).apply { attackRange = ModularStat(100.0) }
        val target = ExposedAlive(100.0)

        assertTrue(target.isInRangeOf(attacker))
    }

    @Test
    fun `the check uses the parameter's range, not the receiver's`() {
        val nearButShortRanged = ExposedAlive(0.0).apply { attackRange = ModularStat(10.0) }
        val farButLongRanged = ExposedAlive(100.0).apply { attackRange = ModularStat(1000.0) }

        // farButLongRanged is within nearButShortRanged's range only if nearButShortRanged's
        // (the parameter's) attackRange is the one being checked - which is far too short here.
        assertFalse(farButLongRanged.isInRangeOf(nearButShortRanged))
        // The reverse check uses nearButShortRanged's own (long) attackRange as the parameter.
        assertTrue(nearButShortRanged.isInRangeOf(farButLongRanged))
    }
}
