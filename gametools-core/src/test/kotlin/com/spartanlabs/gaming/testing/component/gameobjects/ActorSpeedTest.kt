package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Covers [Actor.speed] now that it is a [ModularStat]: its default, and [StatMod]s changing
 * how far the actor travels per tick via its effective [ModularStat.value].
 */
class ActorSpeedTest {

    /** An actor at the origin travelling +x, so its per-tick step length shows up as [Point.x]. */
    private fun directionalActor() = Actor(location = Point(0.0, 0.0)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `speed defaults to a base of 10`() {
        val actor = directionalActor()
        assertEquals(10.0, actor.speed.base)
        assertEquals(10.0, actor.speed.value)
    }

    @Test
    fun `a directional actor advances by its effective speed each tick`() {
        val actor = directionalActor()
        actor.tick()
        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a haste mod makes the actor travel further per tick`() {
        val actor = directionalActor()
        actor.speed.applyMod(StatMod("haste", 0.5)) // +50% -> 15 units/tick

        actor.tick()

        assertEquals(15.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `removing a slow mod restores the original step length`() {
        val actor = directionalActor()
        val slow = StatMod("cripple", -0.6) // -60% -> 4 units/tick

        actor.speed.applyMod(slow)
        actor.tick()
        assertEquals(4.0, actor.location.x, absoluteTolerance = 1e-9)

        actor.speed.removeMod(slow)
        actor.tick()
        assertEquals(14.0, actor.location.x, absoluteTolerance = 1e-9) // 4 + a full 10-unit step
    }

    @Test
    fun `assigning a fresh ModularStat replaces the actor's speed wholesale`() {
        val actor = directionalActor()
        actor.speed = ModularStat(base = 25.0)

        actor.tick()

        assertEquals(25.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a speed buff applied through applyBuff wears off on its own`() {
        val actor = directionalActor()
        actor.applyBuff(Buff("haste", durationTicks = 2, statMods = mapOf("speed" to StatMod("haste", 0.5))))

        actor.tick() // +50% -> 15
        actor.tick() // still +50% this tick, then the buff is pruned
        assertEquals(30.0, actor.location.x, absoluteTolerance = 1e-9)

        actor.tick() // back to base 10
        assertEquals(40.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a buff that reassigns nothing still tracks a wholesale speed replacement`() {
        val actor = directionalActor()
        actor.speed = ModularStat(base = 20.0)
        actor.applyBuff(Buff("haste", durationTicks = -1, statMods = mapOf("speed" to StatMod("haste", 0.5))))

        actor.tick()

        assertEquals(30.0, actor.location.x, absoluteTolerance = 1e-9) // 20 * 1.5, proving stats[] followed the new object
    }
}
