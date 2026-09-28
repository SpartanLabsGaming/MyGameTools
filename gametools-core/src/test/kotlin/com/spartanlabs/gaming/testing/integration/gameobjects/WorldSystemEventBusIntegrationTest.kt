package com.spartanlabs.gaming.testing.integration.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.event.EventBus
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Level 3 - integration between [WorldSystem] and the [World]'s real [EventBus] (no fake), the
 * system's only sanctioned integration point onto existing infrastructure. No sockets are
 * involved, but, as with `IntentSelfClearIntegrationTest`, this is where a system's subscription
 * lifecycle is exercised through real [World.add] / [World.tick] event publication rather than
 * direct method calls.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemEventBusIntegrationTest {

    /** A [WorldSystem] that records every [GameEvent] published on the [World] it is installed on, via a real [EventBus] subscription. */
    private class EventRecordingSystem : WorldSystem {
        val received = mutableListOf<GameEvent>()
        private var subscription: EventBus.Subscription? = null

        override fun installOn(world: World) {
            subscription = world.events.subscribe { received += it }
        }

        override fun uninstallFrom(world: World) {
            subscription?.cancel()
            subscription = null
        }
    }

    /** A [WorldSystem] that keys its subscriptions per-[World], per [WorldSystem]'s own multi-World contract. */
    private class MultiWorldEventRecordingSystem : WorldSystem {
        private val receivedByWorld = mutableMapOf<World, MutableList<GameEvent>>()

        override fun installOn(world: World) {
            val log = receivedByWorld.getOrPut(world) { mutableListOf() }
            world.events.subscribe { log += it }
        }

        fun receivedBy(world: World): List<GameEvent> = receivedByWorld[world] ?: emptyList()
    }

    private fun actor(x: Double) = Actor(location = Point(x, 0.0))

    @Test
    fun `a system that subscribes in installOn and cancels in uninstallFrom receives events only while installed`() {
        val world = World()
        val system = EventRecordingSystem()
        world.installSystem(system)

        val first = actor(0.0)
        world.add(first) // fires EntitySpawned
        world.removeList += first
        world.tick() // fires EntityRemoved
        val whileInstalled = listOf<GameEvent>(GameEvent.EntitySpawned(first), GameEvent.EntityRemoved(first))
        assertEquals(whileInstalled, system.received)

        world.uninstallSystem(system)

        val second = actor(10.0)
        world.add(second)
        world.removeList += second
        world.tick() // fires EntitySpawned then EntityRemoved for `second`, if still subscribed

        assertEquals(whileInstalled, system.received)
    }

    @Test
    fun `one WorldSystem instance installed on two different Worlds tracks each World's own events independently, keyed by the World instance`() {
        val worldA = World()
        val worldB = World()
        val system = MultiWorldEventRecordingSystem()
        worldA.installSystem(system)
        worldB.installSystem(system)

        val a = actor(0.0)
        val b = actor(1.0)
        worldA.add(a)
        worldB.add(b)

        assertEquals(listOf<GameEvent>(GameEvent.EntitySpawned(a)), system.receivedBy(worldA))
        assertEquals(listOf<GameEvent>(GameEvent.EntitySpawned(b)), system.receivedBy(worldB))
    }
}
