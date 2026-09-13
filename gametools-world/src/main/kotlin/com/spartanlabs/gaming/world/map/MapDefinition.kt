package com.spartanlabs.gaming.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.CenteredBox
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.serializations.DimensionsSnapshot
import com.spartanlabs.geometry.serializations.PointSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/** An immutable, serializable copy of a [TerrainType]. */
@Serializable
data class TerrainTypeSnapshot(val walkable: Boolean, val movementCost: Double, val heightLevel: Int, val blocksVision: Boolean)

/**
 * An immutable, serializable copy of a [StaticGeometry] obstacle box. [center] and [halfExtents]
 * are in world units, the same convention as [CenteredBox].
 */
@Serializable
data class ObstacleSnapshot(val center: PointSnapshot, val halfExtents: DimensionsSnapshot)

/** An immutable, serializable copy of a [SpawnPoint]. */
@Serializable
data class SpawnPointSnapshot(val name: String, val position: PointSnapshot, val facing: Int? = null, val team: String? = null)

/**
 * The pure-data, wire/file-shape description of a [TiledMap]. [tiles] is flat and row-major
 * (`index = y * widthTiles + x`, matching Tiled's own TMX layer convention), each entry a
 * `0`-based index into [terrainPalette]. [obstacles] centers/half-extents and [spawnPoints]
 * positions are in world units (`tileSize` apart), not tile units. No file IO here - a consumer
 * reads the file/asset and hands the JSON text (or a decoded [MapDefinition]) to [MapLoader].
 *
 * @property widthTiles the map's width, in tiles
 * @property heightTiles the map's height, in tiles
 * @property tileSize the world-unit length of one tile's edge
 * @property tiles the flat, row-major terrain grid, each entry an index into [terrainPalette]
 * @property terrainPalette the distinct [TerrainType]s [tiles] references
 * @property obstacles the map's static-geometry obstacle boxes
 * @property spawnPoints the map's named spawn points
 */
@Serializable
data class MapDefinition(
    val widthTiles: Int,
    val heightTiles: Int,
    val tileSize: Double,
    val tiles: List<Int>,
    val terrainPalette: List<TerrainTypeSnapshot>,
    val obstacles: List<ObstacleSnapshot> = emptyList(),
    val spawnPoints: List<SpawnPointSnapshot> = emptyList(),
)

//region Snapshot -> domain conversions
/** This snapshot as a domain [TerrainType]. */
fun TerrainTypeSnapshot.toDomain(): TerrainType = TerrainType(walkable, movementCost, heightLevel, blocksVision)

/**
 * This snapshot as a domain [CenteredBox], constructing fresh [Point]/[Dimensions] instances -
 * both are mutable value types upstream, so a domain object built from a snapshot never aliases
 * the snapshot's own [PointSnapshot]/[DimensionsSnapshot].
 */
fun ObstacleSnapshot.toDomain(): CenteredBox = CenteredBox(Point(center.x, center.y), Dimensions(halfExtents.width, halfExtents.height))

/** This snapshot as a domain [SpawnPoint], constructing a fresh [Point] (see [ObstacleSnapshot.toDomain]). */
fun SpawnPointSnapshot.toDomain(): SpawnPoint = SpawnPoint(name, Point(position.x, position.y), facing, team)
//endregion
