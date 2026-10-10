package com.spartanlabs.gaming.testing.nonfunctional.world.zone

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
import com.spartanlabs.gaming.world.zone.UnzonedEntityException
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
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
 * Level 4c - non-functional guard on [ZoneIndex.zoneOf]'s miss path: a million misses stay within a
 * generous time budget (the same "sane time budget" framing as `ZoneGridQueryThroughputTest`, not
 * a hard SLA). A miss that records a stack trace again would be orders of magnitude slower and trip
 * it.
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexQueryThroughputTest {

    /** A rectangular [Space] with no terrain: every point inside [bounds] is in bounds and walkable. */
    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    @Test
    fun `a million zoneOf misses stay within a generous budget and record no stack trace`() {
        val world = World()
        val index = ZoneIndex(ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(64.0, 64.0))), columns = 8, rows = 8))
        world.installSystem(index)
        world.add(Actor(location = Point(5.0, 5.0))) // numbered #1 and placed; every id queried below misses
        world.stepSystems()
        var sampled = 0

        val elapsedMillis = measureNanoTime {
            repeat(1_000_000) { i ->
                val result = index.zoneOf(EntityId(1_000L + i % 1_000))
                if (i % 100_000 == 0) {
                    val miss = assertIs<UnzonedEntityException>(result.exceptionOrNull())
                    assertTrue(miss.stackTrace.isEmpty())
                    sampled++
                }
            }
        } / 1_000_000

        assertEquals(10, sampled)
        assertTrue(elapsedMillis < 10_000, "1000000 zoneOf misses took ${elapsedMillis}ms")
    }
}
