package com.spartanlabs.gaming.testing.nonfunctional.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.OutOfGridException
import com.spartanlabs.gaming.world.map.StaticGeometry
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TiledMap
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
 * Level 4c - non-functional guards on [TiledMap]'s query miss paths: [TiledMap.isWalkable] over
 * mixed points and [TiledMap.terrainAt] off the grid stay within a generous time budget (the same
 * "sane time budget" framing as `gametools-core`'s `WorldSystemRegistryRobustnessTest`, not a hard
 * SLA). A miss that records a stack trace again would be orders of magnitude slower and trip them.
 */
class TiledMapQueryThroughputTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)
    private val water = TerrainType(walkable = false, movementCost = 1.0, heightLevel = 0, blocksVision = true)

    private val size = 64

    /** A 64x64 map, tileSize 1.0, with a checkerboard grass/water palette and one obstacle. */
    private fun fixtureMap(): TiledMap = TiledMap(
        widthTiles = size,
        heightTiles = size,
        tileSize = 1.0,
        terrain = TerrainLayer(size, size, List(size * size) { i -> (i / size + i % size) % 2 }, listOf(grass, water)),
        staticGeometry = StaticGeometry(listOf(CenteredBox(Point(32.0, 32.0), Dimensions(4.0, 4.0)))),
    )

    @Test
    fun `isWalkable over a million mixed points stays within a generous budget`() {
        val map = fixtureMap()
        val far = size.toDouble()
        // In-grid, out of bounds, and exactly on a far edge (contains is true, the tile is off the grid).
        val points = listOf(
            Point(10.5, 20.5), Point(33.5, 33.5), Point(63.5, 0.5),
            Point(-1.0, 10.0), Point(70.0, 70.0), Point(10.0, -3.0),
            Point(far, 10.5), Point(10.5, far), Point(far, far),
        )
        var walkable = 0

        val elapsedMillis = measureNanoTime {
            repeat(1_000_000) { i -> if (map.isWalkable(points[i % points.size])) walkable++ }
        } / 1_000_000

        assertTrue(walkable > 0, "the in-grid sample should include walkable points")
        assertTrue(elapsedMillis < 10_000, "1000000 isWalkable calls took ${elapsedMillis}ms")
    }

    @Test
    fun `a million off-grid terrainAt misses stay within a generous budget and record no stack trace`() {
        val map = fixtureMap()
        val far = size.toDouble()
        val offGrid = listOf(Point(-1.0, 10.0), Point(70.0, 10.0), Point(10.0, -1.0), Point(10.0, 70.0), Point(far, far))
        var sampled = 0

        val elapsedMillis = measureNanoTime {
            repeat(1_000_000) { i ->
                val result = map.terrainAt(offGrid[i % offGrid.size])
                if (i % 100_000 == 0) {
                    val miss = assertIs<OutOfGridException>(result.exceptionOrNull())
                    assertTrue(miss.stackTrace.isEmpty())
                    sampled++
                }
            }
        } / 1_000_000

        assertEquals(10, sampled)
        assertTrue(elapsedMillis < 10_000, "1000000 off-grid terrainAt calls took ${elapsedMillis}ms")
    }
}
