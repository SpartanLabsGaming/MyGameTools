package com.spartanlabs.gaming.spatial

/**
 * A broad-phase spatial index over [E] elements positioned in the 2D plane, maintained
 * incrementally as elements move, join, or leave - unlike [Quadtree], which a caller rebuilds
 * from scratch every frame with [Quadtree.clear] and [Quadtree.insert].
 *
 * An element's identity for [move] and [remove] is its previous indexed position plus the
 * instance itself (by reference equality, mirroring [Quadtree.remove]); an implementation is
 * free to use whatever internal keying gets an element back to its cell or node fastest.
 *
 * Not thread-safe. A [com.spartanlabs.gaming.gameobjects.World] drives one index from a single
 * thread, the way it already drives one [com.spartanlabs.gaming.event.EventBus].
 *
 * @param E the element type stored at each indexed position
 */
interface SpatialIndex<E> {

    /**
     * Indexes [element] at ([x], [y]). Inserting the same element twice without an intervening
     * [remove] is undefined - callers track membership themselves (a [World][com.spartanlabs.gaming.gameobjects.World]
     * inserts once, on arrival).
     *
     * @param x the element's world x coordinate
     * @param y the element's world y coordinate
     * @param element the element to index
     */
    fun insert(x: Double, y: Double, element: E)

    /**
     * Relocates [element] from ([fromX], [fromY]) to ([toX], [toY]) without a full [remove] +
     * [insert] rebuild where the implementation can do better - the incremental alternative to
     * clearing and reinserting an entire index each tick.
     *
     * @param fromX the x coordinate [element] was last indexed at
     * @param fromY the y coordinate [element] was last indexed at
     * @param toX the x coordinate to index [element] at now
     * @param toY the y coordinate to index [element] at now
     * @param element the element being relocated
     */
    fun move(fromX: Double, fromY: Double, toX: Double, toY: Double, element: E)

    /**
     * Removes [element], previously indexed at ([x], [y]), from this index. A no-op if no such
     * element is indexed there.
     *
     * @param x the x coordinate [element] was indexed at
     * @param y the y coordinate [element] was indexed at
     * @param element the element to remove
     */
    fun remove(x: Double, y: Double, element: E)

    /**
     * Every indexed element whose position lies within the axis-aligned box bounded by
     * ([minX], [minY]) and ([maxX], [maxY]), in unspecified order.
     *
     * @param minX the box's lower x bound
     * @param minY the box's lower y bound
     * @param maxX the box's upper x bound
     * @param maxY the box's upper y bound
     * @return the elements found, empty if none
     */
    fun queryBox(minX: Double, minY: Double, maxX: Double, maxY: Double): List<E>

    /**
     * Every indexed element within [radius] of ([x], [y]), in unspecified order.
     *
     * @param x the query circle's centre x coordinate
     * @param y the query circle's centre y coordinate
     * @param radius the query circle's radius
     * @return the elements found, empty if none
     */
    fun queryRadius(x: Double, y: Double, radius: Double): List<E>

    /** Empties this index of every element. */
    fun clear()
}
