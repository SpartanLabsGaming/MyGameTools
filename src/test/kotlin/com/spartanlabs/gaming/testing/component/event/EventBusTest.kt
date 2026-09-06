package com.spartanlabs.gaming.testing.component.event

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.EventBus
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import com.spartanlabs.geometry.Point
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers [EventBus] delivery order, listener-fault isolation, re-entrant publishing, and unsubscribe. */
class EventBusTest {

    private fun anEvent(): GameEvent = GameEvent.EntitySpawned(Actor(location = Point(0.0, 0.0)))

    @Test
    fun `listeners are delivered to in subscription order`() {
        val bus = EventBus()
        val calls = mutableListOf<Int>()
        bus.subscribe { calls += 1 }
        bus.subscribe { calls += 2 }
        bus.subscribe { calls += 3 }

        bus.publish(anEvent())

        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test
    fun `a throwing listener does not stop the others or the publish`() {
        val bus = EventBus()
        val reached = mutableListOf<String>()
        bus.subscribe { reached += "first" }
        bus.subscribe { error("boom") }
        bus.subscribe { reached += "third" }

        bus.publish(anEvent()) // must not throw

        assertEquals(listOf("first", "third"), reached)
    }

    @Test
    fun `an event published from inside a listener is delivered after the current one`() {
        val bus = EventBus()
        val order = mutableListOf<String>()
        val second = GameEvent.EntityRemoved(Actor(location = Point(1.0, 1.0)))

        bus.subscribe { event ->
            when (event) {
                is GameEvent.EntitySpawned -> {
                    order += "spawn:start"
                    bus.publish(second)          // must not recurse
                    order += "spawn:end"
                }
                is GameEvent.EntityRemoved -> order += "remove"
                else -> {}
            }
        }

        bus.publish(anEvent())

        assertEquals(listOf("spawn:start", "spawn:end", "remove"), order)
    }

    @Test
    fun `a cancelled subscription receives nothing further`() {
        val bus = EventBus()
        var hits = 0
        val subscription = bus.subscribe { hits++ }

        bus.publish(anEvent())
        subscription.cancel()
        bus.publish(anEvent())

        assertEquals(1, hits)
    }

    @Test
    fun `cancel is idempotent`() {
        val bus = EventBus()
        val subscription = bus.subscribe { }
        subscription.cancel()
        subscription.cancel() // must not throw
        assertTrue(true)
    }
}
