package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.Movement
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [GameObject.active] gating [GameObject.tick]. */
class GameObjectActiveTest {

    /** An actor at the origin that travels +x at [Actor.speed] (10) units per tick. */
    private fun movingActor(): Actor = Actor(location = Point(0.0, 0.0)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `a game object is active by default`() {
        assertTrue(Actor().active)
    }

    @Test
    fun `an active object ticks`() {
        val actor = movingActor()

        actor.tick()

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an inactive object does not tick`() {
        val actor = movingActor()
        actor.active = false

        repeat(5) { actor.tick() }

        assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `reactivating an object resumes ticking`() {
        val actor = movingActor()
        actor.active = false
        actor.tick()
        assertEquals(0.0, actor.location.x, absoluteTolerance = 1e-9)

        actor.active = true
        actor.tick()

        assertEquals(10.0, actor.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an inactive Alive does not update its health bar`() {
        val actor = Alive(
            location = Point(0.0, 0.0),
            dimensions = Dimensions(width = 20.0, height = 10.0),
            maxHealth = 100.0
        )
        actor.active = false
        actor.health.current = 40.0

        actor.tick()

        assertEquals(20.0, actor.healthBar.dimensions.width) // untouched full width
        assertFalse(actor.active)
    }
}
