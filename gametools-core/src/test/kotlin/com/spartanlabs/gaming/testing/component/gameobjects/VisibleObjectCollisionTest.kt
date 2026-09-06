package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [VisibleObject.collidesWith] axis-aligned overlap testing. */
class VisibleObjectCollisionTest {

    private fun box(x: Double, y: Double, size: Double = 10.0) =
        VisibleObject(width = size, height = size, location = Point(x, y))

    @Test
    fun `overlapping boxes collide`() {
        assertTrue(box(0.0, 0.0).collidesWith(box(8.0, 0.0)))
    }

    @Test
    fun `boxes that only touch at the edge still collide`() {
        // centres 10 apart, summed half-extents 5 + 5 = 10
        assertTrue(box(0.0, 0.0).collidesWith(box(10.0, 0.0)))
    }

    @Test
    fun `boxes with a gap on either axis do not collide`() {
        assertFalse(box(0.0, 0.0).collidesWith(box(11.0, 0.0)))
        assertFalse(box(0.0, 0.0).collidesWith(box(0.0, 20.0)))
    }

    @Test
    fun `collision is symmetric`() {
        val a = box(0.0, 0.0)
        val b = box(9.0, 9.0)

        assertTrue(a.collidesWith(b))
        assertTrue(b.collidesWith(a))
    }

    @Test
    fun `zero-size objects collide only at the exact same point`() {
        assertTrue(box(3.0, 3.0, size = 0.0).collidesWith(box(3.0, 3.0, size = 0.0)))
        assertFalse(box(3.0, 3.0, size = 0.0).collidesWith(box(3.0, 4.0, size = 0.0)))
    }
}
