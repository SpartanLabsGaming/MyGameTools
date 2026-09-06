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
    fun `a removed slot is reused by the next insert at that node`() {
        val tree = populated()
        tree.remove(0, 0, "origin")

        tree.insert(5, 5, "reused") // root node is empty, so this lands there

        assertTrue("reused" in tree.retrieveBox(-11, -11, 11, 11))
    }

    @Test
    fun `clear empties the tree`() {
        val tree = populated()

        tree.clear()

        assertTrue(tree.retrieveBox(-100, -100, 100, 100).isEmpty())
    }
}
