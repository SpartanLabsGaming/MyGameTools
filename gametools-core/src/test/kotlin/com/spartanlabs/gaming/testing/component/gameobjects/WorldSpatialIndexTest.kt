package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.SpatialIndex
import com.spartanlabs.gaming.spatial.UniformGrid
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * The central regression suite for the spatial-index rework: [World.spatialIndex]'s default and
 * deprecated-accessor behaviour, [World.tick]'s incremental reconcile (proved by a spy, not by
 * timing), parity between the default [QuadtreeSpatialIndex] and a swapped-in [UniformGrid], and
 * the removal-mid-tick edge case [World.tick]'s `removeList` drain handles specially.
 */
class WorldSpatialIndexTest {

    /** An actor at ([x], [y]) that travels +x at [Actor.speed] (10) units per tick. */
    private fun directionalActor(x: Double, y: Double) = Actor(location = Point(x, y)).apply {
        movement = Movement.Directional
        angle = 0
    }

    /** A [VisibleObject] that moves itself, then queues its own removal, on the same tick. */
    private class SelfRemovingMover(private val world: World, location: Point) : VisibleObject(location = location) {
        public override fun onUpdate() {
            location += Point(10.0, 0.0)
            world.removeList += this
        }
    }

    /** A non-visible [GameObject] that adds [toSpawn] to [world] via [World.add] during its own tick. */
    private class Spawner(private val world: World, private val toSpawn: VisibleObject) : GameObject() {
        public override fun onUpdate() {
            world.add(toSpawn)
        }
    }

    /** A hand-written [SpatialIndex] spy that records every call it receives, backed by a real [QuadtreeSpatialIndex] so queries still work. */
    private class SpySpatialIndex : SpatialIndex<VisibleObject> {
        private val delegate = QuadtreeSpatialIndex<VisibleObject>()

        val inserted = mutableListOf<VisibleObject>()
        val moved = mutableListOf<VisibleObject>()
        var cleared = false
            private set

        override fun insert(x: Double, y: Double, element: VisibleObject) {
            inserted += element
            delegate.insert(x, y, element)
        }

        override fun move(fromX: Double, fromY: Double, toX: Double, toY: Double, element: VisibleObject) {
            moved += element
            delegate.move(fromX, fromY, toX, toY, element)
        }

        override fun remove(x: Double, y: Double, element: VisibleObject) {
            delegate.remove(x, y, element)
        }

        override fun queryBox(minX: Double, minY: Double, maxX: Double, maxY: Double): List<VisibleObject> =
            delegate.queryBox(minX, minY, maxX, maxY)

        override fun queryRadius(x: Double, y: Double, radius: Double): List<VisibleObject> =
            delegate.queryRadius(x, y, radius)

