package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/** Covers [World] numbering the objects it owns and resolving them through [World.byId]. */
class WorldEntityRegistryTest {

    private fun actor(x: Double = 0.0, y: Double = 0.0) = Actor(location = Point(x, y))

    /** A non-visible object that adds one [VisibleObject] to [world] the first time it ticks. */
    private class Spawner(private val world: World) : GameObject() {
        var spawned: VisibleObject? = null
            private set

        public override fun onUpdate() {
            if (spawned == null) VisibleObject(width = 2.0, height = 2.0).also {
                spawned = it
                world.gameObjects += it
            }
        }
    }

    @Test
    fun `add numbers an object and indexes it immediately`() {
        val world = World()
        val a = actor()

        world.add(a)

        assertNotEquals(EntityId.UNASSIGNED, a.entityId)
        assertSame(a, world.byId(a.entityId))
    }

    @Test
    fun `ids are assigned from one in acquisition order`() {
        val world = World()
        val first = actor()
        val second = actor()
        val third = actor()

        listOf(first, second, third).forEach(world::add)

        assertEquals(EntityId(1L), first.entityId)
        assertEquals(EntityId(2L), second.entityId)
        assertEquals(EntityId(3L), third.entityId)
    }

    @Test
    fun `an object added by mutating gameObjects directly is numbered on the next tick`() {
        val world = World()
        val a = actor()

        world.gameObjects += a
        assertEquals(EntityId.UNASSIGNED, a.entityId, "not numbered until a tick sees it")
        assertNull(world.byId(EntityId(1L)))

        world.tick()

        assertNotEquals(EntityId.UNASSIGNED, a.entityId)
        assertSame(a, world.byId(a.entityId))
    }

    @Test
    fun `a standalone object never added to a world stays UNASSIGNED`() {
        assertEquals(EntityId.UNASSIGNED, actor().entityId)
    }

    @Test
    fun `two worlds number their own objects independently`() {
        val one = World()
        val two = World()

        val a = actor().also(one::add)
        val b = actor().also(two::add)

        assertEquals(EntityId(1L), a.entityId)
        assertEquals(EntityId(1L), b.entityId)
        assertSame(a, one.byId(EntityId(1L)))
        assertSame(b, two.byId(EntityId(1L)))
        assertNotSame(a, two.byId(a.entityId))
    }

    @Test
    fun `byId stops resolving an object once it leaves the world`() {
        val world = World()
        val a = actor().also(world::add)
        val id = a.entityId

        world.removeList += a
        world.tick()

        assertNull(world.byId(id))
    }

    @Test
    fun `a removed instance re-added later keeps its original id`() {
        val world = World()
        val a = actor().also(world::add)
        val id = a.entityId

        world.removeList += a
        world.tick()
        world.add(a)

        assertEquals(id, a.entityId)
        assertSame(a, world.byId(id))
    }

    @Test
    fun `an Alive's sub-objects are numbered too`() {
        val world = World()
        val unit = Alive(location = Point(0.0, 0.0), dimensions = Dimensions(10.0, 10.0), maxHealth = 30.0)

        world.add(unit)

        val healthBar = unit.subObjects.single()
        assertNotEquals(EntityId.UNASSIGNED, healthBar.entityId)
        assertSame(healthBar, world.byId(healthBar.entityId))
        assertNotEquals(unit.entityId, healthBar.entityId)
    }

    @Test
    fun `an object added mid-tick is resolvable from the following tick`() {
        val world = World()
        val spawner = Spawner(world)
        world.add(spawner)

        world.tick() // spawner adds a VisibleObject mid-pass
        val spawned = spawner.spawned!!
        assertEquals(EntityId.UNASSIGNED, spawned.entityId, "not numbered until the next rebuild")

        world.tick() // next rebuild numbers and indexes it
        assertNotEquals(EntityId.UNASSIGNED, spawned.entityId)
        assertSame(spawned, world.byId(spawned.entityId))
    }
}
