package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.CoreCapability
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Covers how a [Buff] steers an [Alive]: suppressing its attack cycle, rooting it while its
 * other per-tick work still runs, modifying combat stats, and dealing damage over time from
 * [Buff.onTick].
 */
class AliveBuffTest {

    private fun alive(x: Double = 0.0, y: Double = 0.0, maxHealth: Double = 100.0) = Alive(
        location = Point(x, y),
        dimensions = Dimensions(width = 10.0, height = 10.0),
        maxHealth = maxHealth
    )

    private fun Alive.attackEveryTick() { attackSpeed = ModularStat(100_000.0) }

    /** A buff that drains [perTick] health from an [Alive] every tick it is active. */
    private class Bleed(name: String, durationTicks: Int, private val perTick: Double) : Buff(name, durationTicks) {
        override fun onTick(target: GameObject) {
            if (target is Alive) target.health.current -= perTick
        }
    }

    @Test
    fun `a disarmed attacker lands no hits, then resumes once the buff expires`() {
        val attacker = alive(0.0, 0.0).apply { attackEveryTick() }
        val target = alive(100.0, 0.0)
        attacker.issueAttack(target)
        attacker.applyBuff(Buff("disarm", durationTicks = 3, suppressedCapabilities = setOf(CoreCapability.ATTACK)))

        repeat(3) { attacker.tick() }
        assertEquals(100.0, target.health.current, "no swing progresses while ATTACK is suppressed")

        repeat(6) { attacker.tick() }
        assertTrue(target.health.current < 100.0, "the attack order resumes after the disarm ends")
    }

    @Test
    fun `a rooted alive stops chasing but still tracks its health bar`() {
        val unit = alive(0.0, 0.0, maxHealth = 100.0)
        val faraway = alive(5000.0, 0.0)
        unit.issueAttack(faraway)
        unit.applyBuff(Buff("root", durationTicks = 5, suppressedCapabilities = setOf(CoreCapability.MOVE)))
        unit.health.current = 40.0

        repeat(3) { unit.tick() }

        assertEquals(0.0, unit.location.x, absoluteTolerance = 1e-9, "a rooted unit does not close the gap")
        assertEquals(10.0 * 0.4, unit.healthBar.dimensions.width, absoluteTolerance = 1e-9, "the health bar still updates")
    }

    @Test
    fun `a damage buff raises the health removed per swing while it lasts`() {
        fun run(withBuff: Boolean): Double {
            val attacker = alive(0.0, 0.0).apply { attackEveryTick() }
            val target = alive(100.0, 0.0, maxHealth = 1_000.0)
            if (withBuff) attacker.applyBuff(
                Buff("rage", durationTicks = -1, statMods = mapOf("damage" to StatMod("rage", 1.0)))
            )
            attacker.issueAttack(target)
            repeat(8) { attacker.tick() }
            return 1_000.0 - target.health.current
        }

        assertTrue(run(withBuff = true) > run(withBuff = false), "doubled damage should out-damage the baseline")
    }

    @Test
    fun `a bleed buff can kill through Buff onTick and death is handled the same tick`() {
        val unit = alive(0.0, 0.0, maxHealth = 30.0).apply { deathResponse = Alive.DeathResponse.RESPAWN }
        unit.respawn = Point(500.0, 500.0)
        unit.applyBuff(Bleed("bleed", durationTicks = -1, perTick = 12.0))

        repeat(3) { unit.tick() } // 12 + 12 + 12 = 36 > 30

        assertTrue(unit.isAlive, "the RESPAWN response restored it after the lethal tick")
        assertEquals(500.0, unit.location.x, absoluteTolerance = 1e-9)
    }
}
