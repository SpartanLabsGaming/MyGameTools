package com.spartanlabs.gaming.testing.component.spatial

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Covers [QuadtreeSpatialIndex]'s delegation to [Quadtree] for `queryBox`/`move`, and its own
 * position-tracking bookkeeping that makes `queryRadius` an accurate circle query.
 *
 * Deliberately has no dedicated half-open-box-edge or degenerate-query cases (unlike
 * `UniformGridTest`) - `queryBox` delegates straight to [Quadtree.retrieveBox] verbatim, and that
 * coverage already exists at the `Quadtree` level in `QuadtreeTest.kt`. The asymmetry with
 * `UniformGridTest` (which reimplements those semantics from scratch and so needs its own cases)
 * is intentional, not an oversight.
 */
class QuadtreeSpatialIndexTest {

    @Test
    fun `queryBox matches Quadtree retrieveBox verbatim for the same inputs`() {
        val points = listOf(0.0 to 0.0, 1.0 to 1.0, -1.0 to -1.0, 10.0 to 10.0)
        val tree = Quadtree<Double, String>()
        val index = QuadtreeSpatialIndex<String>()
        points.forEachIndexed { i, (x, y) ->
            tree.insert(x, y, "p$i")
            index.insert(x, y, "p$i")
        }

        assertEquals(
            tree.retrieveBox(-1.0, -1.0, 5.0, 5.0).toSet(),
            index.queryBox(-1.0, -1.0, 5.0, 5.0).toSet()
        )
    }

    @Test
    fun `move removes the element from its old position and indexes it at the new one`() {
        val index = QuadtreeSpatialIndex<String>()
        index.insert(0.0, 0.0, "a")

        index.move(0.0, 0.0, 10.0, 10.0, "a")

        assertTrue(index.queryBox(-1.0, -1.0, 1.0, 1.0).isEmpty(), "the old position should no longer match")
        assertEquals(listOf("a"), index.queryBox(9.0, 9.0, 11.0, 11.0))
    }

    @Test
    fun `queryRadius filters out a candidate in the bounding box but outside the true circle`() {
        val index = QuadtreeSpatialIndex<String>()
        // (3,3) sits inside the axis-aligned bounding box of a radius-3 circle at the origin
        // (the box spans -3..3 on each axis) but at distance sqrt(18) =~ 4.24, outside the
        // true circle - exactly the case retrieveBox alone cannot filter accurately.
        index.insert(3.0, 3.0, "corner")
        index.insert(1.0, 0.0, "inCircle")

        val found = index.queryRadius(0.0, 0.0, 3.0)

        assertEquals(listOf("inCircle"), found)
    }

    @Test
    fun `clear empties both the tree and the tracked positions`() {
        val index = QuadtreeSpatialIndex<String>()
        index.insert(1.0, 1.0, "a")

        index.clear()

        assertTrue(index.queryBox(-10.0, -10.0, 10.0, 10.0).isEmpty())
        assertTrue(index.queryRadius(0.0, 0.0, 100.0).isEmpty())
    }

    @Test
    fun `re-inserting the same element after clear tracks its new position, not a stale one`() {
        val index = QuadtreeSpatialIndex<String>()
        index.insert(0.0, 0.0, "a")

        index.clear()
        index.insert(50.0, 50.0, "a")

        assertEquals(listOf("a"), index.queryRadius(50.0, 50.0, 1.0))
        assertTrue(index.queryRadius(0.0, 0.0, 1.0).isEmpty())
    }
}
