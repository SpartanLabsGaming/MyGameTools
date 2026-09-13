package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.StaticGeometry
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [StaticGeometry.blocksPoint] against a single obstacle, multiple
 * obstacles, an obstacle's own edge, and the empty-obstacle-list case.
 */
class StaticGeometryTest {

    private val obstacle = CenteredBox(center = Point(10.0, 10.0), halfExtents = Dimensions(5.0, 5.0))

    @Test
    fun `blocksPoint is true for a point inside a single obstacle`() {
        val geometry = StaticGeometry(listOf(obstacle))

        assertTrue(geometry.blocksPoint(Point(10.0, 10.0)))
    }

    @Test
    fun `blocksPoint is false for a point outside every obstacle`() {
        val geometry = StaticGeometry(listOf(obstacle))

        assertFalse(geometry.blocksPoint(Point(100.0, 100.0)))
    }

    @Test
    fun `blocksPoint is true for a point exactly on an obstacle's edge`() {
        val geometry = StaticGeometry(listOf(obstacle))

        assertTrue(geometry.blocksPoint(Point(15.0, 10.0)), "the max-x edge should count as blocked")
        assertTrue(geometry.blocksPoint(Point(5.0, 10.0)), "the min-x edge should count as blocked")
    }

    @Test
    fun `blocksPoint checks across multiple obstacles`() {
        val second = CenteredBox(center = Point(50.0, 50.0), halfExtents = Dimensions(2.0, 2.0))
        val geometry = StaticGeometry(listOf(obstacle, second))

        assertTrue(geometry.blocksPoint(Point(50.0, 50.0)))
        assertTrue(geometry.blocksPoint(Point(10.0, 10.0)))
        assertFalse(geometry.blocksPoint(Point(0.0, 0.0)))
    }

    @Test
    fun `an empty obstacle list never blocks`() {
        val geometry = StaticGeometry(emptyList())

        assertFalse(geometry.blocksPoint(Point(10.0, 10.0)))
        assertFalse(geometry.blocksPoint(Point(0.0, 0.0)))
    }
}
