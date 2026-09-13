package com.spartanlabs.gaming.world.map

/**
 * An integer grid coordinate into a [TerrainLayer] - the tile-space analogue of
 * [com.spartanlabs.geometry.Point], which addresses continuous world coordinates instead.
 *
 * @property x the column, `0`-based from the grid's west edge
 * @property y the row, `0`-based from the grid's north edge (the engine's world space is
 *   y-down: `y` grows downward, matching [com.spartanlabs.gaming.gameobjects.Space]'s own
 *   convention)
 */
data class TileIndex(val x: Int, val y: Int)
