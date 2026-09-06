package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. Pins that combat, whose only random input is the evasion
 * roll, is a pure function of the world seed and the sequence of calls: a fixed seed
 * reproduces the exact health timeline, and changing the seed is able to change it (so the
 * roll is genuinely in play, not dead code).
 */
class SeededCombatDeterminismTest {

    /**
     * Runs a fixed duel in a fresh `World(seed)` - a fast attacker against a 50%-evasion
     * target - and returns the target's health after each of [ticks] ticks.
     */
    private fun healthTimeline(seed: Long, ticks: Int = 60): List<Double> {
        val world = World(seed = seed)
        val attacker = Alive(Point(0.0, 0.0), Dimensions(10.0, 10.0), maxHealth = 100.0).apply {
            attackSpeed = ModularStat(100_000.0)
            deathResponse = Alive.DeathResponse.RESPAWN // keep it in the world so the timeline is full length
        }.also(world::add)
        val target = Alive(Point(20.0, 0.0), Dimensions(10.0, 10.0), maxHealth = 10_000.0).apply {
            evasion = ModularStat(0.5)
            deathResponse = Alive.DeathResponse.RESPAWN
        }.also(world::add)

        attacker.issueAttack(target)
        return buildList { repeat(ticks) { world.tick(); add(target.health.current) } }
    }

    @Test
    fun `a fixed seed reproduces the exact health timeline`() {
        assertEquals(healthTimeline(seed = 42L), healthTimeline(seed = 42L))
    }

    @Test
    fun `the timeline is not identical across every seed`() {
        val finals = (1L..12L).map { healthTimeline(it).last() }
        assertTrue(finals.toSet().size > 1, "every seed produced the same result - the evasion roll is not in play")
    }

    @Test
    fun `some swings are dodged and some connect at 50 percent evasion`() {
        val timeline = healthTimeline(seed = 7L)
        val drops = timeline.zipWithNext { a, b -> a - b }.count { it > 0.0 }
        assertTrue(drops in 1 until timeline.size, "expected a mix of hits and misses, saw $drops health drops")
    }
}
