package com.spartanlabs.gaming.world.map

/**
 * A kind of ground a [TerrainLayer] tile can be, shared across every tile referencing the same
 * palette entry.
 *
 * @property walkable whether an object may stand on a tile of this type
 * @property movementCost the pathfinding cost of crossing a tile of this type - defined now for
 *   Phase 1 item 4 (pathfinding), unused by this item
 * @property heightLevel the tile's elevation band - defined now for Phase 1 item 5 (line-of-sight
 *   occlusion), unused by this item
 * @property blocksVision whether a tile of this type blocks line of sight - ditto, unused by this
 *   item
 */
data class TerrainType(
    val walkable: Boolean,
    val movementCost: Double,
    val heightLevel: Int,
    val blocksVision: Boolean,
)
