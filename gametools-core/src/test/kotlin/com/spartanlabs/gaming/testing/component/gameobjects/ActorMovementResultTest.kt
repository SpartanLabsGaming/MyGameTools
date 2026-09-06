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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Covers the [Result] contract of [Actor.isOneStepAway] and [Actor.move]: an
 * unmeasurable distance is carried as a failed [Result] rather than thrown.
 */
class ActorMovementResultTest {

    // speed defaults to ModularStat(10.0)
    private fun actorAt(x: Double, y: Double) = Actor(location = Point(x, y))

    @Test
    fun `isOneStepAway succeeds with true when the destination is within one step`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(3.0, 0.0)

        val result = actor.isOneStepAway

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `isOneStepAway succeeds with false when the destination is more than one step away`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(100.0, 0.0)

        val result = actor.isOneStepAway

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }

    @Test
    fun `isOneStepAway fails instead of throwing when a coordinate is NaN`() {
        val actor = actorAt(0.0, 0.0)
        actor.location.setTo(Double.NaN, 0.0)

        val result = actor.isOneStepAway

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `move snaps the actor onto the destination once it is within one step`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(4.0, 0.0)

        val result = actor.move()

        assertTrue(result.isSuccess)
        assertEquals(Point(4.0, 0.0), actor.location)
        assertTrue(actor.isAtDestination)
    }

    @Test
    fun `move advances the actor by its speed when the destination is far`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(100.0, 0.0)

        val result = actor.move()

        assertTrue(result.isSuccess)
        assertEquals(Point(10.0, 0.0), actor.location) // one step of length speed (10)
        assertFalse(actor.isAtDestination)
    }

    @Test
    fun `move is a successful no-op when the actor is already at its destination`() {
        val actor = actorAt(7.0, 7.0) // destination initialises to the location

        val result = actor.move()

        assertTrue(result.isSuccess)
        assertEquals(Point(7.0, 7.0), actor.location)
    }

    @Test
    fun `move fails and leaves the actor in place when the distance cannot be measured`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(50.0, 0.0)
        actor.location.setTo(Double.NaN, 0.0)

        val result = actor.move()

        assertTrue(result.isFailure)
        assertTrue(actor.location.x.isNaN(), "a failed move must not have moved the actor")
    }
}
