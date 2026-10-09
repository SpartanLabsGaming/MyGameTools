package com.spartanlabs.gaming.testing.integration.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
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
import kotlin.test.assertFailsWith
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
    private class EventRecordingSystem : AbstractWorldSystem() {
        val received = mutableListOf<GameEvent>()
        private var subscription: EventBus.Subscription? = null

        override fun onInstalled() {
            subscription = world.events.subscribe { received += it }
        }

        override fun onUninstalled() {
            subscription?.cancel()
            subscription = null
        }
    }

    private fun actor(x: Double) = Actor(location = Point(x, 0.0))

    @Test
    fun `a system that subscribes in onInstalled and cancels in onUninstalled receives events only while installed`() {
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
    fun `a WorldSystem instance installed on one World is rejected by a second World, and keeps receiving only its own World's events`() {
        val worldA = World()
        val worldB = World()
        val system = EventRecordingSystem()
        worldA.installSystem(system)

        assertFailsWith<IllegalArgumentException> { worldB.installSystem(system) }

        val b = actor(1.0)
        worldB.add(b) // not recorded: the system never subscribed to worldB
        val a = actor(0.0)
        worldA.add(a)

        assertEquals(listOf<GameEvent>(GameEvent.EntitySpawned(a)), system.received)
    }

    @Test
    fun `uninstall then re-install on the same World re-subscribes exactly once`() {
        val world = World()
        val system = EventRecordingSystem()
        world.installSystem(system)
        world.uninstallSystem(system)
        world.installSystem(system)

        val first = actor(0.0)
        world.add(first)

        // Delivered once: the binding survives uninstall, the subscription does not.
        assertEquals(listOf<GameEvent>(GameEvent.EntitySpawned(first)), system.received)
    }
}
