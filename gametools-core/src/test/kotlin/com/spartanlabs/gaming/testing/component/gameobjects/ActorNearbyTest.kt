package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.spatial.Quadtree
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.UniformGrid
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Covers [Actor.nearby]'s two overloads - the original [Quadtree]-typed one and the
 * [com.spartanlabs.gaming.spatial.SpatialIndex]-typed one added for issue #63 - returning the
 * same result set for the same indexed objects, regardless of which [SpatialIndex]
 * implementation backs the query.
 */
class ActorNearbyTest {

    private fun visible(x: Double, y: Double) = VisibleObject(
        location = Point(x, y),
        dimensions = Dimensions(width = 4.0, height = 4.0)
    )

    @Test
    fun `the SpatialIndex overload finds the same objects as the Quadtree overload`() {
        val actor = Actor(location = Point(0.0, 0.0))
        val inRange = visible(5.0, 5.0)
        val outOfRange = visible(100.0, 100.0)

        val quadtree = Quadtree<Double, VisibleObject>()
        listOf(inRange, outOfRange).forEach { quadtree.insert(it.location.x, it.location.y, it) }

        val quadtreeResult = actor.nearby(quadtree, range = 10.0)
        val quadtreeIndexResult = actor.nearby(QuadtreeSpatialIndex(quadtree), range = 10.0)

        assertEquals(listOf(inRange), quadtreeResult)
        assertEquals(quadtreeResult.toSet(), quadtreeIndexResult.toSet())
    }

    @Test
    fun `a UniformGrid-backed query finds objects within range and excludes objects outside it`() {
        val actor = Actor(location = Point(0.0, 0.0))
        val inRange = visible(5.0, 5.0)
        val outOfRange = visible(100.0, 100.0)

        val grid = UniformGrid<VisibleObject>(cellSize = 10.0)
        listOf(inRange, outOfRange).forEach { grid.insert(it.location.x, it.location.y, it) }

        assertEquals(listOf(inRange), actor.nearby(grid, range = 10.0))
    }
}
