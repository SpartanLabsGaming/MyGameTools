package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. Regression guard for acceptance criterion 2 of issue #46: [World.space]
 * defaults to `null`, an assignment is retained, and a [World] with [World.space] set ticks and
 * adds objects identically to one that leaves it `null` - [World.tick] does not consult [space]
 * at all.
 */
class WorldSpaceTest {

    /** A trivial, fixed-bounds [Space] stub - [World] never calls into it; it only needs to exist. */
    private object StubSpace : Space {
        override val bounds: Square = Square(Point(0.0, 0.0), Dimensions(100.0, 100.0))
        override fun contains(point: Point): Boolean = true
        override fun isWalkable(point: Point): Boolean = true
    }

    /** An actor at ([x], [y]) that travels +x at [Actor.speed] (10) units per tick. */
    private fun directionalActor(x: Double, y: Double) = Actor(location = Point(x, y)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `space defaults to null`() {
        assertNull(World().space)
    }

    @Test
    fun `assigning a Space is retained by the getter`() {
        val world = World()

        world.space = StubSpace

        assertSame(StubSpace, world.space)
    }

    @Test
    fun `a World with space set ticks and adds objects identically to one with space left null`() {
        val withoutSpace = World(seed = 12345L)
        val withSpace = World(seed = 12345L).apply { space = StubSpace }

        val moverWithoutSpace = directionalActor(0.0, 0.0)
        val moverWithSpace = directionalActor(0.0, 0.0)
        withoutSpace.add(moverWithoutSpace)
        withSpace.add(moverWithSpace)

        repeat(3) {
            withoutSpace.tick()
            withSpace.tick()
        }

        assertEquals(withoutSpace.tickCount, withSpace.tickCount)
        assertEquals(withoutSpace.gameObjects.size, withSpace.gameObjects.size)
        assertEquals(moverWithoutSpace.location.x, moverWithSpace.location.x, absoluteTolerance = 1e-9)
        assertEquals(moverWithoutSpace.location.y, moverWithSpace.location.y, absoluteTolerance = 1e-9)
        assertEquals(moverWithoutSpace.entityId, moverWithSpace.entityId)
        assertSame(moverWithoutSpace, withoutSpace.byId(moverWithoutSpace.entityId))
        assertSame(moverWithSpace, withSpace.byId(moverWithSpace.entityId))
        assertTrue(withSpace.spatialIndex.queryBox(-1.0, -1.0, 100.0, 100.0).contains(moverWithSpace))
        assertEquals(
            withoutSpace.spatialIndex.queryBox(-1.0, -1.0, 100.0, 100.0).size,
            withSpace.spatialIndex.queryBox(-1.0, -1.0, 100.0, 100.0).size,
        )
    }
}
