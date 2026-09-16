package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [ZoneIndex.refresh]'s recompute-and-diff bookkeeping: zone placement,
 * boundary crossings, skipping unnumbered entities, entities leaving the grid, entities leaving
 * the [World], and [ZoneIndex.entitiesIn]'s defensive-copy contract.
 */
class ZoneIndexTest {

    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    /** A 40x30 space, tiled by a 4x3 grid into 10x10 zones. */
    private fun fixtureGrid(): ZoneGrid = ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(40.0, 30.0))), columns = 4, rows = 3)

    @Test
    fun `a fresh ZoneIndex before any refresh has empty zoneOf and entitiesIn`() {
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)

        assertNull(index.zoneOf(EntityId(1L)))
        assertTrue(index.entitiesIn(grid.zones.first()).isEmpty())
    }

    @Test
    fun `one refresh places entities in the expected zones`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(15.0, 5.0)).also(world::add)

        index.refresh(world)

        val expectedZone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        assertEquals(expectedZone, index.zoneOf(actor.entityId))
        assertEquals(setOf(actor.entityId), index.entitiesIn(expectedZone))
    }

    @Test
    fun `a second refresh after crossing a zone boundary updates both the old and new zone`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        index.refresh(world)
        val oldZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(setOf(actor.entityId), index.entitiesIn(oldZone))

        actor.location.setTo(15.0, 5.0)
        index.refresh(world)
        val newZone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()

        assertEquals(newZone, index.zoneOf(actor.entityId))
        assertTrue(index.entitiesIn(oldZone).isEmpty())
        assertEquals(setOf(actor.entityId), index.entitiesIn(newZone))
    }

    @Test
    fun `an entity still at EntityId UNASSIGNED is skipped, not indexed`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(5.0, 5.0))
        world.gameObjects += actor // not numbered yet - neither World.add nor World.tick has run

        index.refresh(world)

        assertEquals(EntityId.UNASSIGNED, actor.entityId)
        assertNull(index.zoneOf(actor.entityId))
    }

    @Test
    fun `an entity moved outside the grid's extent is dropped from entitiesIn and zoneOf`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        index.refresh(world)
        val originalZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()

        actor.location.setTo(-100.0, -100.0)
        index.refresh(world)

        assertNull(index.zoneOf(actor.entityId))
        assertTrue(index.entitiesIn(originalZone).isEmpty())
    }

    @Test
    fun `a removed entity is dropped from the index on the next refresh`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        index.refresh(world)
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(setOf(actor.entityId), index.entitiesIn(zone))

        world.removeList += actor
        world.tick()
        index.refresh(world)

        assertNull(index.zoneOf(actor.entityId))
        assertTrue(index.entitiesIn(zone).isEmpty())
    }

    @Test
    fun `entitiesIn returns a copy - mutating it does not affect the index`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        index.refresh(world)
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()

        val copy = index.entitiesIn(zone).toMutableSet()
        copy.clear()

        assertEquals(setOf(actor.entityId), index.entitiesIn(zone))
    }
}
