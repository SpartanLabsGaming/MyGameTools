package com.spartanlabs.gaming.testing.integration.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.MapDefinition
import com.spartanlabs.gaming.world.map.MapLoader
import com.spartanlabs.gaming.world.map.SpawnPoint
import com.spartanlabs.gaming.world.map.TerrainTypeSnapshot
import com.spartanlabs.gaming.world.map.TiledMap
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Level 3 - integration. Loads the real `fixture-map.json` file-format fixture through
 * [MapLoader.fromJson] and checks the resulting [TiledMap] against the fixture's known values,
 * plus [MapLoader]'s [Result]-returning failure handling for malformed JSON and a structurally
 * invalid [MapDefinition].
 */
class MapLoaderIntegrationTest {

    /** Reads `src/test/resources/fixture-map.json` off the test classpath. */
    private fun fixtureJson(): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixture-map.json")) { "fixture-map.json not found on the test classpath" }
            .bufferedReader()
            .readText()

    @Test
    fun `fromJson builds a TiledMap whose bounds, isWalkable, terrainAt and spawnPoint match the fixture`() {
        val map = MapLoader.fromJson(fixtureJson()).getOrThrow()

        assertEquals(Point(0.0, 0.0), map.bounds.location)
        assertEquals(Dimensions(40.0, 30.0), map.bounds.dimensions)

        assertTrue(map.isWalkable(Point(5.0, 5.0)), "tile (0,0) is grass and unobstructed")
        assertFalse(map.isWalkable(Point(15.0, 15.0)), "tile (1,1) is water")
        assertFalse(map.isWalkable(Point(25.0, 5.0)), "tile (2,0) is grass but the obstacle covers this point")
        assertTrue(map.isWalkable(Point(21.0, 5.0)), "tile (2,0) is grass and outside the obstacle")

        val grass = map.terrainAt(Point(5.0, 5.0))
        checkNotNull(grass)
        assertTrue(grass.walkable)
        assertFalse(grass.blocksVision)

        val water = map.terrainAt(Point(15.0, 15.0))
        checkNotNull(water)
        assertFalse(water.walkable)
        assertTrue(water.blocksVision)

        assertEquals(
            SpawnPoint(name = "red-spawn", position = Point(5.0, 5.0), facing = 0, team = "red"),
            map.spawnPoint("red-spawn"),
        )
        assertEquals(
            SpawnPoint(name = "blue-spawn", position = Point(15.0, 25.0), facing = 180, team = "blue"),
            map.spawnPoint("blue-spawn"),
        )
    }

    @Test
    fun `fromJson returns a Result failure, not a thrown exception, for malformed JSON`() {
        val result = MapLoader.fromJson("{this is not valid json")

        assertTrue(result.isFailure)
    }

    @Test
    fun `fromDefinition returns a Result failure, not a thrown exception, for a structurally invalid MapDefinition`() {
        val invalid = MapDefinition(
            widthTiles = 1,
            heightTiles = 1,
            tileSize = 10.0,
            tiles = listOf(5), // out of range: the palette below has only one entry (index 0)
            terrainPalette = listOf(TerrainTypeSnapshot(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)),
        )

        val result = MapLoader.fromDefinition(invalid)

        assertTrue(result.isFailure)
    }
}
