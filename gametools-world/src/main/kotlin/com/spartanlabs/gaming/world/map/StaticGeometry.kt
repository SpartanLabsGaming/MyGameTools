package com.spartanlabs.gaming.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Point
//endregion

/**
 * The obstacles carved out of a [TiledMap]'s walkable area, independent of [TerrainLayer]. v1 is
 * AABB-only - every obstacle is a [CenteredBox] - matching GeneralTools 2.2.0's collision
 * convention directly rather than an internal box type; polygonal obstacles are a later addition
 * behind the same [blocksPoint] query.
 *
 * @property obstacles every obstacle box making up this map's static geometry
 */
class StaticGeometry(val obstacles: List<CenteredBox>) {

    /**
     * Whether [point] falls inside (or on the edge of) any obstacle.
     *
     * @param point the world coordinate to test
     * @return `true` if [point] is blocked by at least one obstacle
     */
    fun blocksPoint(point: Point): Boolean = obstacles.any { it.contains(point) }
}
