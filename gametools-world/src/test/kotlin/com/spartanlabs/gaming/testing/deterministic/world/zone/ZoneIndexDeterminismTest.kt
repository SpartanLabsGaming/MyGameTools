package com.spartanlabs.gaming.testing.deterministic.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.UnzonedEntityException
import com.spartanlabs.gaming.world.zone.Zone
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. The same seeded [World] + [ZoneIndex] (installed with
 * [World.installSystem] and driven by [World.stepSystems]) + the same seeded sequence of entity
 * moves produces the same sequence of published [EntityChangedZone]s and the same
 * [ZoneIndex.zoneOf] answers across repeated runs - matching the rest of the engine's "same seed,
 * same result" contract ([World]'s own class KDoc) - and [ZoneIndex.zoneOf] is a pure,
 * repeatable read.
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexDeterminismTest {

    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    private fun fixtureGrid(): ZoneGrid = ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(80.0, 80.0))), columns = 4, rows = 4)

    /** One scenario run: the event projection, the final zoneOf projection, and the live objects. */
    private class Run(
        val events: List<Triple<Int, Zone?, Zone?>>,
        val finalZones: List<String>,
        val world: World,
        val index: ZoneIndex,
        val actors: List<Actor>,
    )

    /**
     * Runs a fixed number of entities through a fixed number of ticks, each tick placing every
     * entity at a fresh seeded-random absolute point in `-10..90` on both axes - deliberately
     * overshooting the 80x80 grid, so entities leave the grid's extent and re-enter it - and
     * stepping the installed [ZoneIndex] after every tick. Returns
     * every published [EntityChangedZone] as `(actor's index in this run's own actor list, from,
     * to)` - a projection that is comparable across two independent runs, since each run
     * constructs its own fresh [Actor] instances (which compare by reference, not by value) - and
     * each actor's final [ZoneIndex.zoneOf] answer: the zone's name on a success, `"unzoned"` on a
     * failure.
     */
    private fun runScenario(seed: Long): Run {
        val world = World(seed)
        val index = ZoneIndex(fixtureGrid())
        world.installSystem(index)

        val random = Random(seed)
        val actors = List(5) { Actor(location = Point(random.nextDouble(0.0, 80.0), random.nextDouble(0.0, 80.0))) }
        val actorIndices: Map<Actor, Int> = actors.withIndex().associate { (i, actor) -> actor to i }
        actors.forEach(world::add)

        val events = mutableListOf<Triple<Int, Zone?, Zone?>>()
        world.events.subscribe { event ->
            if (event is EntityChangedZone) events += Triple(actorIndices.getValue(event.entity as Actor), event.from, event.to)
        }

        repeat(10) {
            actors.forEach { actor -> actor.location.setTo(random.nextDouble(-10.0, 90.0), random.nextDouble(-10.0, 90.0)) }
            world.tick()
            world.stepSystems()
        }
        val finalZones = actors.map { actor -> index.zoneOf(actor.entityId).fold({ it.name }, { "unzoned" }) }
        return Run(events, finalZones, world, index, actors)
    }

    @Test
    fun `the same seeded sequence of moves produces the same EntityChangedZone sequence and the same zoneOf answers every run, driven via installSystem and stepSystems`() {
        val first = runScenario(47_2028L)
        val second = runScenario(47_2028L)

        assertContentEquals(first.events, second.events)
        assertContentEquals(first.finalZones, second.finalZones)
    }

    @Test
    fun `zoneOf is a pure, repeatable read`() {
        val run = runScenario(47_2028L)
        val published = mutableListOf<EntityChangedZone>()
        run.world.events.subscribe { if (it is EntityChangedZone) published += it }
        val grid = fixtureGrid()
        val occupancyBefore = grid.zones.map { run.index.entitiesIn(it) }
        val ids = run.actors.map { it.entityId } + EntityId.UNASSIGNED + EntityId(999L)

        ids.forEach { id ->
            val a = run.index.zoneOf(id)
            val b = run.index.zoneOf(id)
            assertEquals(a.getOrNull(), b.getOrNull(), "zoneOf($id)")
            if (a.isFailure) {
                // Two failures are compared by their entityId, never with ==: each miss builds a new exception.
                assertTrue(b.isFailure)
                assertEquals(id, assertIs<UnzonedEntityException>(a.exceptionOrNull()).entityId)
                assertEquals(id, assertIs<UnzonedEntityException>(b.exceptionOrNull()).entityId)
            }
        }

        assertTrue(published.isEmpty())
        assertEquals(occupancyBefore, grid.zones.map { run.index.entitiesIn(it) })
    }
}
