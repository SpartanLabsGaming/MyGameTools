package com.spartanlabs.gaming.world.map

/**
 * A `widthTiles x heightTiles` grid of [TerrainType]s, stored as a flat, row-major list of
 * indices into a shared [palette] - the same shape Tiled's own TMX layer format uses.
 *
 * @param widthTiles the grid's width, in tiles; must be positive
 * @param heightTiles the grid's height, in tiles; must be positive
 * @param tileTypeIndices flat, row-major palette indices; `index = y * widthTiles + x`. Its size
 *   must equal `widthTiles * heightTiles`, and every entry must be a valid index into [palette].
 * @param palette the distinct [TerrainType]s [tileTypeIndices] references; must not be empty
 * @throws IllegalArgumentException if [widthTiles] / [heightTiles] are not positive, [palette] is
 *   empty, [tileTypeIndices]'s size does not match the grid's tile count, or any entry of
 *   [tileTypeIndices] is out of range for [palette]
 */
class TerrainLayer(
    val widthTiles: Int,
    val heightTiles: Int,
    tileTypeIndices: List<Int>,
    palette: List<TerrainType>,
) {
    init {
        require(widthTiles > 0 && heightTiles > 0) { "widthTiles/heightTiles must be positive" }
        require(palette.isNotEmpty()) { "palette must not be empty" }
        require(tileTypeIndices.size == widthTiles * heightTiles) {
            "tileTypeIndices.size (${tileTypeIndices.size}) must equal widthTiles*heightTiles (${widthTiles * heightTiles})"
        }
        tileTypeIndices.forEachIndexed { i, p ->
            require(p in palette.indices) { "tileTypeIndices[$i] = $p is out of range for a palette of ${palette.size}" }
        }
    }

    private val tileTypeIndices = tileTypeIndices.toList()
    private val palette = palette.toList()

    /**
     * The [TerrainType] at [tile], or [Result.failure] if [tile] falls outside the grid - an
     * out-of-range tile lookup is an operational condition a caller (pathfinding, an LOS
     * raycast walking off the grid edge) can hit routinely, not a programmer error.
     *
     * @param tile the grid coordinate to look up
     * @return the tile's [TerrainType], or a failed [Result] carrying an
     *   [IndexOutOfBoundsException] if [tile] is outside the grid
     */
    fun terrainAt(tile: TileIndex): Result<TerrainType> =
        if (tile.x !in 0 until widthTiles || tile.y !in 0 until heightTiles)
            Result.failure(IndexOutOfBoundsException("$tile is outside a ${widthTiles}x$heightTiles grid"))
        else Result.success(palette[tileTypeIndices[tile.y * widthTiles + tile.x]])
}
