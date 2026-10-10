package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.MissingSpawnPointException
import com.spartanlabs.gaming.world.map.OutOfGridException
import com.spartanlabs.gaming.world.map.SpawnPoint
import com.spartanlabs.gaming.world.map.StaticGeometry
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TiledMap
import com.spartanlabs.gaming.world.map.TileIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [TiledMap]'s bounds/tile conversion, [TiledMap.isWalkable]'s combination
 * of bounds, terrain, and static geometry, its spawn-point registry, and its constructor
 * `require()` guards.
 */
class TiledMapTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)
    private val water = TerrainType(walkable = false, movementCost = 1.0, heightLevel = 0, blocksVision = true)

    /** Asserts [name] misses: spawnPoint is a failure carrying a MissingSpawnPointException for exactly [name]. */
    private fun TiledMap.assertNoSpawnPoint(name: String) {
        val result = spawnPoint(name)
        assertTrue(result.isFailure, "expected no spawn point named '$name', but got ${result.getOrNull()}")
        assertEquals(name, assertIs<MissingSpawnPointException>(result.exceptionOrNull()).name)
    }

    /** Asserts [point] is off the grid: terrainAt is a failure carrying an OutOfGridException for point's tile. */
    private fun TiledMap.assertOffGrid(point: Point) {
        val result = terrainAt(point)
        assertTrue(result.isFailure, "expected $point to be off the grid, but terrainAt returned ${result.getOrNull()}")
        assertEquals(tileAt(point), assertIs<OutOfGridException>(result.exceptionOrNull()).tile)
    }

    /** A 2x2 tile grid, tileSize 10: (0,0)=grass, (1,0)=water, (0,1)=grass, (1,1)=grass, plus one obstacle over tile (1,1). */
    private fun fixtureMap(spawnPoints: List<SpawnPoint> = emptyList()): TiledMap {
        val terrain = TerrainLayer(
            widthTiles = 2,
            heightTiles = 2,
            tileTypeIndices = listOf(0, 1, 0, 0),
            palette = listOf(grass, water),
        )
        val obstacle = CenteredBox(center = Point(15.0, 15.0), halfExtents = Dimensions(2.0, 2.0))
        return TiledMap(
            widthTiles = 2,
            heightTiles = 2,
            tileSize = 10.0,
            terrain = terrain,
            staticGeometry = StaticGeometry(listOf(obstacle)),
            spawnPoints = spawnPoints,
        )
    }

    @Test
    fun `bounds matches widthTiles, heightTiles and tileSize`() {
        val map = fixtureMap()

        assertEquals(Point(0.0, 0.0), map.bounds.location)
        assertEquals(Dimensions(20.0, 20.0), map.bounds.dimensions)
    }

    @Test
    fun `tileAt floors interior, edge and out-of-bounds points, including negative coordinates`() {
        val map = fixtureMap()

        assertEquals(TileIndex(0, 0), map.tileAt(Point(0.0, 0.0)))
        assertEquals(TileIndex(0, 0), map.tileAt(Point(5.0, 5.0)))
        assertEquals(TileIndex(1, 0), map.tileAt(Point(10.0, 0.0)))
        assertEquals(TileIndex(1, 1), map.tileAt(Point(19.9, 19.9)))
        assertEquals(TileIndex(2, 2), map.tileAt(Point(20.0, 20.0)), "exactly on the far edge floors to the next tile, outside the grid")
        assertEquals(TileIndex(-1, -1), map.tileAt(Point(-5.0, -5.0)))
    }

    @Test
    fun `isWalkable is true inside bounds over walkable terrain with no obstacle`() {
        val map = fixtureMap()

        assertTrue(map.isWalkable(Point(1.0, 1.0)))
    }

    @Test
    fun `isWalkable is false outside bounds`() {
        val map = fixtureMap()

        assertFalse(map.isWalkable(Point(-1.0, 1.0)))
        assertFalse(map.isWalkable(Point(25.0, 1.0)))
    }

    @Test
    fun `isWalkable is false over non-walkable terrain`() {
        val map = fixtureMap()

        assertFalse(map.isWalkable(Point(11.0, 1.0)))
    }

    @Test
    fun `isWalkable is false inside a static geometry obstacle`() {
        val map = fixtureMap()

        assertFalse(map.isWalkable(Point(15.0, 15.0)), "tile (1,1) is grass but the obstacle covers this point")
    }

    @Test
    fun `terrainAt is a success in the grid and an OutOfGridException failure off it`() {
        val map = fixtureMap()

        assertEquals(grass, map.terrainAt(Point(1.0, 1.0)).getOrNull())
        assertEquals(water, map.terrainAt(Point(11.0, 1.0)).getOrNull())
        map.assertOffGrid(Point(-1.0, 1.0))
        map.assertOffGrid(Point(25.0, 1.0))
        map.assertOffGrid(Point(20.0, 5.0)) // the far edge: floors to tile (2, 0), off the 2x2 grid
    }

    @Test
    fun `terrainAt passes TerrainLayer's failure through unchanged`() {
        val map = fixtureMap()
        val p = Point(25.0, 1.0)

        val viaMap = assertIs<OutOfGridException>(map.terrainAt(p).exceptionOrNull())
        val viaLayer = assertIs<OutOfGridException>(map.terrain.terrainAt(map.tileAt(p)).exceptionOrNull())

        // Identity is not observable - each call builds its own exception - so compare tile and message.
        assertEquals(viaLayer.tile, viaMap.tile)
        assertEquals(viaLayer.message, viaMap.message)
    }

    @Test
    fun `isWalkable is false exactly on the far edge, where contains is true but the tile is off the grid`() {
        val map = fixtureMap()

        assertTrue(map.contains(Point(20.0, 5.0)))
        assertFalse(map.isWalkable(Point(20.0, 5.0)))
    }

    @Test
    fun `spawnPoint is a success for a registered name and a MissingSpawnPointException failure for an unknown one`() {
        val spawn = SpawnPoint(name = "start", position = Point(5.0, 5.0))
        val map = fixtureMap(listOf(spawn))

        assertEquals(spawn, map.spawnPoint("start").getOrNull())
        map.assertNoSpawnPoint("unknown")
    }

    @Test
    fun `addSpawnPoint registers a new spawn point and rejects a duplicate name`() {
        val map = fixtureMap(listOf(SpawnPoint(name = "start", position = Point(5.0, 5.0))))
        val extra = SpawnPoint(name = "extra", position = Point(15.0, 5.0))

        map.addSpawnPoint(extra)
        assertEquals(extra, map.spawnPoint("extra").getOrNull())

        assertFailsWith<IllegalArgumentException> {
            map.addSpawnPoint(SpawnPoint(name = "start", position = Point(1.0, 1.0)))
        }
    }

    @Test
    fun `constructor rejects a terrain grid size mismatched against widthTiles and heightTiles`() {
        val mismatchedTerrain = TerrainLayer(
            widthTiles = 3,
            heightTiles = 3,
            tileTypeIndices = List(9) { 0 },
            palette = listOf(grass),
        )

        assertFailsWith<IllegalArgumentException> {
            TiledMap(
                widthTiles = 2,
                heightTiles = 2,
                tileSize = 10.0,
                terrain = mismatchedTerrain,
                staticGeometry = StaticGeometry(emptyList()),
            )
        }
    }

    @Test
    fun `constructor rejects non-positive widthTiles, heightTiles or tileSize`() {
        val terrain = TerrainLayer(widthTiles = 1, heightTiles = 1, tileTypeIndices = listOf(0), palette = listOf(grass))

        assertFailsWith<IllegalArgumentException> {
            TiledMap(widthTiles = 0, heightTiles = 1, tileSize = 10.0, terrain = terrain, staticGeometry = StaticGeometry(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            TiledMap(widthTiles = 1, heightTiles = 0, tileSize = 10.0, terrain = terrain, staticGeometry = StaticGeometry(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            TiledMap(widthTiles = 1, heightTiles = 1, tileSize = 0.0, terrain = terrain, staticGeometry = StaticGeometry(emptyList()))
        }
    }

    @Test
    fun `constructor rejects duplicate spawn point names`() {
        val terrain = TerrainLayer(widthTiles = 1, heightTiles = 1, tileTypeIndices = listOf(0), palette = listOf(grass))

        assertFailsWith<IllegalArgumentException> {
            TiledMap(
                widthTiles = 1,
                heightTiles = 1,
                tileSize = 10.0,
                terrain = terrain,
                staticGeometry = StaticGeometry(emptyList()),
                spawnPoints = listOf(
                    SpawnPoint(name = "start", position = Point(1.0, 1.0)),
                    SpawnPoint(name = "start", position = Point(2.0, 2.0)),
                ),
            )
        }
    }
}
