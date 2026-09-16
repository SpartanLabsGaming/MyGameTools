package com.spartanlabs.gaming.testing.deterministic.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.EntityChangedZone
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
//endregion

/**
 * Level 4a - deterministic logic. The same [World] + [ZoneGrid] + the same seeded sequence of
 * entity moves produces the same sequence of published [EntityChangedZone]s across repeated
 * runs - matching the rest of the engine's "same seed, same result" contract
 * ([World]'s own class KDoc).
 */
class ZoneIndexRefreshDeterminismTest {

    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    private fun fixtureGrid(): ZoneGrid = ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(80.0, 80.0))), columns = 4, rows = 4)

    /**
     * Runs a fixed number of entities through a fixed number of ticks, each moved by a fixed
     * seeded-random displacement, refreshing [ZoneIndex] after every tick, and returns every
     * published [EntityChangedZone] as `(actor's index in this run's own actor list, from, to)`
     * - a projection that is comparable across two independent runs, since each run constructs
     * its own fresh [Actor] instances (which compare by reference, not by value).
     */
    private fun runScenario(seed: Long): List<Triple<Int, Zone?, Zone?>> {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid)

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
            index.refresh(world)
        }
        return events
    }

    @Test
    fun `the same seeded sequence of moves produces the same sequence of EntityChangedZone events every run`() {
        val first = runScenario(seed = 47_2028L)
        val second = runScenario(seed = 47_2028L)

        assertContentEquals(first, second)
    }
}
