package com.spartanlabs.gaming.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Space
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.floor
//endregion

/**
 * The bounded, tiled playfield - the framework-provided [Space] implementation. Combines a
 * [terrain] grid, [staticGeometry] obstacles, and named [SpawnPoint]s into the single object a
 * [com.spartanlabs.gaming.gameobjects.World] assigns to
 * [com.spartanlabs.gaming.gameobjects.World.space].
 *
 * The engine's world space is y-down: the origin `(0, 0)` is the map's top-left corner and `y`
 * grows downward.
 *
 * Purely data at this point in the framework - [isWalkable] / [bounds] are not enforced against
 * [com.spartanlabs.gaming.gameobjects.World.tick] or any
 * [com.spartanlabs.gaming.gameobjects.Movement]; a system that wants to enforce them (physics,
 * #49) queries this object itself, mirroring [com.spartanlabs.gaming.gameobjects.World.space]'s
 * own KDoc ("purely descriptive ... no existing behaviour changes when this is set").
 *
 * Not thread-safe: [addSpawnPoint] mutates the spawn-point registry after construction with no
 * synchronization. A [com.spartanlabs.gaming.gameobjects.World] assumes one thread drives it
 * (and whatever [com.spartanlabs.gaming.gameobjects.World.space] it holds), the way it already
 * drives one [com.spartanlabs.gaming.event.EventBus].
 *
 * @param widthTiles the map's width, in tiles; must be positive
 * @param heightTiles the map's height, in tiles; must be positive
 * @param tileSize the world-unit length of one tile's edge; must be positive
 * @param terrain the map's terrain grid; its own `widthTiles`/`heightTiles` must match this map's
 * @param staticGeometry the map's obstacle boxes
 * @param spawnPoints the spawn points this map is built with; names must be unique
 * @throws IllegalArgumentException if [widthTiles] / [heightTiles] / [tileSize] are not
 *   positive, [terrain]'s grid size does not match this map's, or [spawnPoints] contains a
 *   duplicate name
 */
class TiledMap(
    val widthTiles: Int,
    val heightTiles: Int,
    val tileSize: Double,
    val terrain: TerrainLayer,
    val staticGeometry: StaticGeometry,
    spawnPoints: List<SpawnPoint> = emptyList(),
) : Space {
    init {
        require(widthTiles > 0 && heightTiles > 0 && tileSize > 0.0) { "widthTiles/heightTiles/tileSize must be positive" }
        require(terrain.widthTiles == widthTiles && terrain.heightTiles == heightTiles) {
            "terrain's ${terrain.widthTiles}x${terrain.heightTiles} grid does not match the map's ${widthTiles}x$heightTiles"
        }
        val duplicate = spawnPoints.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicate.isEmpty()) { "duplicate spawn point name(s): $duplicate" }
    }

    /** The map's extent in world coordinates: `(0, 0)` to `(widthTiles * tileSize, heightTiles * tileSize)`. */
    override val bounds: Square = Square(Point(0.0, 0.0), Dimensions(widthTiles * tileSize, heightTiles * tileSize))

    override fun contains(point: Point): Boolean = bounds.contains(point)

    /**
     * Floors [point] to its tile coordinate. Always succeeds - may return an index outside the
     * grid for an out-of-bounds [point]; use [terrainAt] / [contains] to test that.
     *
     * @param point the world coordinate to convert
     * @return the tile [point] falls within
     */
    fun tileAt(point: Point): TileIndex = TileIndex(floor(point.x / tileSize).toInt(), floor(point.y / tileSize).toInt())

    /**
     * The terrain at [point], as a [Result]: [Result.success] with the [TerrainType] of the tile
     * [point] falls in, or [Result.failure] carrying an [OutOfGridException] for that tile when it is
     * outside the grid - a normal, frequent outcome near a map edge, including a point exactly on the
     * far edge, which [contains] accepts but which floors to the next tile (see [tileAt]). The failure
     * is [terrain]'s own, from [TerrainLayer.terrainAt], passed through unchanged. Never throws and
     * never returns `null`; a miss costs two small objects - the [Result] failure wrapper and the
     * exception - and no stack walk.
     *
     * @param point the world coordinate to look up
     * @return the [TerrainType] at [point], or a failure carrying an [OutOfGridException] if [point]
     *   is off the grid
     */
    fun terrainAt(point: Point): Result<TerrainType> = terrain.terrainAt(tileAt(point))

    /**
     * Whether an object could stand at [point]: in [bounds], over walkable terrain, and not
     * inside a [staticGeometry] obstacle.
     */
    override fun isWalkable(point: Point): Boolean =
        contains(point) &&
            terrainAt(point).fold(onSuccess = { it.walkable }, onFailure = { false }) &&
            !staticGeometry.blocksPoint(point)

    private val spawnPointsByName: MutableMap<String, SpawnPoint> = spawnPoints.associateByTo(LinkedHashMap()) { it.name }

    /**
     * The spawn point named [name], as a [Result]: [Result.success] with it if this map was built with
     * it or it was added with [addSpawnPoint], otherwise [Result.failure] carrying a
     * [MissingSpawnPointException] for [name]. Never throws and never returns `null`.
     *
     * @param name the spawn point's [SpawnPoint.name]
     * @return the spawn point, or a failure carrying a [MissingSpawnPointException] if none is named
     *   [name]
     */
    fun spawnPoint(name: String): Result<SpawnPoint> =
        spawnPointsByName[name]?.let { Result.success(it) } ?: Result.failure(MissingSpawnPointException(name))

    /**
     * Registers an additional spawn point at runtime - the map owns the ones it was built with;
     * a game may add more.
     *
     * @param spawn the spawn point to add
     * @throws IllegalArgumentException if a spawn point with the same [SpawnPoint.name] already
     *   exists
     */
    fun addSpawnPoint(spawn: SpawnPoint) {
        require(spawn.name !in spawnPointsByName) { "a spawn point named '${spawn.name}' already exists" }
        spawnPointsByName[spawn.name] = spawn
    }
}
