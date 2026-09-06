package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. [VisibleObject.collidesWith] is a pure predicate over the
 * two objects' positions and sizes; this pins its mathematical properties (reflexivity,
 * symmetry, translation invariance, the exact contact boundary) across randomised inputs.
 * [com.spartanlabs.gaming.testing.component.gameobjects] covers the worked examples.
 */
class CollisionPredicateLawsTest {

    private val random = Random(902_2026)

    private fun randomBox(): VisibleObject = VisibleObject(
        width = random.nextDouble(0.0, 40.0),
        height = random.nextDouble(0.0, 40.0),
        x = random.nextDouble(-100.0, 100.0),
        y = random.nextDouble(-100.0, 100.0)
    )

    private fun VisibleObject.movedBy(dx: Double, dy: Double): VisibleObject =
        VisibleObject(width = dimensions.width, height = dimensions.height, x = location.x + dx, y = location.y + dy)

    @Test
    fun `every object collides with itself`() {
        repeat(200) {
            val box = randomBox()
            assertTrue(box.collidesWith(box))
        }
    }

    @Test
    fun `collision is symmetric`() {
        repeat(500) {
            val a = randomBox()
            val b = randomBox()
            assertEquals(a.collidesWith(b), b.collidesWith(a), "collidesWith disagreed by argument order")
        }
    }

    @Test
    fun `collision is invariant under translating both objects by the same vector`() {
        repeat(500) {
            val a = randomBox()
            val b = randomBox()
            val dx = random.nextDouble(-250.0, 250.0)
            val dy = random.nextDouble(-250.0, 250.0)
            assertEquals(
                a.collidesWith(b),
                a.movedBy(dx, dy).collidesWith(b.movedBy(dx, dy)),
                "translating both objects together changed the outcome"
            )
        }
    }

    @Test
    fun `the contact boundary sits at the summed half-extents on each axis`() {
        // A margin well above double rounding error (~1e-13 at these magnitudes) but far below
        // any box size, so the boundary is probed without depending on exact float equality.
        val margin = 1e-3
        repeat(200) {
            val a = randomBox()
            val bWidth = random.nextDouble(1.0, 40.0)
            val bHeight = random.nextDouble(1.0, 40.0)
            val gapX = (a.dimensions.width + bWidth) / 2.0
            val gapY = (a.dimensions.height + bHeight) / 2.0

            fun bAt(x: Double, y: Double) = VisibleObject(width = bWidth, height = bHeight, x = x, y = y)

            assertTrue(
                a.collidesWith(bAt(a.location.x + gapX - margin, a.location.y + gapY - margin)),
                "boxes just inside the summed half-extents should collide"
            )
            assertFalse(
                a.collidesWith(bAt(a.location.x + gapX + margin, a.location.y)),
                "a box just past the summed half-extents on x should not collide"
            )
            assertFalse(
                a.collidesWith(bAt(a.location.x, a.location.y + gapY + margin)),
                "a box just past the summed half-extents on y should not collide"
            )
        }
    }

    @Test
    fun `a box whose centre lies inside another always collides`() {
        repeat(300) {
            val outer = VisibleObject(width = random.nextDouble(10.0, 80.0), height = random.nextDouble(10.0, 80.0))
            val inner = VisibleObject(
                width = random.nextDouble(0.0, 5.0),
                height = random.nextDouble(0.0, 5.0),
                x = random.nextDouble(-outer.dimensions.width / 2.0, outer.dimensions.width / 2.0),
                y = random.nextDouble(-outer.dimensions.height / 2.0, outer.dimensions.height / 2.0)
            )
            assertTrue(outer.collidesWith(inner))
        }
    }

    @Test
    fun `the predicate is deterministic - the same pair always gives the same answer`() {
        repeat(100) {
            val a = randomBox()
            val b = randomBox()
            val first = a.collidesWith(b)
            repeat(5) { assertEquals(first, a.collidesWith(b)) }
        }
    }
}
