package com.spartanlabs.gaming.testing.deterministic.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.serializations.DimensionsSnapshot
import com.spartanlabs.geometry.serializations.PointSnapshot
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.MapDefinition
import com.spartanlabs.gaming.world.map.ObstacleSnapshot
import com.spartanlabs.gaming.world.map.SpawnPointSnapshot
import com.spartanlabs.gaming.world.map.TerrainTypeSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Level 4a - deterministic logic. [MapDefinition] -> JSON -> decoded [MapDefinition] must be
 * `equals()`-identical to the original. This is the behavioural test [MapDefinition.tiles]'s
 * `List<Int>` typing (rather than `IntArray`) exists to make meaningful - an `IntArray`-typed
 * property would fail this via reference-equality `equals()` even on a correct round trip.
 */
class MapDefinitionRoundTripTest {

    private val json = Json

    private val grass = TerrainTypeSnapshot(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)
    private val water = TerrainTypeSnapshot(walkable = false, movementCost = 1.5, heightLevel = -1, blocksVision = true)

    private fun assertRoundTrips(definition: MapDefinition) {
        val encoded = json.encodeToString(definition)
        val decoded = json.decodeFromString<MapDefinition>(encoded)

        assertEquals(definition, decoded)
    }

    @Test
    fun `a minimal MapDefinition with no obstacles or spawn points round-trips`() {
        assertRoundTrips(
            MapDefinition(
                widthTiles = 1,
                heightTiles = 1,
                tileSize = 10.0,
                tiles = listOf(0),
                terrainPalette = listOf(grass),
            )
        )
    }

    @Test
    fun `a MapDefinition with a multi-entry palette and a larger grid round-trips`() {
        assertRoundTrips(
            MapDefinition(
                widthTiles = 3,
                heightTiles = 2,
                tileSize = 25.0,
                tiles = listOf(0, 1, 0, 1, 1, 0),
                terrainPalette = listOf(grass, water),
            )
        )
    }

    @Test
    fun `a MapDefinition with obstacles and spawn points round-trips`() {
        assertRoundTrips(
            MapDefinition(
                widthTiles = 4,
                heightTiles = 3,
                tileSize = 10.0,
                tiles = listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
                terrainPalette = listOf(grass, water),
                obstacles = listOf(
                    ObstacleSnapshot(center = PointSnapshot(25.0, 5.0), halfExtents = DimensionsSnapshot(3.0, 3.0)),
                    ObstacleSnapshot(center = PointSnapshot(5.0, 25.0), halfExtents = DimensionsSnapshot(1.5, 4.0)),
                ),
                spawnPoints = listOf(
                    SpawnPointSnapshot(name = "red-spawn", position = PointSnapshot(5.0, 5.0), facing = 0, team = "red"),
                    SpawnPointSnapshot(name = "blue-spawn", position = PointSnapshot(15.0, 25.0), facing = 180, team = "blue"),
                ),
            )
        )
    }

    @Test
    fun `a spawn point with null facing and team round-trips`() {
        assertRoundTrips(
            MapDefinition(
                widthTiles = 1,
                heightTiles = 1,
                tileSize = 10.0,
                tiles = listOf(0),
                terrainPalette = listOf(grass),
                spawnPoints = listOf(SpawnPointSnapshot(name = "unaffiliated", position = PointSnapshot(1.0, 1.0))),
            )
        )
    }
}
