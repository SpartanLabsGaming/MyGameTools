package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/** Covers [Alive] death handling: [Alive.respawn], [Alive.DeathResponse], and [Alive.die]. */
class AliveDeathTest {

    private fun alive(x: Double = 5.0, y: Double = 7.0, maxHealth: Double = 100.0) = Alive(
        location = Point(x, y),
        dimensions = Dimensions(width = 10.0, height = 10.0),
        maxHealth = maxHealth
    )

    @Test
    fun `respawn defaults to the creation point and is unaffected by later movement`() {
        val actor = alive(x = 5.0, y = 7.0)

        actor.location.setTo(40.0, 40.0)

        assertEquals(5.0, actor.respawn.x)
        assertEquals(7.0, actor.respawn.y)
    }

    @Test
    fun `death response defaults to REMOVAL`() {
        assertEquals(Alive.DeathResponse.REMOVAL, alive().deathResponse)
    }

    @Test
    fun `World add wires the actor's world back-reference`() {
        val world = World()
        val actor = alive()

        world.add(actor)

        assertSame(world, actor.world)
        assertTrue(actor in world.gameObjects)
    }

    @Test
    fun `a REMOVAL death queues the actor into removeList and the world then drops it`() {
        val world = World()
        val actor = alive()
        world.add(actor)
        actor.deathResponse = Alive.DeathResponse.REMOVAL
        actor.health.current = 0.0

        world.tick()

        assertFalse(actor in world.gameObjects)
        assertTrue(world.removeList.isEmpty())
    }

    @Test
    fun `a REMOVAL death without a world does not throw and leaves the actor in play`() {
        val actor = alive()
        actor.health.current = 0.0

        actor.tick()

        assertFalse(actor.isAlive)
    }

    @Test
    fun `a RESPAWN death returns the actor to its respawn point at full health`() {
        val world = World()
        val actor = alive(x = 5.0, y = 7.0, maxHealth = 100.0)
        world.add(actor)
        actor.deathResponse = Alive.DeathResponse.RESPAWN
        actor.location.setTo(50.0, 50.0)
        actor.health.current = 0.0

        world.tick()

        assertEquals(5.0, actor.location.x, absoluteTolerance = 1e-9)
        assertEquals(7.0, actor.location.y, absoluteTolerance = 1e-9)
        assertEquals(actor.health.max.value, actor.health.current)
        assertTrue(actor in world.gameObjects)
    }

    @Test
    fun `a RESPAWN actor can die and come back more than once`() {
        val world = World()
        val actor = alive()
        world.add(actor)
        actor.deathResponse = Alive.DeathResponse.RESPAWN

        actor.health.current = 0.0
        world.tick()
        assertEquals(actor.health.max.value, actor.health.current)

        actor.health.current = 0.0
        world.tick()
        assertEquals(actor.health.max.value, actor.health.current)
    }

    @Test
    fun `die applies the response only once per death`() {
        val world = World()
        val actor = alive()
        world.add(actor)
        actor.deathResponse = Alive.DeathResponse.REMOVAL
        actor.health.current = 0.0

        actor.tick()
        actor.tick()

        assertEquals(1, world.removeList.count { it === actor })
    }
}
