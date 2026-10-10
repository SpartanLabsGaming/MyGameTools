package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
//endregion

/**
 * Level 2 - component. [ZoneIndex.step] publishing exactly one [EntityChangedZone] per
 * transition on [World.events]: entering a zone, crossing between zones, leaving the grid's
 * extent, and a non-[com.spartanlabs.gaming.gameobjects.VisibleObject] [GameObject] still being
 * tracked and published.
 */
@OptIn(ExperimentalGameToolsApi::class)
class EntityChangedZoneTest {

    /** A non-visible [GameObject] - no drawable state, just a [location] to move around. */
    private class Trigger(location: Point) : GameObject(location)

    @Test
    fun `a fresh entity entering a zone publishes from = null`() {
        val world = World()
        val grid = fixtureGrid()
        ZoneIndex(grid).also(world::installSystem)
        val events = recorder(world)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()

        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertContentEquals(listOf(EntityChangedZone(actor, null, zone)), events)
    }

    @Test
    fun `crossing zones publishes both the old and new zone`() {
        val world = World()
        val grid = fixtureGrid()
        ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        world.stepSystems()
        val events = recorder(world)

        actor.location.setTo(15.0, 5.0)
        world.stepSystems()

        val oldZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        val newZone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        assertContentEquals(listOf(EntityChangedZone(actor, oldZone, newZone)), events)
    }

    @Test
    fun `leaving the grid publishes to = null`() {
        val world = World()
        val grid = fixtureGrid()
        ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        world.stepSystems()
        val events = recorder(world)

        actor.location.setTo(-100.0, -100.0)
        world.stepSystems()

        val oldZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertContentEquals(listOf(EntityChangedZone(actor, oldZone, null)), events)
    }

    @Test
    fun `a non-VisibleObject GameObject still gets tracked and published`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val events = recorder(world)
        val trigger = Trigger(Point(5.0, 5.0)).also(world::add)

        world.stepSystems()

        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertContentEquals(listOf(EntityChangedZone(trigger, null, zone)), events)
        assertEquals(zone, index.zoneOf(trigger.entityId).getOrNull())
    }
}
