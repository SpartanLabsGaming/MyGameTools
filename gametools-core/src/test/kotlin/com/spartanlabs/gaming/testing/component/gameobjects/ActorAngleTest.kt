package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers how [Actor.destination]'s setter re-aims [Actor.angle] at the new target. */
class ActorAngleTest {

    private fun actorAt(x: Double, y: Double) = Actor(location = Point(x, y))

    @Test
    fun `a destination due east leaves the actor facing 0 degrees`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(10.0, 0.0)
        assertEquals(0, actor.angle)
    }

    @Test
    fun `a destination due north faces the actor at 90 degrees`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(0.0, 10.0)
        assertEquals(90, actor.angle)
    }

    @Test
    fun `a destination due west faces the actor at 180 degrees`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(-10.0, 0.0)
        assertEquals(180, actor.angle)
    }

    @Test
    fun `a destination due south normalises to 270 rather than -90 degrees`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(0.0, -10.0)
        assertEquals(270, actor.angle)
    }

    @Test
    fun `a diagonal destination faces the actor at 45 degrees`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(7.0, 7.0)
        assertEquals(45, actor.angle)
    }

    @Test
    fun `the heading is measured from the actor's current position, not the origin`() {
        val actor = actorAt(5.0, 5.0)
        actor.destination = Point(5.0, 15.0)
        assertEquals(90, actor.angle)
    }

    @Test
    fun `every heading normalises into the 0 until 360 range`() {
        val actor = actorAt(0.0, 0.0)
        for (degrees in 0 until 360) {
            val radians = Math.toRadians(degrees.toDouble())
            actor.destination = Point(Math.cos(radians) * 50, Math.sin(radians) * 50)
            assertTrue(actor.angle in 0..359, "heading $degrees produced ${actor.angle}")
        }
    }

    @Test
    fun `a destination equal to the current position leaves the angle unchanged`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(0.0, 10.0)   // face north
        actor.destination = Point(0.0, 0.0)    // "arrive" back at the origin
        assertEquals(90, actor.angle)
    }

    @Test
    fun `a destination with a NaN coordinate leaves the angle unchanged but is still stored`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(0.0, 10.0)
        actor.destination = Point(Double.NaN, 0.0)
        assertEquals(90, actor.angle)
        assertTrue(actor.destination.x.isNaN(), "the destination itself should still be recorded")
    }
}
