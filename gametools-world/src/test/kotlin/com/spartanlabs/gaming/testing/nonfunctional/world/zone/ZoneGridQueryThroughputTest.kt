package com.spartanlabs.gaming.testing.nonfunctional.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.world.zone.UnzonedPointException
import com.spartanlabs.gaming.world.zone.ZoneGrid
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.system.measureNanoTime
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional guard on [ZoneGrid.zoneAt]'s `clamped = false` miss path - the one
 * `ZoneIndex.step` meets for every entity outside the grid, every step. It stays within a generous
 * time budget (the same "sane time budget" framing as `gametools-core`'s
 * `WorldSystemRegistryRobustnessTest`, not a hard SLA); a miss that records a stack trace again
 * would be orders of magnitude slower and trip it.
 */
class ZoneGridQueryThroughputTest {

    /** A rectangular [Space] with no terrain: every point inside [bounds] is in bounds and walkable. */
    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    /** An 8x8 grid over a 64x64 space, so 8x8 zones. */
    private fun fixtureGrid(): ZoneGrid = ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(64.0, 64.0))), columns = 8, rows = 8)

    @Test
    fun `a million out-of-extent zoneAt misses with clamped = false stay within a generous budget and record no stack trace`() {
        val grid = fixtureGrid()
        // One point beyond each of the four sides of the extent.
        val outside = listOf(Point(-1.0, 32.0), Point(70.0, 32.0), Point(32.0, -1.0), Point(32.0, 70.0))
        var sampled = 0

        val elapsedMillis = measureNanoTime {
            repeat(1_000_000) { i ->
                val result = grid.zoneAt(outside[i % outside.size], clamped = false)
                if (i % 100_000 == 0) {
                    val miss = assertIs<UnzonedPointException>(result.exceptionOrNull())
                    assertTrue(miss.stackTrace.isEmpty())
                    sampled++
                }
            }
        } / 1_000_000

        assertEquals(10, sampled)
        assertTrue(elapsedMillis < 10_000, "1000000 out-of-extent zoneAt calls took ${elapsedMillis}ms")
    }
}
