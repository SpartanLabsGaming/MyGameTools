package com.spartanlabs.gaming.testing.component.spatial

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.UniformGrid
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Covers [UniformGrid] insertion, half-open box queries, Euclidean-circle queries, `move`
 * within and across cells, `remove`, multiple elements sharing one cell, and `clear`.
 */
class UniformGridTest {

    private data class P(val id: Int, val x: Double, val y: Double)

    private val points = listOf(
        P(0, 0.0, 0.0),
        P(1, 5.0, 5.0),
        P(2, -5.0, -5.0),
        P(3, 12.0, 3.0),
        P(4, 3.0, 12.0),
        P(5, -20.0, 20.0)
    )

    private fun gridOf(points: List<P>, cellSize: Double = 10.0): UniformGrid<P> =
        UniformGrid<P>(cellSize).apply { points.forEach { insert(it.x, it.y, it) } }

    private fun List<P>.inBox(minX: Double, minY: Double, maxX: Double, maxY: Double): Set<P> =
        filter { it.x > minX && it.x <= maxX && it.y > minY && it.y <= maxY }.toSet()

    private fun List<P>.inRadius(x: Double, y: Double, radius: Double): Set<P> =
        filter {
            val dx = it.x - x
            val dy = it.y - y
            dx * dx + dy * dy <= radius * radius
        }.toSet()

    @Test
    fun `queryBox matches a brute-force scan`() {
        val grid = gridOf(points)

        assertEquals(points.inBox(-6.0, -6.0, 6.0, 6.0), grid.queryBox(-6.0, -6.0, 6.0, 6.0).toSet())
        assertEquals(points.inBox(-100.0, -100.0, 100.0, 100.0), grid.queryBox(-100.0, -100.0, 100.0, 100.0).toSet())
        assertEquals(points.inBox(100.0, 100.0, 200.0, 200.0), grid.queryBox(100.0, 100.0, 200.0, 200.0).toSet())
    }

    @Test
    fun `queryRadius matches a brute-force scan using a true Euclidean circle`() {
        val grid = gridOf(points)

        assertEquals(points.inRadius(0.0, 0.0, 8.0), grid.queryRadius(0.0, 0.0, 8.0).toSet())
        assertEquals(points.inRadius(0.0, 0.0, 100.0), grid.queryRadius(0.0, 0.0, 100.0).toSet())
        assertEquals(points.inRadius(50.0, 50.0, 5.0), grid.queryRadius(50.0, 50.0, 5.0).toSet())
    }

    @Test
    fun `the half-open box edge convention includes maxX and excludes minX`() {
        val grid = UniformGrid<String>(10.0).apply {
            insert(4.0, 2.0, "onMaxXEdge")
            insert(0.0, 2.0, "onMinXEdge")
        }

        val found = grid.queryBox(0.0, 0.0, 4.0, 4.0)

        assertTrue("onMaxXEdge" in found, "an element exactly on the maxX edge should be included")
        assertTrue("onMinXEdge" !in found, "an element exactly on the minX edge should be excluded")
    }

    @Test
    fun `a degenerate minX greater than maxX query returns empty rather than throwing`() {
        val grid = gridOf(points)

        assertTrue(grid.queryBox(10.0, -10.0, -10.0, 10.0).isEmpty())
    }

    @Test
    fun `move within the same cell relocates the element without leaving a duplicate`() {
        val grid = UniformGrid<String>(10.0).apply { insert(1.0, 1.0, "a") }

        grid.move(1.0, 1.0, 2.0, 2.0, "a")

        assertEquals(listOf("a"), grid.queryBox(-10.0, -10.0, 10.0, 10.0))
        assertTrue(grid.queryBox(0.5, 0.5, 1.5, 1.5).isEmpty(), "the old position should no longer match")
        assertEquals(listOf("a"), grid.queryBox(1.5, 1.5, 2.5, 2.5))
    }

    @Test
    fun `move across cells relocates the element`() {
        val grid = UniformGrid<String>(10.0).apply { insert(1.0, 1.0, "a") }

        grid.move(1.0, 1.0, 25.0, 25.0, "a")

        assertTrue(grid.queryBox(0.0, 0.0, 10.0, 10.0).isEmpty())
        assertEquals(listOf("a"), grid.queryBox(20.0, 20.0, 30.0, 30.0))
    }

    @Test
    fun `remove drops a present element`() {
        val grid = UniformGrid<String>(10.0).apply {
            insert(1.0, 1.0, "a")
            insert(2.0, 2.0, "b")
        }

        grid.remove(1.0, 1.0, "a")

        assertEquals(listOf("b"), grid.queryBox(-10.0, -10.0, 10.0, 10.0))
    }

    @Test
    fun `remove of an absent element is a no-op`() {
        val grid = UniformGrid<String>(10.0).apply { insert(1.0, 1.0, "a") }

        grid.remove(1.0, 1.0, "not-there") // wrong element at a real cell
        grid.remove(99.0, 99.0, "a") // right element, wrong (empty) cell

        assertEquals(listOf("a"), grid.queryBox(-10.0, -10.0, 10.0, 10.0))
    }

    @Test
    fun `multiple elements sharing one cell are all retrievable and independently removable`() {
        val grid = UniformGrid<String>(10.0).apply {
            insert(1.0, 1.0, "a")
            insert(2.0, 2.0, "b")
            insert(3.0, 3.0, "c")
        }

        assertEquals(setOf("a", "b", "c"), grid.queryBox(0.0, 0.0, 5.0, 5.0).toSet())

        grid.remove(2.0, 2.0, "b")

        assertEquals(setOf("a", "c"), grid.queryBox(0.0, 0.0, 5.0, 5.0).toSet())
    }

    @Test
    fun `clear empties the grid`() {
        val grid = gridOf(points)

        grid.clear()

        assertTrue(grid.queryBox(-100.0, -100.0, 100.0, 100.0).isEmpty())
    }
}