        override fun clear() {
            cleared = true
            delegate.clear()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a new world defaults to a QuadtreeSpatialIndex and quadtree delegates to it without rebuilding`() {
        val world = World()

        assertTrue(world.spatialIndex is QuadtreeSpatialIndex<VisibleObject>)
        val first = world.quadtree
        val second = world.quadtree
        assertTrue(first === second, "the deprecated accessor should hand back the same live tree, not rebuild")
    }

    @Test
    fun `reconcile inserts only new objects, moves only objects whose location changed, and never clears`() {
        val world = World()
        val spy = SpySpatialIndex()
        world.spatialIndex = spy
        val moving = directionalActor(0.0, 0.0)
        val still = VisibleObject(location = Point(100.0, 100.0))
        world.gameObjects += listOf(moving, still)

        world.tick() // both are seen for the first time: both inserted, nothing has moved yet

        assertEquals(listOf<VisibleObject>(moving, still), spy.inserted)
        assertTrue(spy.moved.isEmpty())
        assertFalse(spy.cleared)

        spy.inserted.clear()
        world.tick() // moving relocated during the first tick's pass; still did not move

        assertTrue(spy.inserted.isEmpty(), "no new object arrived on the second tick")
        assertEquals(listOf<VisibleObject>(moving), spy.moved)
        assertFalse(spy.cleared, "the incremental reconcile never clears the index")
    }

    @Test
    fun `a UniformGrid-backed world exhibits the same indexing behaviour as the default Quadtree-backed world`() {
        val quadtreeWorld = World()
        val gridWorld = World().apply {
            spatialIndex = UniformGrid(cellSize = 50.0)
            reindexSpatial()
        }
        val quadtreeActor = directionalActor(0.0, 0.0)
        val gridActor = directionalActor(0.0, 0.0)
        quadtreeWorld.gameObjects += quadtreeActor
        gridWorld.gameObjects += gridActor

        quadtreeWorld.tick() // indexes (0,0), then the actor moves to (10,0)
        gridWorld.tick()

        listOf(quadtreeWorld to quadtreeActor, gridWorld to gridActor).forEach { (world, actor) ->
            assertTrue(actor in world.spatialIndex.queryBox(-1.0, -1.0, 1.0, 1.0))
            assertFalse(actor in world.spatialIndex.queryBox(9.0, -1.0, 11.0, 1.0))
        }

        quadtreeWorld.tick() // now indexes (10,0)
        gridWorld.tick()

        listOf(quadtreeWorld to quadtreeActor, gridWorld to gridActor).forEach { (world, actor) ->
            assertTrue(actor in world.spatialIndex.queryBox(9.0, -1.0, 11.0, 1.0))
            assertTrue(
                world.spatialIndex.queryBox(-1.0, -1.0, 1.0, 1.0).isEmpty(),
                "the stale position from the first tick should not linger"
            )
        }
    }

    @Test
    fun `removing an object mid-tick after it moved leaves no trace at either its old or new position`() {
        val world = World()
        val mover = SelfRemovingMover(world, Point(0.0, 0.0))
        world.gameObjects += mover

        world.tick() // reconciled at (0,0), then moves to (10,0) and queues its own removal

        assertTrue(world.spatialIndex.queryBox(-1.0, -1.0, 1.0, 1.0).isEmpty(), "no trace at the old position")
        assertTrue(world.spatialIndex.queryBox(9.0, -1.0, 11.0, 1.0).isEmpty(), "no trace at the new position")
        assertTrue(mover !in world.gameObjects)
    }

    @Test
    fun `a same-tick removal and a same-tick addition do not interfere with each other's indexing`() {
        val world = World()
        val mover = SelfRemovingMover(world, Point(0.0, 0.0))
        val newcomer = VisibleObject(location = Point(50.0, 50.0))
        world.gameObjects += listOf(mover, Spawner(world, newcomer))

        // mover is reconciled at (0,0), moves to (10,0), and queues its own removal; the spawner
        // adds newcomer via World.add before the removeList drain runs
        world.tick()

        assertTrue(
            world.spatialIndex.queryBox(-1.0, -1.0, 1.0, 1.0).isEmpty(),
            "the removed mover leaves no trace at its old position"
        )
        assertTrue(
            world.spatialIndex.queryBox(9.0, -1.0, 11.0, 1.0).isEmpty(),
            "the removed mover leaves no trace at its new position either"
        )
        assertTrue(mover !in world.gameObjects)
        assertTrue(newcomer in world.gameObjects)

        world.tick() // newcomer is reconciled into the spatial index on its first full tick

        assertTrue(
            newcomer in world.spatialIndex.queryBox(49.0, 49.0, 51.0, 51.0),
            "the newly-added object is correctly indexed and queryable"
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a UniformGrid-backed world's deprecated quadtree accessor still returns correct membership`() {
        val world = World().apply { spatialIndex = UniformGrid(cellSize = 50.0) }
        val actor = directionalActor(5.0, 5.0)
        world.gameObjects += actor

        world.tick() // reconciled at (5,5), then the actor moves to (15,5)

        // The fallback branch rebuilds from gameObjects' *current* positions on every access
        // (no move-tracking marker of its own), so it reflects (15,5), not the pre-move (5,5)
        // the live QuadtreeSpatialIndex branch would have kept until the next reconcile.
        assertEquals(listOf(actor), world.quadtree.retrieveBox(14.0, 4.0, 16.0, 6.0))
        assertTrue(world.quadtree.retrieveBox(4.0, 4.0, 6.0, 6.0).isEmpty())
    }
}
