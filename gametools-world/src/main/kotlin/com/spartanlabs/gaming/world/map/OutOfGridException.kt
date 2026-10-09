package com.spartanlabs.gaming.world.map

/**
 * Signals that a terrain lookup fell outside the grid: [TerrainLayer.terrainAt] was asked for a
 * [tile] beyond its `widthTiles x heightTiles` extent, or [TiledMap.terrainAt] for a point whose
 * tile is - which includes a point exactly on the map's far edge (see [TiledMap.tileAt]).
 *
 * It is the *value* of an expected outcome: neither lookup throws it, both return it inside
 * [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is an
 * [IndexOutOfBoundsException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs two small objects - the
 * [Result] failure wrapper and this exception - and no stack walk. Diagnose from the message and [tile].
 *
 * @property tile the tile that was looked up
 * @see TerrainLayer.terrainAt
 * @see TiledMap.terrainAt
 */
class OutOfGridException(val tile: TileIndex) :
    IndexOutOfBoundsException("tile (${tile.x}, ${tile.y}) is outside the terrain grid") {

    // Throwable's constructor calls this before `tile` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
