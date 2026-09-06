package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Movement
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers the four [Movement] strategies an [Actor] can run under. */
class ActorMovementTypeTest {

    // speed defaults to ModularStat(10.0)
    private fun actorAt(x: Double, y: Double) = Actor(location = Point(x, y))

    @Test
    fun `Targeting stops pursuing once it has arrived even after being pushed away`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(25.0, 0.0)
        repeat(5) { actor.tick() } // 5 steps of 10 units reaches and snaps onto (25,0)
        assertTrue(actor.isAtDestination)

        actor.location.setTo(0.0, 0.0) // shove it back to the origin
        actor.tick()

        assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `Persistent resumes the approach after being pushed away`() {
        val actor = actorAt(0.0, 0.0)
        actor.movement = Movement.Persistent
        actor.destination = Point(25.0, 0.0)
        repeat(5) { actor.tick() }
        assertTrue(actor.isAtDestination)

        actor.location.setTo(0.0, 0.0)
        actor.tick()

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9) // stepped back toward it
    }

    @Test
    fun `Directional travels along the actor's angle and ignores the destination`() {
        val actor = actorAt(0.0, 0.0)
        actor.destination = Point(0.0, 100.0) // would face 90 degrees
        actor.movement = Movement.Directional
        actor.angle = 0                        // Directional follows this, not the destination

        actor.tick()

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, actor.location.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun `Directional keeps going tick after tick`() {
        val actor = actorAt(0.0, 0.0)
        actor.movement = Movement.Directional
        actor.angle = 90

        repeat(3) { actor.tick() }

        assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-6)
        assertEquals(30.0, actor.location.y, absoluteTolerance = 1e-6)
    }

    @Test
    fun `Homing chases a target that moves`() {
        val target = actorAt(100.0, 0.0)
        val actor = actorAt(0.0, 0.0)
        actor.movement = Movement.Homing(target)

        actor.tick()
        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)

        target.location.setTo(10.0, 0.0) // target retreats to just behind the actor's next step
        repeat(2) { actor.tick() }

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, actor.location.y, absoluteTolerance = 1e-9)
        assertTrue(actor.isAtDestination)
    }
}
