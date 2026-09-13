package com.spartanlabs.gaming.testing.deterministic.spatial

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.SpatialIndex
import com.spartanlabs.gaming.spatial.UniformGrid
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
//endregion

/**
 * Level 4a - deterministic logic. [UniformGrid] and [QuadtreeSpatialIndex] are designed to share
 * exactly the same query semantics (the half-open box `minX < x <= maxX && minY < y <= maxY` for
 * [SpatialIndex.queryBox], a true Euclidean circle for [SpatialIndex.queryRadius]) - this pins
 * that law against a brute-force linear-scan oracle, one shared oracle applying to both
 * implementations, for a static insert-only population and across a sequence of seeded random
 * `move`s simulating ticks.
 */
class SpatialIndexQueryLawsTest {

    /** [id] keeps every generated point distinct even if two land on the same coordinates. */
    private data class P(val id: Int, var x: Double, var y: Double)

    private val random = Random(48_2026)

    private fun randomPoints(count: Int): List<P> =
        List(count) { P(it, random.nextDouble(-1_000.0, 1_000.0), random.nextDouble(-1_000.0, 1_000.0)) }

    /** The two [SpatialIndex] implementations under test, each fresh and empty. */
    private fun implementations(): List<Pair<String, SpatialIndex<P>>> =
        listOf("UniformGrid" to UniformGrid(cellSize = 50.0), "QuadtreeSpatialIndex" to QuadtreeSpatialIndex())

    /** Brute-force equivalent of [SpatialIndex.queryBox]'s half-open `(minX, maxX] x (minY, maxY]` box. */
    private fun List<P>.inBox(minX: Double, minY: Double, maxX: Double, maxY: Double): Set<P> =
        filter { it.x > minX && it.x <= maxX && it.y > minY && it.y <= maxY }.toSet()

    /** Brute-force equivalent of [SpatialIndex.queryRadius]'s true Euclidean circle. */
    private fun List<P>.inRadius(x: Double, y: Double, radius: Double): Set<P> =
        filter {
            val dx = it.x - x
            val dy = it.y - y
            dx * dx + dy * dy <= radius * radius
        }.toSet()

    /** Asserts [index]'s `queryBox`/`queryRadius` agree with a linear scan over [points], [samples] times each. */
    private fun assertQueryLawsHold(label: String, index: SpatialIndex<P>, points: List<P>, samples: Int = 100) {
        repeat(samples) {
            val minX = random.nextDouble(-1_000.0, 900.0)
            val minY = random.nextDouble(-1_000.0, 900.0)
            val maxX = minX + random.nextDouble(0.0, 400.0)
            val maxY = minY + random.nextDouble(0.0, 400.0)
            assertEquals(
                points.inBox(minX, minY, maxX, maxY),
                index.queryBox(minX, minY, maxX, maxY).toSet(),
                "$label: queryBox disagreed with a linear scan for box ($minX,$minY)..($maxX,$maxY)"
            )
        }
        repeat(samples) {
            val cx = random.nextDouble(-1_000.0, 1_000.0)
            val cy = random.nextDouble(-1_000.0, 1_000.0)
            val radius = random.nextDouble(1.0, 300.0)
            assertEquals(
                points.inRadius(cx, cy, radius),
                index.queryRadius(cx, cy, radius).toSet(),
                "$label: queryRadius disagreed with a linear scan for circle ($cx,$cy) r=$radius"
            )
        }
    }

    @Test
    fun `queryBox and queryRadius match a linear scan for a static insert-only population`() {
        val points = randomPoints(500)

        implementations().forEach { (label, index) ->
            points.forEach { index.insert(it.x, it.y, it) }
            assertQueryLawsHold(label, index, points)
        }
    }

    @Test
    fun `the query laws hold after each of a sequence of seeded random moves`() {
        val points = randomPoints(300)

        implementations().forEach { (label, index) ->
            points.forEach { index.insert(it.x, it.y, it) }

            repeat(100) {
                val moving = points[random.nextInt(points.size)]
                val fromX = moving.x
                val fromY = moving.y
                val toX = random.nextDouble(-1_000.0, 1_000.0)
                val toY = random.nextDouble(-1_000.0, 1_000.0)

                index.move(fromX, fromY, toX, toY, moving)
                moving.x = toX
                moving.y = toY

                assertQueryLawsHold(label, index, points, samples = 5)
            }
        }
    }
}
