package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Move
import com.spartanlabs.gaming.gameobjects.Movement
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Covers [Move] directly, independent of [com.spartanlabs.gaming.networking.command.Stop]: what
 * [Move.issue] assigns, and what [Move.clear] resets (the §2.2.1 halt-in-place behavior).
 */
class MoveIntentTest {

    private fun actor(x: Double = 0.0, y: Double = 0.0) =
        Actor(location = Point(x, y), dimensions = Dimensions(2.0, 2.0))

    @Test
    fun `issue with a destination sets both movement and destination`() {
        val mover = actor()

        Move(Movement.Targeting, destination = Point(40.0, 50.0)).issue(mover)

        assertEquals(Movement.Targeting, mover.movement)
        assertEquals(40.0, mover.destination.x)
        assertEquals(50.0, mover.destination.y)
    }

    @Test
    fun `issue without a destination leaves destination untouched`() {
        val mover = actor().apply { destination = Point(7.0, 8.0) }

        Move(Movement.Directional).issue(mover)

        assertEquals(Movement.Directional, mover.movement)
        assertEquals(7.0, mover.destination.x)
        assertEquals(8.0, mover.destination.y)
    }

    @Test
    fun `clear resets movement to Targeting and destination to the actor's current location`() {
        val mover = actor(x = 12.0, y = 34.0).apply {
            movement = Movement.Directional
            destination = Point(999.0, 999.0)
        }

        Move(Movement.Directional).clear(mover)

        assertEquals(Movement.Targeting, mover.movement)
        assertEquals(12.0, mover.destination.x)
        assertEquals(34.0, mover.destination.y)
    }
}
