package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.CoreCapability
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. Pins the timing laws of the buff lifecycle over a sweep of
 * durations: a buff of duration `n` gates behaviour for exactly `n` ticks, its stat mods net
 * to zero once it lifts, and an indefinite buff never expires on its own.
 */
class BuffDurationLawsTest {

    private val durations = listOf(1, 2, 3, 5, 8, 20)

    /** An actor at the origin travelling +x, so its per-tick step length is its [Point.x]. */
    private fun directionalActor() = Actor(location = Point(0.0, 0.0)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `a root of duration n stops movement for exactly n ticks`() {
        for (n in durations) {
            val actor = directionalActor()
            actor.applyBuff(Buff("root", durationTicks = n, suppressedCapabilities = setOf(CoreCapability.MOVE)))

            repeat(n) { actor.tick() }
            assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9, "held for all $n ticks")

            actor.tick()
            assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9, "free on tick ${n + 1}")
        }
    }

    @Test
    fun `a stat mod from a buff of duration n is fully reverted after n ticks`() {
        for (n in durations) {
            val actor = directionalActor()
            actor.applyBuff(Buff("haste", durationTicks = n, statMods = mapOf("speed" to StatMod("haste", 0.5))))

            repeat(n) {
                assertEquals(15.0, actor.speed.value, absoluteTolerance = 1e-9, "boosted during tick ${it + 1} of $n")
                actor.tick()
            }
            assertEquals(10.0, actor.speed.value, absoluteTolerance = 1e-9, "restored after $n ticks")
            assertTrue(actor.buffs.isEmpty())
        }
    }

    @Test
    fun `an indefinite buff survives an arbitrary number of ticks`() {
        for (n in durations) {
            val actor = directionalActor()
            actor.applyBuff(Buff("aura", durationTicks = -1, suppressedCapabilities = setOf(CoreCapability.MOVE)))

            repeat(n) { actor.tick() }

            assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9)
            assertEquals(1, actor.buffs.size)
        }
    }
}
