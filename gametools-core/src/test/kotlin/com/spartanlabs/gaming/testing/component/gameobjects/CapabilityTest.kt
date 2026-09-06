package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.CoreCapability
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Covers the [CoreCapability] declarations on the [com.spartanlabs.gaming.gameobjects.GameObject]
 * tree and how a suppressing [Buff] gates [com.spartanlabs.gaming.gameobjects.GameObject.can].
 */
class CapabilityTest {

    private fun actor() = Actor(location = Point(0.0, 0.0))
    private fun alive() = Alive(location = Point(0.0, 0.0), dimensions = Dimensions(10.0, 10.0), maxHealth = 100.0)

    @Test
    fun `a bare visible object has no capabilities`() {
        assertTrue(VisibleObject().capabilities.isEmpty())
    }

    @Test
    fun `an actor can move but cannot attack`() {
        val a = actor()

        assertEquals(setOf(CoreCapability.MOVE), a.capabilities)
        assertTrue(a.can(CoreCapability.MOVE))
        assertFalse(a.can(CoreCapability.ATTACK))
    }

    @Test
    fun `an alive adds attack on top of the move it inherits`() {
        val a = alive()

        assertEquals(setOf(CoreCapability.MOVE, CoreCapability.ATTACK), a.capabilities)
        assertTrue(a.can(CoreCapability.MOVE))
        assertTrue(a.can(CoreCapability.ATTACK))
    }

    @Test
    fun `a suppressing buff blocks can without removing the capability from the type`() {
        val a = alive()

        a.applyBuff(Buff("stun", durationTicks = 3, suppressedCapabilities = setOf(CoreCapability.MOVE, CoreCapability.ATTACK)))

        assertFalse(a.can(CoreCapability.MOVE))
        assertFalse(a.can(CoreCapability.ATTACK))
        assertTrue(CoreCapability.MOVE in a.capabilities, "the type still owns the capability")
    }

    @Test
    fun `a capability stays suppressed until every suppressing buff is gone`() {
        val a = alive()
        val rootA = Buff("root-a", durationTicks = -1, suppressedCapabilities = setOf(CoreCapability.MOVE))
        val rootB = Buff("root-b", durationTicks = -1, suppressedCapabilities = setOf(CoreCapability.MOVE))

        a.applyBuff(rootA)
        a.applyBuff(rootB)
        assertFalse(a.can(CoreCapability.MOVE))

        a.removeBuff(rootA)
        assertFalse(a.can(CoreCapability.MOVE), "the second root still holds it")

        a.removeBuff(rootB)
        assertTrue(a.can(CoreCapability.MOVE))
    }

    @Test
    fun `a rooted actor holds position for the life of the buff then moves again`() {
        val a = actor().apply {
            movement = Movement.Directional
            angle = 0
        }
        a.applyBuff(Buff("root", durationTicks = 2, suppressedCapabilities = setOf(CoreCapability.MOVE)))

        a.tick() // buff in force
        a.tick() // buff in force, then pruned
        assertEquals(0.0, a.location.x, absoluteTolerance = 1e-9)

        a.tick() // free to move
        assertEquals(10.0, a.location.x, absoluteTolerance = 1e-9)
    }
}
