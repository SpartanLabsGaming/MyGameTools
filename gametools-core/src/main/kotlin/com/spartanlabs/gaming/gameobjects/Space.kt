package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
//endregion

/**
 * A bounded playfield: the port a game's map implementation satisfies so [World] and its
 * systems can query bounds and walkability without depending on any particular map
 * representation. `gametools-world`'s `TiledMap` is the framework-provided implementation.
 *
 * A [World] with no [Space] simulates in the pre-Phase-1 unbounded plane - every coordinate is
 * in bounds and walkable. Adopting a [Space] is additive and opt-in.
 */
interface Space {

    /** The playfield's extent, in world coordinates. */
    val bounds: Square

    /**
     * Whether [point] falls within [bounds].
     *
     * @param point the world coordinate to test
     * @return `true` if [point] is inside the playfield's extent
     */
    fun contains(point: Point): Boolean

    /**
     * Whether an object could stand at [point] - in bounds and not blocked by terrain or static
     * geometry. A [point] outside [bounds] is never walkable.
     *
     * @param point the world coordinate to test
     * @return `true` if [point] is open ground
     */
    fun isWalkable(point: Point): Boolean
}
