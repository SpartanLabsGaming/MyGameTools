package com.spartanlabs.gaming.testing.nonfunctional

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
import kotlin.system.measureNanoTime
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional validation for [World.tick]: a large world advances within a
 * sane time budget, its per-frame cost grows roughly linearly (not quadratically) with the
 * object count, and a heavy heterogeneous world ticks without error while keeping its spatial
 * index consistent. Frame-by-frame behaviour is covered in
 * [com.spartanlabs.gaming.testing.component.gameobjects].
 */
class WorldTickThroughputTest {

    private val random = Random(7_2026)

    /** A world of [actorCount] actors, each drifting in a straight line at [Actor.speed] (10) per tick. */
    private fun driftingWorld(actorCount: Int): World = World().apply {
        repeat(actorCount) {
            add(
                Actor(location = Point(random.nextDouble(-1_000.0, 1_000.0), random.nextDouble(-1_000.0, 1_000.0)))
                    .apply {
                        movement = Movement.Directional
                        angle = 0
                    }
            )
        }
    }

    private fun World.tickTimes(frames: Int) = repeat(frames) { tick() }

    @Test
    fun `a five-thousand-object world advances every actor and stays within budget over 100 frames`() {
        val world = driftingWorld(5_000)
        val startXs = world.gameObjects.filterIsInstance<Actor>().map { it.location.x }

        world.tickTimes(5) // warm up
        val elapsedMillis = measureNanoTime { world.tickTimes(100) } / 1_000_000

        world.gameObjects.filterIsInstance<Actor>().forEachIndexed { index, actor ->
            // 5 warm-up + 100 timed frames at 10 units/frame along angle 0
            assertEquals(startXs[index] + 105 * 10.0, actor.location.x, absoluteTolerance = 1e-6)
        }
        assertTrue(elapsedMillis < 8_000, "100 ticks of a 5000-object world took ${elapsedMillis}ms")
    }

    @Test
    fun `per-frame cost grows roughly linearly with object count`() {
        fun frameNanos(count: Int): Long {
            val world = driftingWorld(count)
            world.tickTimes(10) // warm up
            return measureNanoTime { world.tickTimes(40) } / 40
        }

        val small = frameNanos(1_500)
        val large = frameNanos(6_000)

        // 4x the objects: linear would be ~4x per frame, quadratic ~16x. Allow generous headroom
        // for tree-shape and allocation noise but still catch a quadratic regression.
        assertTrue(
            large < small * 10,
            "a 4x larger world cost ${large}ns/frame vs ${small}ns/frame - worse than linear scaling"
        )
    }

    @Test
    fun `a heavy heterogeneous world ticks without error and keeps its quadtree consistent`() {
        val world = World().apply {
            repeat(3_000) { add(Actor(location = Point(random.nextDouble(-5e3, 5e3), random.nextDouble(-5e3, 5e3)))) }
            repeat(3_000) {
                add(VisibleObject(location = Point(random.nextDouble(-5e3, 5e3), random.nextDouble(-5e3, 5e3))))
            }
            repeat(2_000) {
                add(
                    Actor(location = Point(random.nextDouble(-5e3, 5e3), random.nextDouble(-5e3, 5e3)))
                        .apply { active = false }
                )
            }
        }

        repeat(20) { world.tick() }

        val indexed = world.quadtree.retrieveBox(-1e9, -1e9, 1e9, 1e9)
        val indexable = world.gameObjects.filterIsInstance<VisibleObject>()
        assertEquals(
            indexable.size,
            indexed.size,
            "every VisibleObject the world owns should be indexed exactly once after a tick"
        )
        assertEquals(8_000, indexable.size, "sanity: the world still owns everything it was given")
    }
}
