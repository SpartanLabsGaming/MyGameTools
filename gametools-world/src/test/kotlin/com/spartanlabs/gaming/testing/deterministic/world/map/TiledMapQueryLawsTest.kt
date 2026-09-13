package com.spartanlabs.gaming.testing.deterministic.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.StaticGeometry
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TiledMap
import com.spartanlabs.gaming.world.map.TileIndex
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.floor
import kotlin.random.Random
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Level 4a - deterministic logic. [TiledMap.tileAt] / [TiledMap.terrainAt] / [TiledMap.isWalkable]
 * are pinned against a brute-force reference computed directly from the fixture's raw grid/
 * obstacle data (no [TiledMap] internals involved), over a seeded, randomized sample of points
 * plus a fixed set of boundary points - same "queried structure agrees with a linear-scan oracle"
 * shape as `SpatialIndexQueryLawsTest`.
 */
class TiledMapQueryLawsTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)
    private val water = TerrainType(walkable = false, movementCost = 1.0, heightLevel = 0, blocksVision = true)
    private val palette = listOf(grass, water)

    private val widthTiles = 4
    private val heightTiles = 3
    private val tileSize = 10.0

    /** Flat, row-major, matching `fixture-map.json`'s own grid. */
    private val tileTypeIndices = listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1)

    private data class ObstacleBox(val centerX: Double, val centerY: Double, val halfWidth: Double, val halfHeight: Double)

    private val obstacle = ObstacleBox(centerX = 25.0, centerY = 5.0, halfWidth = 3.0, halfHeight = 3.0)

    private fun fixtureMap(): TiledMap = TiledMap(
        widthTiles = widthTiles,
        heightTiles = heightTiles,
        tileSize = tileSize,
        terrain = TerrainLayer(widthTiles, heightTiles, tileTypeIndices, palette),
        staticGeometry = StaticGeometry(
            listOf(CenteredBox(Point(obstacle.centerX, obstacle.centerY), Dimensions(obstacle.halfWidth, obstacle.halfHeight)))
        ),
    )

    /** Direct index arithmetic over the raw grid - the oracle [TiledMap.tileAt] is checked against. */
    private fun naiveTileAt(x: Double, y: Double): TileIndex = TileIndex(floor(x / tileSize).toInt(), floor(y / tileSize).toInt())

    /** Direct index arithmetic over the raw grid/palette - the oracle [TiledMap.terrainAt] is checked against. */
    private fun naiveTerrainAt(x: Double, y: Double): TerrainType? {
        val tile = naiveTileAt(x, y)
        if (tile.x !in 0 until widthTiles || tile.y !in 0 until heightTiles) return null
        return palette[tileTypeIndices[tile.y * widthTiles + tile.x]]
    }

    /** Direct bounds arithmetic - the oracle [TiledMap.contains] is checked against. */
    private fun naiveContains(x: Double, y: Double): Boolean =
        x in 0.0..(widthTiles * tileSize) && y in 0.0..(heightTiles * tileSize)

    /** Direct box arithmetic - the oracle [StaticGeometry.blocksPoint] is checked against. */
    private fun naiveBlocksPoint(x: Double, y: Double): Boolean =
        x in (obstacle.centerX - obstacle.halfWidth)..(obstacle.centerX + obstacle.halfWidth) &&
            y in (obstacle.centerY - obstacle.halfHeight)..(obstacle.centerY + obstacle.halfHeight)

    /** Direct combination of the three oracles above - the oracle [TiledMap.isWalkable] is checked against. */
    private fun naiveIsWalkable(x: Double, y: Double): Boolean =
        naiveContains(x, y) && naiveTerrainAt(x, y)?.walkable == true && !naiveBlocksPoint(x, y)

    /** Corners, tile edges, obstacle edges/center, and out-of-bounds points on every side. */
    private fun boundaryPoints(): List<Point> = listOf(
        Point(0.0, 0.0), Point(40.0, 0.0), Point(0.0, 30.0), Point(40.0, 30.0),
        Point(10.0, 0.0), Point(20.0, 10.0), Point(30.0, 20.0), Point(40.0, 30.0),
        Point(22.0, 2.0), Point(22.0, 8.0), Point(28.0, 2.0), Point(28.0, 8.0), Point(25.0, 5.0),
        Point(-1.0, -1.0), Point(41.0, 31.0), Point(-5.0, 15.0), Point(45.0, 15.0),
    )

    private fun randomPoints(count: Int, random: Random): List<Point> =
        List(count) { Point(random.nextDouble(-10.0, 50.0), random.nextDouble(-10.0, 40.0)) }

    private fun assertQueryLawsHold(map: TiledMap, points: List<Point>) {
        points.forEach { point ->
            assertEquals(naiveTileAt(point.x, point.y), map.tileAt(point), "tileAt disagreed with the oracle at $point")
            assertEquals(naiveTerrainAt(point.x, point.y), map.terrainAt(point), "terrainAt disagreed with the oracle at $point")
            assertEquals(naiveIsWalkable(point.x, point.y), map.isWalkable(point), "isWalkable disagreed with the oracle at $point")
        }
    }

    @Test
    fun `tileAt, terrainAt and isWalkable agree with a brute-force oracle at every boundary point`() {
        assertQueryLawsHold(fixtureMap(), boundaryPoints())
    }

    @Test
    fun `tileAt, terrainAt and isWalkable agree with a brute-force oracle over a seeded random sample`() {
        assertQueryLawsHold(fixtureMap(), randomPoints(500, Random(46_2026)))
    }
}
