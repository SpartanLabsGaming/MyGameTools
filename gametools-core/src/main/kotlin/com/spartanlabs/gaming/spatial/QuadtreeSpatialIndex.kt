package com.spartanlabs.gaming.spatial

//region 3. Utility / Catch-all
// 3.1 Java Standard library
import java.util.IdentityHashMap
//endregion

/**
 * A [SpatialIndex] that wraps a [Quadtree] rather than reimplementing indexing - the default
 * backing for [World.spatialIndex][com.spartanlabs.gaming.gameobjects.World.spatialIndex],
 * chosen because it reproduces `5.1.0`'s exact [Quadtree.retrieveBox] query semantics.
 *
 * [Quadtree.retrieveBox] returns only elements, never their positions, which is not enough to
 * filter [queryRadius] accurately by true distance. Rather than adding position recovery to
 * [Quadtree] itself (kept behaviourally untouched by design), this class keeps its own
 * [positions] map - an [IdentityHashMap] from each currently-indexed element to its last known
 * `(x, y)` - updated in lockstep by every mutating call, and consulted only by [queryRadius].
 *
 * Not thread-safe, per [SpatialIndex]'s own contract.
 *
 * @param E the element type stored at each indexed position
 * @param tree the backing tree, empty by default; exposed `internal` so
 *   [World.quadtree][com.spartanlabs.gaming.gameobjects.World.quadtree]'s deprecated accessor
 *   can hand back the live tree with zero copying when this is the world's backing index
 */
class QuadtreeSpatialIndex<E>(internal val tree: Quadtree<Double, E> = Quadtree()) : SpatialIndex<E> {

    /** Last known `(x, y)` per currently-indexed element, by reference identity, for [queryRadius]. */
    private val positions: IdentityHashMap<E & Any, DoubleArray> = IdentityHashMap()

    /** Delegates to [Quadtree.insert] and records [element]'s position for [queryRadius]. */
    override fun insert(x: Double, y: Double, element: E) {
        tree.insert(x, y, element)
        element?.let { positions[it] = doubleArrayOf(x, y) }
    }

    /**
     * Removes [element] from ([fromX], [fromY]) and re-inserts it at ([toX], [toY]) - a full
     * root-to-leaf walk on each half, with no reuse shortcut: [Quadtree.insert] no longer
     * repurposes a dead slot for an unrelated element (that reuse was removed as unsound - it
     * silently mis-positioned the moved element at its old coordinates). This is `O(depth)` per
     * half, not the `O(1)` amortised cost [UniformGrid] offers instead.
     */
    override fun move(fromX: Double, fromY: Double, toX: Double, toY: Double, element: E) {
        tree.remove(fromX, fromY, element)
        tree.insert(toX, toY, element)
        element?.let { positions[it] = doubleArrayOf(toX, toY) }
    }

    /** Delegates to [Quadtree.remove] and drops [element]'s tracked position. */
    override fun remove(x: Double, y: Double, element: E) {
        tree.remove(x, y, element)
        element?.let { positions.remove(it) }
    }

    /** Delegates to [Quadtree.retrieveBox] verbatim, preserving its exact half-open box semantics. */
    override fun queryBox(minX: Double, minY: Double, maxX: Double, maxY: Double): List<E> =
        tree.retrieveBox(minX, minY, maxX, maxY)

    /**
     * Retrieves candidates from [Quadtree.retrieveBox] over the query circle's bounding box,
     * then filters to a true Euclidean circle using each candidate's tracked [positions] entry
     * - the bounding box alone would wrongly include a candidate in the box's corner but outside
     * the circle.
     */
    override fun queryRadius(x: Double, y: Double, radius: Double): List<E> {
        val radiusSquared = radius * radius
        return tree.retrieveBox(x - radius, y - radius, x + radius, y + radius).filter { candidate ->
            val position = candidate?.let { positions[it] } ?: return@filter false
            val dx = position[0] - x
            val dy = position[1] - y
            dx * dx + dy * dy <= radiusSquared
        }
    }

    /** Clears both [tree] and [positions]. */
    override fun clear() {
        tree.clear()
        positions.clear()
    }
}
