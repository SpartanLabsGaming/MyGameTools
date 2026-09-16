package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.world.zone.ZoneGrid
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
 * Level 2 - component. [ZoneGrid]'s partition shape (size, row-major order, full-extent
 * tiling), [ZoneGrid.zoneAt]'s interior/boundary/out-of-bounds resolution in both `clamped`
 * modes, and the constructor's `require()` guard.
 */
class ZoneGridTest {

    /** A minimal [Space] with fixed [bounds] and no walkability logic - all [ZoneGrid] reads is [Space.bounds]. */
    private class FixtureSpace(override val bounds: Square) : Space {
        override fun contains(point: Point): Boolean = bounds.contains(point)
        override fun isWalkable(point: Point): Boolean = contains(point)
    }

    /** A 40x30 space, tiled by a 4x3 grid into 10x10 zones - matches `fixture-map.json`'s own extent. */
    private fun fixtureSpace(): Space = FixtureSpace(Square(Point(0.0, 0.0), Dimensions(40.0, 30.0)))

    private fun fixtureGrid(columns: Int = 4, rows: Int = 3): ZoneGrid = ZoneGrid(fixtureSpace(), columns, rows)

    @Test
    fun `zones size is columns times rows, in row-major order`() {
        val grid = fixtureGrid(columns = 4, rows = 3)

        assertEquals(12, grid.zones.size)
        grid.zones.forEachIndexed { index, zone ->
            assertEquals(index % 4, zone.column)
            assertEquals(index / 4, zone.row)
        }
    }

    @Test
    fun `columns and rows read back as the constructor values`() {
        val grid = fixtureGrid(columns = 4, rows = 3)

        assertEquals(4, grid.columns)
        assertEquals(3, grid.rows)
    }

    @Test
    fun `zones tile the space's full extent with no gap or overlap`() {
        val space = fixtureSpace()
        val grid = ZoneGrid(space, columns = 4, rows = 3)

        val spaceArea = space.bounds.dimensions.width * space.bounds.dimensions.height
        val summedZoneArea = grid.zones.sumOf { it.bounds.dimensions.width * it.bounds.dimensions.height }
        assertEquals(spaceArea, summedZoneArea)

        // Adjacent zones (same row, columns c and c+1) share exactly one edge: the right edge of
        // one is exactly the left edge of the other, with matching vertical extent.
        grid.zones.forEach { zone ->
            val eastNeighbor = grid.zones.find { it.row == zone.row && it.column == zone.column + 1 }
            if (eastNeighbor != null) {
                assertEquals(zone.bounds.location.x + zone.bounds.dimensions.width, eastNeighbor.bounds.location.x)
                assertEquals(zone.bounds.location.y, eastNeighbor.bounds.location.y)
                assertEquals(zone.bounds.dimensions.height, eastNeighbor.bounds.dimensions.height)
            }
            val southNeighbor = grid.zones.find { it.column == zone.column && it.row == zone.row + 1 }
            if (southNeighbor != null) {
                assertEquals(zone.bounds.location.y + zone.bounds.dimensions.height, southNeighbor.bounds.location.y)
                assertEquals(zone.bounds.location.x, southNeighbor.bounds.location.x)
                assertEquals(zone.bounds.dimensions.width, southNeighbor.bounds.dimensions.width)
            }
        }
    }

    @Test
    fun `zoneAt resolves an interior point to the correct zone`() {
        val grid = fixtureGrid()

        val zone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()

        assertEquals(1, zone.column)
        assertEquals(0, zone.row)
    }

    @Test
    fun `zoneAt on an internal boundary floors to the next zone, mirroring TiledMap's own edge-flooring case`() {
        val grid = fixtureGrid()

        val zone = grid.zoneAt(Point(10.0, 0.0)).getOrThrow()

        assertEquals(1, zone.column)
        assertEquals(0, zone.row)
    }

    @Test
    fun `zoneAt with clamped = false fails on the far edge and on a negative out-of-bounds point`() {
        val grid = fixtureGrid()

        val farEdge = grid.zoneAt(Point(40.0, 30.0), clamped = false)
        assertTrue(farEdge.isFailure)
        assertIs<IndexOutOfBoundsException>(farEdge.exceptionOrNull())

        val negative = grid.zoneAt(Point(-5.0, -5.0), clamped = false)
        assertTrue(negative.isFailure)
        assertIs<IndexOutOfBoundsException>(negative.exceptionOrNull())
    }

    @Test
    fun `zoneAt with clamped = true (default) succeeds for out-of-bounds points, clamping to the nearest edge or corner zone`() {
        val grid = fixtureGrid()

        val farEdge = grid.zoneAt(Point(40.0, 30.0)).getOrThrow()
        assertEquals(3, farEdge.column)
        assertEquals(2, farEdge.row)

        val negative = grid.zoneAt(Point(-5.0, -5.0)).getOrThrow()
        assertEquals(0, negative.column)
        assertEquals(0, negative.row)

        // Far outside on both axes still clamps to the correct corner, not just "some" zone.
        val farOutside = grid.zoneAt(Point(1000.0, -1000.0)).getOrThrow()
        assertEquals(3, farOutside.column)
        assertEquals(0, farOutside.row)
    }

    @Test
    fun `constructor rejects non-positive columns or rows`() {
        val space = fixtureSpace()

        assertFailsWith<IllegalArgumentException> { ZoneGrid(space, columns = 0, rows = 1) }
        assertFailsWith<IllegalArgumentException> { ZoneGrid(space, columns = 1, rows = 0) }
        assertFailsWith<IllegalArgumentException> { ZoneGrid(space, columns = -1, rows = 1) }
    }
}
