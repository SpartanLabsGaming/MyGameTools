package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [World] rebuilding its quadtree from, and then ticking, its owned [GameObject]s. */
class WorldTest {

    /** A non-visible [GameObject] that records how many times it has been ticked. */
    private class TickCounter : GameObject() {
        var ticks = 0
            private set

        public override fun onUpdate() {
            ticks++
        }
    }

    /** A non-visible [GameObject] that adds a fresh [TickCounter] to [world] on every tick. */
    private class Spawner(private val world: World) : GameObject() {
        public override fun onUpdate() {
            world.gameObjects += TickCounter()
        }
    }

    /** An actor at ([x], [y]) that travels +x at [Actor.speed] (10) units per tick. */
    private fun directionalActor(x: Double, y: Double) = Actor(location = Point(x, y)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `a new world owns nothing`() {
        assertTrue(World().gameObjects.isEmpty())
    }

    @Test
    fun `tick advances every owned object`() {
        val world = World()
        val actor = directionalActor(0.0, 0.0)
        val counter = TickCounter()
        world.gameObjects += listOf(actor, counter)

        world.tick()

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
        assertEquals(1, counter.ticks)
    }

    @Test
    fun `tick indexes the visible objects into the quadtree`() {
        val world = World()
        val actor = directionalActor(5.0, 5.0)
        world.gameObjects += actor

        world.tick()

        assertEquals(listOf(actor), world.quadtree.retrieveBox(4.0, 4.0, 6.0, 6.0))
    }

    @Test
    fun `the quadtree is rebuilt from the positions held at the start of the tick`() {
        val world = World()
        val actor = directionalActor(0.0, 0.0)
        world.gameObjects += actor

        world.tick() // indexes (0,0), then the actor moves to (10,0)

        assertTrue(actor in world.quadtree.retrieveBox(-1.0, -1.0, 1.0, 1.0))
        assertFalse(actor in world.quadtree.retrieveBox(9.0, -1.0, 11.0, 1.0))

        world.tick() // now indexes (10,0)

        assertTrue(actor in world.quadtree.retrieveBox(9.0, -1.0, 11.0, 1.0))
    }

    @Test
    fun `a stale entry from a previous tick does not linger in the quadtree`() {
        val world = World()
        val actor = directionalActor(0.0, 0.0)
        world.gameObjects += actor

        world.tick()
        world.tick() // actor is now at (10,0); the (0,0) region must be clear

        assertTrue(world.quadtree.retrieveBox(-1.0, -1.0, 1.0, 1.0).isEmpty())
    }

    @Test
    fun `non-visible objects are ticked but left out of the quadtree`() {
        val world = World()
        val counter = TickCounter()
        world.gameObjects += counter

        world.tick()

        assertEquals(1, counter.ticks)
        assertTrue(world.quadtree.retrieveBox(-1e6, -1e6, 1e6, 1e6).isEmpty())
    }

    @Test
    fun `an inactive object is not ticked`() {
        val world = World()
        val actor = directionalActor(0.0, 0.0).apply { active = false }
        world.gameObjects += actor

        world.tick()

        assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an object added during a tick is not ticked until the next tick`() {
        val world = World()
        world.gameObjects += Spawner(world)

        world.tick() // the spawner adds a TickCounter mid-pass; must not fail or tick it yet
        val spawned = world.gameObjects.filterIsInstance<TickCounter>().single()
        assertEquals(0, spawned.ticks)

        world.tick()
        assertEquals(1, spawned.ticks)
    }

    @Test
    fun `objects queued in removeList are ticked this pass then dropped`() {
        val world = World()
        val keep = TickCounter()
        val drop = TickCounter()
        world.gameObjects += listOf(keep, drop)
        world.removeList += drop

        world.tick()

        assertEquals(listOf<GameObject>(keep), world.gameObjects)
        assertTrue(world.removeList.isEmpty())
        assertEquals(1, drop.ticks) // still ticked before removal
    }
}
