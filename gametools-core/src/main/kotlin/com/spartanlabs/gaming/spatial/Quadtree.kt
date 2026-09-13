package com.spartanlabs.gaming.spatial

/**
 * A point-region quadtree that indexes elements by a 2D key for fast rectangular-range
 * queries.
 *
 * Each node stores one ([x], [y]) point with its element and splits the rest of the plane into
 * four quadrants about that point, named as if `+y` were north - a labelling convention
 * internal to the tree's own logic, independent of the caller's coordinate convention (the
 * engine's own world space is y-down: `-y` is up). The tree only ever compares magnitudes, so
 * the two conventions never need to agree. The tree is unbalanced: insertion order determines
 * its shape. It is not thread-safe; a typical game rebuilds it once per frame with [clear]
 * followed by [insert].
 *
 * A node whose element is [remove]d is never reclaimed - it persists as a permanent
 * routing waypoint until the whole tree is discarded by [clear], so a tree that lives across
 * many [remove]/[insert] cycles (e.g. one backing an incrementally-reconciled index) can
 * accumulate dead nodes and grow in node count - and potentially depth - over its lifetime.
 * A caller that needs to bound this should periodically discard and rebuild the tree
 * (`clear()` followed by re-`insert()`-ing every live element) rather than rely on any
 * automatic compaction, which this class does not perform.
 *
 * Ported from the original `com.DotA3.main.Quadtree`.
 *
 * @param N the coordinate type, ordered via [Comparable]
 * @param E the element type stored at each point
 */
class Quadtree<N : Comparable<N>, E> {

    private var root: Node? = null

    private inner class Node(val x: N, val y: N, var element: E?) {
        var northWest: Node? = null
        var northEast: Node? = null
        var southEast: Node? = null
        var southWest: Node? = null
    }

    /**
     * Inserts [element] at ([x], [y]), always creating a fresh node at that position. A node
     * on the path whose element has been [remove]d is never reused for this new element -
     * it survives purely as a routing waypoint, since its own ([x], [y]) is fixed at
     * construction and reusing it for an unrelated point would desynchronize it from the
     * tree-structural invariant [retrieveBox]'s descent depends on.
     */
    fun insert(x: N, y: N, element: E) {
        root = insert(root, x, y, element)
    }

    private fun insert(node: Node?, x: N, y: N, element: E): Node {
        if (node == null) return Node(x, y, element)

        val lessX = x < node.x
        val lessY = y < node.y
        when {
            lessX && !lessY -> node.northWest = insert(node.northWest, x, y, element)
            !lessX && !lessY -> node.northEast = insert(node.northEast, x, y, element)
            !lessX && lessY -> node.southEast = insert(node.southEast, x, y, element)
            else -> node.southWest = insert(node.southWest, x, y, element)
        }
        return node
    }

    /**
     * Returns every indexed element whose point lies in the half-open box
     * `(minX, maxX] x (minY, maxY]`, in unspecified order.
     */
    fun retrieveBox(minX: N, minY: N, maxX: N, maxY: N): List<E> =
        buildList { retrieveBox(root, minX, minY, maxX, maxY, this) }

    private fun retrieveBox(node: Node?, minX: N, minY: N, maxX: N, maxY: N, into: MutableList<E>) {
        if (node == null) return

        val lessMinX = minX < node.x
        val lessMinY = minY < node.y
        val lessMaxX = maxX < node.x
        val lessMaxY = maxY < node.y

        if (lessMinX && lessMinY && !lessMaxX && !lessMaxY) node.element?.let(into::add)
        if (lessMinX && !lessMaxY) retrieveBox(node.northWest, minX, minY, maxX, maxY, into)
        if (!lessMaxX && !lessMaxY) retrieveBox(node.northEast, minX, minY, maxX, maxY, into)
        if (!lessMaxX && lessMinY) retrieveBox(node.southEast, minX, minY, maxX, maxY, into)
        if (lessMinX && lessMinY) retrieveBox(node.southWest, minX, minY, maxX, maxY, into)
    }

    /**
     * Clears the first node holding [element] (by identity) on the path to ([x], [y]),
     * leaving the node in place as a permanent routing waypoint - it is never reused for a
     * different element ([insert]'s contract), only reclaimed as part of the whole tree when
     * [clear] is called.
     */
    fun remove(x: N, y: N, element: E) {
        remove(root, x, y, element)
    }

    private fun remove(node: Node?, x: N, y: N, element: E) {
        if (node == null) return
        if (node.element === element) {
            node.element = null
            return
        }
        val lessX = x < node.x
        val lessY = y < node.y
        when {
            lessX && !lessY -> remove(node.northWest, x, y, element)
            !lessX && !lessY -> remove(node.northEast, x, y, element)
            !lessX && lessY -> remove(node.southEast, x, y, element)
            else -> remove(node.southWest, x, y, element)
        }
    }

    /** Empties the tree. */
    fun clear() {
        root = null
    }
}
