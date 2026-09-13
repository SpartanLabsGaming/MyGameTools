package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TileIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [TerrainLayer]'s palette lookup, its out-of-range handling via [Result]
 * rather than a thrown exception, and its constructor `require()` guards.
 */
class TerrainLayerTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)
    private val water = TerrainType(walkable = false, movementCost = 1.0, heightLevel = 0, blocksVision = true)

    /** A 2x2 grid: (0,0)=grass, (1,0)=water, (0,1)=water, (1,1)=grass. */
    private fun fixtureLayer() = TerrainLayer(
        widthTiles = 2,
        heightTiles = 2,
        tileTypeIndices = listOf(0, 1, 1, 0),
        palette = listOf(grass, water),
    )

    @Test
    fun `terrainAt resolves each tile through the palette`() {
        val layer = fixtureLayer()

        assertEquals(grass, layer.terrainAt(TileIndex(0, 0)).getOrThrow())
        assertEquals(water, layer.terrainAt(TileIndex(1, 0)).getOrThrow())
        assertEquals(water, layer.terrainAt(TileIndex(0, 1)).getOrThrow())
        assertEquals(grass, layer.terrainAt(TileIndex(1, 1)).getOrThrow())
    }

    @Test
    fun `terrainAt fails with Result rather than throwing for an out-of-range tile on every edge`() {
        val layer = fixtureLayer()

        listOf(
            TileIndex(-1, 0), TileIndex(0, -1),
            TileIndex(2, 0), TileIndex(0, 2),
        ).forEach { outside ->
            val result = layer.terrainAt(outside)
            assertTrue(result.isFailure, "$outside should be outside the grid")
            assertIs<IndexOutOfBoundsException>(result.exceptionOrNull())
        }
    }

    @Test
    fun `constructor rejects a tileTypeIndices size that does not match widthTiles times heightTiles`() {
        assertFailsWith<IllegalArgumentException> {
            TerrainLayer(widthTiles = 2, heightTiles = 2, tileTypeIndices = listOf(0, 0, 0), palette = listOf(grass))
        }
    }

    @Test
    fun `constructor rejects a tileTypeIndices entry out of range for the palette`() {
        assertFailsWith<IllegalArgumentException> {
            TerrainLayer(widthTiles = 2, heightTiles = 1, tileTypeIndices = listOf(0, 1), palette = listOf(grass))
        }
    }

    @Test
    fun `constructor rejects an empty palette`() {
        assertFailsWith<IllegalArgumentException> {
            TerrainLayer(widthTiles = 1, heightTiles = 1, tileTypeIndices = listOf(0), palette = emptyList())
        }
    }

    @Test
    fun `constructor rejects non-positive widthTiles or heightTiles`() {
        assertFailsWith<IllegalArgumentException> {
            TerrainLayer(widthTiles = 0, heightTiles = 1, tileTypeIndices = emptyList(), palette = listOf(grass))
        }
        assertFailsWith<IllegalArgumentException> {
            TerrainLayer(widthTiles = 1, heightTiles = 0, tileTypeIndices = emptyList(), palette = listOf(grass))
        }
    }
}
