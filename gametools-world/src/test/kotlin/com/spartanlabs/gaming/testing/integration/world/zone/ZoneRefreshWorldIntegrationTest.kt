package com.spartanlabs.gaming.testing.integration.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.map.StaticGeometry
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TiledMap
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
//endregion

/**
 * Level 3 - integration. A real [World], a [TiledMap]-backed [com.spartanlabs.gaming.gameobjects.Space],
 * several [Actor]s ticked across multiple frames with [ZoneIndex.refresh] called after each
 * [World.tick] (the documented ordering, `ZoneIndex`'s class KDoc). Asserts the
 * [EntityChangedZone] events observed on [World.events] exactly match the expected sequence for
 * a scripted movement path crossing multiple zones, including one entity that despawns
 * mid-scenario (a `to = null` event naming its last-known reference, not a missing event).
 */
class ZoneRefreshWorldIntegrationTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)

    /** A 4x3 tile, all-grass map - 40x30 world units, tileSize 10, matching the zone grid below. */
    private fun fixtureMap(): TiledMap = TiledMap(
        widthTiles = 4,
        heightTiles = 3,
        tileSize = 10.0,
        terrain = TerrainLayer(widthTiles = 4, heightTiles = 3, tileTypeIndices = List(12) { 0 }, palette = listOf(grass)),
        staticGeometry = StaticGeometry(emptyList()),
    )

    private fun recorder(world: World): MutableList<EntityChangedZone> =
        mutableListOf<EntityChangedZone>().also { log -> world.events.subscribe { if (it is EntityChangedZone) log += it } }

    @Test
    fun `refresh after each tick publishes exactly the expected EntityChangedZone sequence, including a despawn`() {
        val map = fixtureMap()
        val grid = ZoneGrid(map, columns = 4, rows = 3)
        val index = ZoneIndex(grid)
        val world = World().apply { space = map }
        val events = recorder(world)

        // Travels +x, 10 units/tick, along the row of zones (0,0) -> (1,0) -> (2,0) -> (3,0).
        val mover = Actor(location = Point(5.0, 5.0)).apply { movement = Movement.Directional; angle = 0 }
        // Stationary in zone (3,2); despawned partway through the scenario.
        val faller = Actor(location = Point(35.0, 25.0))
        world.add(mover)
        world.add(faller)

        val zone10 = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        val zone20 = grid.zoneAt(Point(25.0, 5.0)).getOrThrow()
        val zone30 = grid.zoneAt(Point(35.0, 5.0)).getOrThrow()
        val zone32 = grid.zoneAt(Point(35.0, 25.0)).getOrThrow()

        world.tick() // mover -> (15,5); faller settles in place
        index.refresh(world)

        world.tick() // mover -> (25,5)
        index.refresh(world)

        world.removeList += faller
        world.tick() // mover -> (35,5); faller ticked then dropped
        index.refresh(world)

        assertContentEquals(
            listOf(
                EntityChangedZone(mover, null, zone10),
                EntityChangedZone(faller, null, zone32),
                EntityChangedZone(mover, zone10, zone20),
                EntityChangedZone(mover, zone20, zone30),
                EntityChangedZone(faller, zone32, null),
            ),
            events,
        )
    }
}
