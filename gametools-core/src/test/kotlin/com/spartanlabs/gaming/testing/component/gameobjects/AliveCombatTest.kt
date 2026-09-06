package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.ModularStat
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [Alive]'s attack cycle: closing to range, swinging on a loop, evasion, and damage. */
class AliveCombatTest {

    private fun alive(x: Double = 0.0, y: Double = 0.0, maxHealth: Double = 100.0) = Alive(
        location = Point(x, y),
        dimensions = Dimensions(width = 10.0, height = 10.0),
        maxHealth = maxHealth
    )

    /** Winds [attackSpeed] high enough that a swing lands on nearly every in-range tick. */
    private fun Alive.attackEveryTick() {
        attackSpeed = ModularStat(100_000.0)
    }

    @Test
    fun `an in-range attack damages the target in multiples of the attacker's damage`() {
        val attacker = alive(0.0, 0.0)
        val target = alive(100.0, 0.0)
        attacker.attackEveryTick()

        attacker.issueAttack(target)
        repeat(6) { attacker.tick() }

        assertTrue(target.health.current < 100.0, "target should have taken damage")
        assertEquals(0.0, (100.0 - target.health.current) % 10.0, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a target that always evades takes no damage`() {
        val attacker = alive(0.0, 0.0)
        val target = alive(100.0, 0.0)
        attacker.attackEveryTick()
        target.evasion = ModularStat(1.0)

        attacker.issueAttack(target)
        repeat(20) { attacker.tick() }

        assertEquals(100.0, target.health.current)
    }

    @Test
    fun `an attacker with an out-of-range target advances toward it`() {
        val attacker = alive(0.0, 0.0)
        val target = alive(5000.0, 0.0)

        attacker.issueAttack(target)
        repeat(3) { attacker.tick() }

        assertTrue(attacker.location.x > 0.0, "attacker should have moved toward the target")
        assertEquals(100.0, target.health.current, "no hit lands while out of range")
    }

    @Test
    fun `sustained attacks deplete the target's health`() {
        val attacker = alive(0.0, 0.0)
        val target = alive(100.0, 0.0, maxHealth = 25.0)
        attacker.attackEveryTick()

        attacker.issueAttack(target)
        repeat(20) { attacker.tick() }

        assertFalse(target.isAlive)
    }
}
