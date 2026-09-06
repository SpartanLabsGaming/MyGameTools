package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers [World] publishing [GameEvent.EntitySpawned] / [GameEvent.EntityRemoved] as objects join and leave. */
class WorldLifecycleEventsTest {

    private fun recorder(world: World): MutableList<GameEvent> =
        mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }

    private fun actor(x: Double = 0.0) = Actor(location = Point(x, 0.0))

    @Test
    fun `add fires EntitySpawned immediately, once`() {
        val world = World()
        val events = recorder(world)
        val a = actor()

        world.add(a)
        world.tick() // the reindex must not re-announce

        assertContentEquals(listOf(GameEvent.EntitySpawned(a)), events)
    }

    @Test
    fun `an object added by mutating the list is announced on the next tick`() {
        val world = World()
        val events = recorder(world)
        val a = actor()

        world.gameObjects += a
        assertTrue(events.isEmpty())

        world.tick()

        assertContentEquals(listOf(GameEvent.EntitySpawned(a)), events)
    }

    @Test
    fun `a removed object fires EntityRemoved after the tick that drops it`() {
        val world = World()
        val a = actor()
        world.add(a)
        val events = recorder(world)

        world.removeList += a
        world.tick()

        assertContentEquals(listOf(GameEvent.EntityRemoved(a)), events)
    }

    @Test
    fun `re-adding a removed instance announces it again`() {
        val world = World()
        val a = actor()
        world.add(a)
        world.removeList += a
        world.tick()
        val events = recorder(world)

        world.add(a)

        assertContentEquals(listOf(GameEvent.EntitySpawned(a)), events)
    }

    @Test
    fun `spawn events during a tick reindex follow gameObjects order`() {
        val world = World()
        val a = actor(1.0)
        val b = actor(2.0)
        val c = actor(3.0)
        world.gameObjects += listOf(a, b, c)
        val spawned = mutableListOf<GameObject>()
        world.events.subscribe { if (it is GameEvent.EntitySpawned) spawned += it.entity }

        world.tick()

        assertContentEquals(listOf(a, b, c), spawned)
    }

    @Test
    fun `a listener that throws does not break the tick`() {
        val world = World()
        world.events.subscribe { error("listener blew up") }

        world.add(actor())
        world.tick() // must complete

        assertEquals(1, world.gameObjects.size)
    }
}
