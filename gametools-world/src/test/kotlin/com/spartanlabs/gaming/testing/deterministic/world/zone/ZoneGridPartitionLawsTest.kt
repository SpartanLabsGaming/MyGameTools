package com.spartanlabs.gaming.testing.deterministic.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.world.zone.ZoneGrid
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
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. [ZoneGrid.zoneAt]'s partition laws, checked over many random
 * `(columns, rows, bounds)` combinations and many random query points, seeded for
 * reproducibility - the partition never misreports which cell a point falls in: `clamped =
 * false` succeeds exactly when the point is within the space's bounds, and on success the
 * resolved zone's own bounds contain the point; `clamped = true` always succeeds, in or out of
 * bounds.
 */
class ZoneGridPartitionLawsTest {

    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    private data class Config(val columns: Int, val rows: Int, val width: Double, val height: Double)

    private fun randomConfigs(count: Int, random: Random): List<Config> = List(count) {
        Config(
            columns = random.nextInt(1, 10),
            rows = random.nextInt(1, 10),
            width = random.nextDouble(1.0, 100.0),
            height = random.nextDouble(1.0, 100.0),
        )
    }

    private fun randomPoints(count: Int, width: Double, height: Double, random: Random): List<Point> = List(count) {
        Point(random.nextDouble(-width, 2 * width), random.nextDouble(-height, 2 * height))
    }

    @Test
    fun `zoneAt(clamped = false) succeeds iff the point is within bounds, and the resolved zone contains the point`() {
        val random = Random(47_2026)
        randomConfigs(50, random).forEach { config ->
            val space = FixtureSpace(Square(Point(0.0, 0.0), Dimensions(config.width, config.height)))
            val grid = ZoneGrid(space, config.columns, config.rows)

            randomPoints(20, config.width, config.height, random).forEach { point ->
                val result = grid.zoneAt(point, clamped = false)
                assertEquals(space.bounds.contains(point), result.isSuccess, "zoneAt(clamped=false) disagreed with contains() at $point for $config")
                result.getOrNull()?.let { zone -> assertTrue(zone.bounds.contains(point), "resolved zone does not contain $point for $config") }
            }
        }
    }

    @Test
    fun `zoneAt(clamped = true) always succeeds, in or out of bounds`() {
        val random = Random(47_2027)
        randomConfigs(50, random).forEach { config ->
            val space = FixtureSpace(Square(Point(0.0, 0.0), Dimensions(config.width, config.height)))
            val grid = ZoneGrid(space, config.columns, config.rows)

            randomPoints(20, config.width, config.height, random).forEach { point ->
                assertTrue(grid.zoneAt(point, clamped = true).isSuccess, "zoneAt(clamped=true) failed for $point in $config")
            }
        }
    }
}
