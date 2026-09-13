package com.spartanlabs.gaming.testing.nonfunctional.spatial

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
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
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional validation isolating exactly the two [World] spatial-index update
 * strategies against each other: repeated calls to [World.reconcileSpatialIndex] (the
 * incremental reconcile [World.tick] uses, `internal`-exposed for this benchmark only) against
 * repeated calls to [World.reindexSpatial] (the pre-`5.2.0` clear-and-reinsert-everything
 * rebuild, kept available as this comparison's same-process baseline) - both driven against a
 * mostly-static ~10k-object field with the same small fraction of entities actually moving
 * between calls. Calling each update method directly, rather than going through a full
 * [World.tick], keeps unrelated per-tick work ([World.tickCount] bookkeeping, the `byId` reindex,
 * every [com.spartanlabs.gaming.gameobjects.GameObject]'s own tick, event publishing) out of
 * the measurement, so this is a true reconcile-vs-rebuild A/B rather than a full tick compared
 * against a bare index rebuild. Correctness of the reconcile itself is covered in
 * [com.spartanlabs.gaming.testing.component.gameobjects.WorldSpatialIndexTest].
 */
class SpatialIndexScalabilityTest {

    /**
     * A [World] of [count] [VisibleObject]s built from a fresh [Random] seeded with [seed] - so
     * two worlds built with the same [seed] hold identical entities at identical positions,
     * making them a matched pair for comparison rather than two independent random draws - paired
     * with the subset of those objects ([movingFraction] of [count]) this benchmark moves between
     * update calls.
     */
    private fun mostlyStaticWorld(count: Int, movingFraction: Double, seed: Long): Pair<World, List<VisibleObject>> {
        val random = Random(seed)
        val world = World()
        val movers = mutableListOf<VisibleObject>()
        repeat(count) {
            val location = Point(random.nextDouble(-5_000.0, 5_000.0), random.nextDouble(-5_000.0, 5_000.0))
            val obj = VisibleObject(location = location)
            if (random.nextDouble() < movingFraction) movers += obj
            world.add(obj)
        }
        return world to movers
    }

    /** Nudges every one of [movers] +x by a fixed step, simulating one tick's worth of movement. */
    private fun advance(movers: List<VisibleObject>) {
        movers.forEach { it.location += Point(10.0, 0.0) }
    }

    @Test
    fun `World's incremental reconcile does materially less work than a full rebuild for a mostly-static 10k-object field`() {
        val seed = 61_2026L
        val (reconcileWorld, reconcileMovers) = mostlyStaticWorld(10_000, movingFraction = 0.05, seed = seed)
        val (rebuildWorld, rebuildMovers) = mostlyStaticWorld(10_000, movingFraction = 0.05, seed = seed)

        // One full pass each to index every object once - the reconcile's own one-time worst
        // case - before any measurement starts.
        reconcileWorld.reconcileSpatialIndex()
        rebuildWorld.reindexSpatial()

        // Warm up both paths - including the movement they'll see under measurement - before the
        // JIT has settled on either loop.
        repeat(5) {
            advance(reconcileMovers)
            reconcileWorld.reconcileSpatialIndex()
            advance(rebuildMovers)
            rebuildWorld.reindexSpatial()
        }

        val reconcileNanos = measureNanoTime {
            repeat(100) {
                advance(reconcileMovers)
                reconcileWorld.reconcileSpatialIndex()
            }
        }
        val rebuildNanos = measureNanoTime {
            repeat(100) {
                advance(rebuildMovers)
                rebuildWorld.reindexSpatial()
            }
        }

        assertTrue(
            reconcileNanos < rebuildNanos,
            "100 World.reconcileSpatialIndex() calls (${reconcileNanos}ns) should beat 100 " +
                "World.reindexSpatial() calls (${rebuildNanos}ns) for a mostly-static field"
        )
    }
}
