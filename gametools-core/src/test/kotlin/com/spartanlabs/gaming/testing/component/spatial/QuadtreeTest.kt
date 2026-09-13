package com.spartanlabs.gaming.testing.component.spatial

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers [Quadtree] insertion, half-open box retrieval, removal, and clearing. */
class QuadtreeTest {

    private fun populated() = Quadtree<Int, String>().apply {
        insert(0, 0, "origin")
        insert(1, 1, "ne")
        insert(-1, -1, "sw")
        insert(10, 10, "far")
    }

    @Test
    fun `retrieveBox returns points inside the half-open box`() {
        val found = populated().retrieveBox(-1, -1, 4, 4)

        // (minX, maxX] x (minY, maxY]: the origin and (1,1) qualify; (-1,-1) is on the excluded edge
        assertEquals(setOf("origin", "ne"), found.toSet())
    }

    @Test
    fun `a wide box retrieves every point`() {
        val found = populated().retrieveBox(-11, -11, 11, 11)

        assertEquals(setOf("origin", "ne", "sw", "far"), found.toSet())
    }

    @Test
    fun `an empty region retrieves nothing`() {
        assertTrue(populated().retrieveBox(100, 100, 200, 200).isEmpty())
    }

    @Test
    fun `remove drops an element from later retrievals`() {
        val tree = populated()

        tree.remove(0, 0, "origin")
        tree.remove(10, 10, "far")

        assertEquals(setOf("ne", "sw"), tree.retrieveBox(-11, -11, 11, 11).toSet())
    }

    @Test
    fun `insert after remove indexes the new element at its own position, not the dead slot's`() {
        val tree = populated()
        tree.remove(0, 0, "origin") // the root node's element is cleared, but its (x, y) stays (0, 0)

        tree.insert(5, 5, "reused") // must create a fresh node at (5, 5), not reuse the dead root node

        // found via a box that only matches its actual (5, 5) position
        assertTrue("reused" in tree.retrieveBox(4, 4, 6, 6))
        // not found via a box that only matches the dead slot's old (0, 0) position - the bug this
        // regression test locks in would have stored "reused" there instead
        assertTrue("reused" !in tree.retrieveBox(-1, -1, 0, 0))
    }

    @Test
    fun `clear empties the tree`() {
        val tree = populated()

        tree.clear()

        assertTrue(tree.retrieveBox(-100, -100, 100, 100).isEmpty())
    }
}
