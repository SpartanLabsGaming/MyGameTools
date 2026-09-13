package com.spartanlabs.gaming.spatial

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
//endregion

/**
 * A [SpatialIndex] backed by a uniform grid of square cells [cellSize] on a side, keyed by
 * packed `(cellX, cellY)` coordinates in a `HashMap`. Well suited to a roughly uniform-density
 * field (the common RTS/MOBA case) where every cell holds a similar number of elements: every
 * operation touches only the one or two cells its coordinates fall in, giving `O(1)` amortised
 * [insert]/[move]/[remove] and a query cost proportional to the query shape's area in cells,
 * not to the total element count.
 *
 * **Box-edge convention:** [queryBox] uses the same half-open box [Quadtree.retrieveBox]
 * already ships - `minX < x <= maxX && minY < y <= maxY` - so a [World][com.spartanlabs.gaming.gameobjects.World]
 * gets identical results whether it is backed by this grid or by a [QuadtreeSpatialIndex].
 * [queryRadius] uses a true Euclidean circle (`dx*dx + dy*dy <= radius*radius`), which has no
 * such precedent to match.
 *
 * Sizing [cellSize] well for the query shapes a caller actually issues is the caller's
 * responsibility - a cell much smaller than typical query boxes/radii means a query walks many
 * near-empty cells; a cell much larger than the element spacing means each cell holds many
 * elements and degrades toward a linear scan per query. There is no default: a deliberate
 * choice is forced.
 *
 * Not thread-safe, per [SpatialIndex]'s own contract.
 *
 * @param E the element type stored at each indexed position
 * @param cellSize the side length of one square cell; must be positive
 */
class UniformGrid<E>(private val cellSize: Double) : SpatialIndex<E> {

    /** One indexed element's own last-known position, carried alongside it so a query can test the *element's* position, not just which cell it landed in. */
    private data class Entry<E>(val x: Double, val y: Double, val element: E)

    /** Every non-empty cell, keyed by [cellKey]. A cell that empties out is dropped rather than left as a `0`-length list, so long-run memory doesn't grow with visited-then-abandoned cells. */
    private val cells: HashMap<Long, MutableList<Entry<E>>> = HashMap()

    /** The integer cell coordinate along one axis that world coordinate [c] falls into. */
    private fun cellCoord(c: Double): Int = floor(c / cellSize).toInt()

    /**
     * Packs cell coordinates ([cx], [cy]) into one lossless `Long` key. Uses `or`, not `xor` -
     * `xor` would let distinct `(cx, cy)` pairs collide on the same key.
     */
    private fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    override fun insert(x: Double, y: Double, element: E) {
        val key = cellKey(cellCoord(x), cellCoord(y))
        cells.getOrPut(key) { mutableListOf() }.add(Entry(x, y, element))
    }

    override fun move(fromX: Double, fromY: Double, toX: Double, toY: Double, element: E) {
        val fromKey = cellKey(cellCoord(fromX), cellCoord(fromY))
        val toKey = cellKey(cellCoord(toX), cellCoord(toY))
        if (fromKey == toKey) {
            // Same cell: overwrite the stored Entry in place rather than a remove-then-insert
            // round trip through the map.
            val cell = cells[fromKey] ?: return
            val index = cell.indexOfFirst { it.element === element }
            if (index >= 0) cell[index] = Entry(toX, toY, element)
            return
        }
        remove(fromX, fromY, element)
        insert(toX, toY, element)
    }

    override fun remove(x: Double, y: Double, element: E) {
        val key = cellKey(cellCoord(x), cellCoord(y))
        val cell = cells[key] ?: return
        // Identity, not equals - mirrors Quadtree.remove's own contract and SpatialIndex's KDoc.
        val index = cell.indexOfFirst { it.element === element }
        if (index < 0) return
        cell.removeAt(index)
        if (cell.isEmpty()) cells.remove(key)
    }

    override fun queryBox(minX: Double, minY: Double, maxX: Double, maxY: Double): List<E> {
        // minOf/maxOf guard a degenerate minX > maxX (or minY > maxY) query: the cell walk still
        // covers a valid, non-empty range, and the per-entry filter below naturally returns
        // nothing since no x can satisfy `x > minX && x <= maxX` when minX >= maxX.
        val minCellX = min(cellCoord(minX), cellCoord(maxX))
        val maxCellX = max(cellCoord(minX), cellCoord(maxX))
        val minCellY = min(cellCoord(minY), cellCoord(maxY))
        val maxCellY = max(cellCoord(minY), cellCoord(maxY))
        return buildList {
            for (cx in minCellX..maxCellX) {
                for (cy in minCellY..maxCellY) {
                    cells[cellKey(cx, cy)]?.forEach { entry ->
                        if (entry.x > minX && entry.x <= maxX && entry.y > minY && entry.y <= maxY) {
                            add(entry.element)
                        }
                    }
                }
            }
        }
    }

    override fun queryRadius(x: Double, y: Double, radius: Double): List<E> {
        val minCellX = cellCoord(x - radius)
        val maxCellX = cellCoord(x + radius)
        val minCellY = cellCoord(y - radius)
        val maxCellY = cellCoord(y + radius)
        val radiusSquared = radius * radius
        return buildList {
            for (cx in minCellX..maxCellX) {
                for (cy in minCellY..maxCellY) {
                    cells[cellKey(cx, cy)]?.forEach { entry ->
                        val dx = entry.x - x
                        val dy = entry.y - y
                        if (dx * dx + dy * dy <= radiusSquared) add(entry.element)
                    }
                }
            }
        }
    }

    override fun clear() {
        cells.clear()
    }
}
