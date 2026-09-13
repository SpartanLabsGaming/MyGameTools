package com.spartanlabs.gaming.world.map

//region 2. Intended Function
import kotlinx.serialization.json.Json
//endregion

/**
 * Builds a [TiledMap] from a [MapDefinition] or its JSON encoding. No file IO happens here - a
 * consumer reads the file/asset itself and hands the text to [fromJson], or an already-decoded
 * [MapDefinition] to [fromDefinition].
 *
 * [fromJson] decodes with `ignoreUnknownKeys = true`: a JSON key that is not one of
 * [MapDefinition]'s properties - including a typo'd one - is silently dropped rather than
 * rejected; it does not surface as a [Result.failure].
 *
 * Stateless and safe to call from multiple threads concurrently - each call decodes/builds a
 * fresh [TiledMap] from its own input, touching no shared mutable state.
 */
object MapLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes [source] as a [MapDefinition] and builds the [TiledMap] it describes.
     *
     * @param source the map's JSON text
     * @return the built [TiledMap], or [Result.failure] if [source] fails to decode or the
     *   decoded [MapDefinition] is structurally invalid
     */
    fun fromJson(source: String): Result<TiledMap> =
        runCatching { json.decodeFromString(MapDefinition.serializer(), source) }
            .mapCatching(::buildTiledMap)

    /**
     * Builds the [TiledMap] [definition] describes.
     *
     * @param definition the map's pure-data description
     * @return the built [TiledMap], or [Result.failure] if [definition] is structurally invalid
     */
    fun fromDefinition(definition: MapDefinition): Result<TiledMap> = runCatching { buildTiledMap(definition) }

    /** Assembles the domain [TerrainLayer]/[StaticGeometry]/[SpawnPoint]s a [TiledMap] needs from [definition]. */
    private fun buildTiledMap(definition: MapDefinition): TiledMap = with(definition) {
        TiledMap(
            widthTiles, heightTiles, tileSize,
            terrain = TerrainLayer(widthTiles, heightTiles, tiles, terrainPalette.map { it.toDomain() }),
            staticGeometry = StaticGeometry(obstacles.map { it.toDomain() }),
            spawnPoints = spawnPoints.map { it.toDomain() },
        )
    }
}
