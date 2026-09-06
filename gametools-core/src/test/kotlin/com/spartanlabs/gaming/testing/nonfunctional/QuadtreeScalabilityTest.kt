package com.spartanlabs.gaming.testing.nonfunctional

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
import kotlin.system.measureNanoTime
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional validation for [Quadtree]: it stays correct and sub-linear as the
 * element count grows, tolerates a pathological insertion order, and resets cleanly at scale.
 * Correctness for small inputs is covered in
 * [com.spartanlabs.gaming.testing.component.spatial].
 */
class QuadtreeScalabilityTest {

    /** [id] keeps every generated element distinct even if two happen to share coordinates. */
    private data class P(val id: Int, val x: Double, val y: Double)

    private val random = Random(4_2026)

    private fun randomPoints(count: Int): List<P> =
        List(count) { P(it, random.nextDouble(-10_000.0, 10_000.0), random.nextDouble(-10_000.0, 10_000.0)) }

    private fun treeOf(points: List<P>): Quadtree<Double, P> =
        Quadtree<Double, P>().apply { points.forEach { insert(it.x, it.y, it) } }

    /** Brute-force equivalent of [Quadtree.retrieveBox]'s half-open `(minX, maxX] x (minY, maxY]` box. */
    private fun List<P>.inBox(minX: Double, minY: Double, maxX: Double, maxY: Double): Set<P> =
        filter { it.x > minX && it.x <= maxX && it.y > minY && it.y <= maxY }.toSet()

    @Test
    fun `retrieveBox matches a brute-force scan for many random windows at scale`() {
        val points = randomPoints(20_000)
        val tree = treeOf(points)

        repeat(250) {
            val minX = random.nextDouble(-10_000.0, 9_000.0)
            val minY = random.nextDouble(-10_000.0, 9_000.0)
            val maxX = minX + random.nextDouble(0.0, 4_000.0)
            val maxY = minY + random.nextDouble(0.0, 4_000.0)

            assertEquals(
                points.inBox(minX, minY, maxX, maxY),
                tree.retrieveBox(minX, minY, maxX, maxY).toSet(),
                "quadtree window ($minX,$minY)..($maxX,$maxY) disagreed with a linear scan"
            )
        }
    }

    @Test
    fun `a windowed query is markedly cheaper than a linear scan over the same elements`() {
        val points = randomPoints(20_000)
        val tree = treeOf(points)

        val windows = List(4_000) {
            val cx = random.nextDouble(-9_000.0, 9_000.0)
            val cy = random.nextDouble(-9_000.0, 9_000.0)
            doubleArrayOf(cx - 100.0, cy - 100.0, cx + 100.0, cy + 100.0)
        }

        // touch both paths once so the JIT has seen them before timing
        windows.take(200).forEach { w ->
            tree.retrieveBox(w[0], w[1], w[2], w[3])
            points.count { it.x > w[0] && it.x <= w[2] && it.y > w[1] && it.y <= w[3] }
        }

        val treeNanos = measureNanoTime {
            windows.forEach { w -> tree.retrieveBox(w[0], w[1], w[2], w[3]) }
        }
        val scanNanos = measureNanoTime {
            windows.forEach { w -> points.count { it.x > w[0] && it.x <= w[2] && it.y > w[1] && it.y <= w[3] } }
        }

        assertTrue(
            treeNanos < scanNanos,
            "small-window quadtree queries ($treeNanos ns) should beat a full linear scan ($scanNanos ns)"
        )
    }

    @Test
    fun `a degenerate sorted insertion order still retrieves correctly`() {
        // Inserting already-sorted keys makes the tree lean into a near-linked-list shape.
        val points = randomPoints(2_000).sortedWith(compareBy({ it.x }, { it.y }))
        val tree = treeOf(points)

        repeat(50) {
            val minX = random.nextDouble(-10_000.0, 9_000.0)
            val minY = random.nextDouble(-10_000.0, 9_000.0)
            val maxX = minX + random.nextDouble(0.0, 6_000.0)
            val maxY = minY + random.nextDouble(0.0, 6_000.0)
            assertEquals(
                points.inBox(minX, minY, maxX, maxY),
                tree.retrieveBox(minX, minY, maxX, maxY).toSet()
            )
        }
    }

    @Test
    fun `clear empties a large tree and it can be refilled`() {
        val whole = doubleArrayOf(-1e9, -1e9, 1e9, 1e9)
        val points = randomPoints(10_000)
        val tree = treeOf(points)
        assertEquals(points.size, tree.retrieveBox(whole[0], whole[1], whole[2], whole[3]).size)

        tree.clear()
        assertTrue(tree.retrieveBox(whole[0], whole[1], whole[2], whole[3]).isEmpty())

        randomPoints(10_000).forEach { tree.insert(it.x, it.y, it) }
        assertEquals(10_000, tree.retrieveBox(whole[0], whole[1], whole[2], whole[3]).size)
    }
}
