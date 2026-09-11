package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.ActorSnapshot
import com.spartanlabs.gaming.gameobjects.Idle
import com.spartanlabs.gaming.gameobjects.Intent
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
//endregion

/**
 * Covers [Actor.issue] / [Actor.clearIntent]: the outgoing intent is always [Intent.clear]ed
 * before the next is installed and [Intent.issue]d, [GameEvent.IntentIssued] /
 * [GameEvent.IntentCleared] are published as documented, and a fresh [Actor] starts [Idle].
 */
class IntentTest {

    /** An [Intent] that records, into a shared [log], the order [issue] / [clear] fire in. */
    private class TrackingIntent(override val label: String, private val log: MutableList<String>) : Intent() {
        override fun issue(actor: Actor) { log += "$label.issue" }
        override fun clear(actor: Actor) { log += "$label.clear" }
    }

    private fun actor() = Actor(location = Point(0.0, 0.0), dimensions = Dimensions(2.0, 2.0))

    private fun world() = World(seed = 1L)

    @Test
    fun `issue runs the previous intent's clear, then installs and issues the next`() {
        val mover = actor()
        val calls = mutableListOf<String>()
        val first = TrackingIntent("first", calls)
        val second = TrackingIntent("second", calls)
        mover.issue(first)
        calls.clear()

        mover.issue(second)

        assertEquals(listOf("first.clear", "second.issue"), calls)
        assertSame(second, mover.intent)
    }

    @Test
    fun `clearIntent returns intent to Idle and publishes IntentCleared with the previous intent`() {
        val world = world()
        val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }
        val mover = actor().also(world::add)
        val order = TrackingIntent("order", mutableListOf())
        mover.issue(order)
        events.clear()

        mover.clearIntent()

        assertSame(Idle, mover.intent)
        val cleared = assertIs<GameEvent.IntentCleared>(events.single())
        assertSame(mover, cleared.actor)
        assertSame(order, cleared.previous)
    }

    @Test
    fun `issuing a non-Idle intent publishes IntentIssued carrying that intent`() {
        val world = world()
        val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }
        val mover = actor().also(world::add)
        val order = TrackingIntent("order", mutableListOf())
        events.clear()

        mover.issue(order)

        val issued = assertIs<GameEvent.IntentIssued>(events.single())
        assertSame(mover, issued.actor)
        assertSame(order, issued.intent)
    }

    @Test
    fun `issuing the same intent type twice still runs clear then issue both times`() {
        val mover = actor()
        val calls = mutableListOf<String>()
        val first = TrackingIntent("repeat", calls)
        val second = TrackingIntent("repeat", calls)

        mover.issue(first)
        mover.issue(second)

        // Idle.clear (no-op, unrecorded) -> first.issue, then first.clear -> second.issue:
        // no short-circuit just because both intents share a label/type.
        assertEquals(listOf("repeat.issue", "repeat.clear", "repeat.issue"), calls)
    }

    @Test
    fun `a freshly constructed actor starts Idle and its snapshot reports idle`() {
        val mover = actor()

        assertSame(Idle, mover.intent)
        assertEquals("idle", (ActorSnapshot from mover).intent)
    }
}
