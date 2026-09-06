package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.Player
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/** Covers [Alive]'s health tracking and the health bar sub-object it manages. */
class AliveTest {

    private fun alive(maxHealth: Double = 100.0) = Alive(
        location = Point(0.0, 0.0),
        dimensions = Dimensions(width = 20.0, height = 10.0),
        maxHealth = maxHealth
    )

    @Test
    fun `building the health bar does not resize the actor`() {
        val actor = alive()

        assertEquals(20.0, actor.dimensions.width)
        assertEquals(10.0, actor.dimensions.height)
    }

    @Test
    fun `building the health bar does not move the actor`() {
        val actor = Alive(
            location = Point(3.0, 4.0),
            dimensions = Dimensions(width = 20.0, height = 10.0),
            maxHealth = 100.0
        )

        assertEquals(3.0, actor.location.x)
        assertEquals(4.0, actor.location.y)
    }

    @Test
    fun `the health bar shrinks proportionally as health drops`() {
        val actor = alive(maxHealth = 100.0)
        actor.health.current = 40.0

        actor.tick()

        assertEquals(20.0 * 0.4, actor.healthBar.dimensions.width, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the health bar width never goes negative when health is below zero`() {
        val actor = alive(maxHealth = 100.0)
        actor.health.current = -10.0

        actor.tick()

        assertEquals(0.0, actor.healthBar.dimensions.width)
        assertFalse(actor.isAlive)
    }

    @Test
    fun `the health bar follows the actor as it moves`() {
        val actor = alive() // at (0,0), height 10 -> bar sits 10 * (0.5 + 0.2/2) = 6.0 above the actor (-y)
        actor.destination = Point(100.0, 0.0)

        actor.tick() // one step of length speed (10)

        assertEquals(actor.location.x, actor.healthBar.location.x)
        assertEquals(actor.location.y - 10.0 * (0.5 + 0.2 / 2), actor.healthBar.location.y)
        assertEquals(10.0, actor.healthBar.location.x)
    }

    @Test
    fun `the health bar is registered as a sub-object`() {
        val actor = alive()

        assertTrue(actor.healthBar in actor.subObjects)
    }

    @Test
    fun `a new actor is neutral and its faction can be reassigned`() {
        val actor = alive()

        assertEquals("neutral", actor.faction)
        assertEquals(Alive.DEFAULT_FACTION, actor.faction)

        actor.faction = "red"
        assertEquals("red", actor.faction)
    }

    @Test
    fun `a new actor is unowned until an owner is assigned`() {
        val actor = alive()

        assertNull(actor.owner)
        assertFalse(actor.hasOwner)

        val player = Player("alice")
        actor.owner = player

        assertEquals(player, actor.owner)
        assertTrue(actor.hasOwner)
    }
}
