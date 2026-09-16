package com.spartanlabs.gaming.world.zone

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
 * A static, uniform `columns x rows` partition of a [Space]'s [Space.bounds] into named
 * rectangular [Zone]s, covering the space's full extent with no gaps or overlaps. Phase 1 ships
 * this uniform-grid partition only; irregular zones are a later addition behind [zoneAt]'s
 * existing contract (`docs/phase-1-map-and-space-plan.md` Open Decision 11).
 *
 * Built once from [space]'s [Space.bounds] at construction time, not a live reference to
 * [space] - this grid does not react to a map mutated afterwards (see the class's risk note in
 * the plan this package implements).
 *
 * @param space the playfield to partition; only [Space.bounds] is read at construction time -
 *   the grid does not track subsequent changes to [space]
 * @param columns how many zones wide the grid is; must be positive
 * @param rows how many zones tall the grid is; must be positive
 * @throws IllegalArgumentException if [columns] or [rows] is not positive
 */
class ZoneGrid(space: Space, val columns: Int, val rows: Int) {
    init {
        require(columns > 0 && rows > 0) { "columns/rows must be positive" }
    }

    /** The top-left corner of the partitioned space's bounds, captured at construction time. */
    private val origin: Point = space.bounds.location

    /** The world-unit width of one zone: [Space.bounds]'s width divided evenly by [columns]. */
    private val cellWidth: Double = space.bounds.dimensions.width / columns

    /** The world-unit height of one zone: [Space.bounds]'s height divided evenly by [rows]. */
    private val cellHeight: Double = space.bounds.dimensions.height / rows

    /** Every zone in this grid, row-major (row 0's columns first, then row 1's, ...). */
    val zones: List<Zone> = (0 until rows).flatMap { row ->
        (0 until columns).map { column ->
            Zone(
                name = "zone-$column-$row",
                bounds = Square(
                    Point(origin.x + column * cellWidth, origin.y + row * cellHeight),
                    Dimensions(cellWidth, cellHeight),
                ),
                column = column,
                row = row,
            )
        }
    }

    /**
     * The zone [point] falls within.
     *
     * @param point the world coordinate to look up
     * @param clamped if `true` (the default), a [point] outside the grid's covered extent
     *   resolves to its nearest edge zone and this always succeeds - convenient for a caller
     *   that only wants *some* zone to attribute a point to. If `false`, a [point] outside the
     *   extent fails instead of guessing - the contract [ZoneIndex.refresh] uses internally,
     *   since silently clamping a departing entity to an edge zone would defeat the point of
     *   reporting that it left.
     * @return the resolved [Zone] on success; on failure (only possible with `clamped = false`),
     *   a [Result.failure] wrapping an [IndexOutOfBoundsException]
     */
    fun zoneAt(point: Point, clamped: Boolean = true): Result<Zone> {
        val rawColumn = floor((point.x - origin.x) / cellWidth).toInt()
        val rawRow = floor((point.y - origin.y) / cellHeight).toInt()
        val inBounds = rawColumn in 0 until columns && rawRow in 0 until rows

        if (!inBounds && !clamped)
            return Result.failure(IndexOutOfBoundsException("$point is outside a ${columns}x$rows zone grid"))

        val column = rawColumn.coerceIn(0, columns - 1)
        val row = rawRow.coerceIn(0, rows - 1)
        return Result.success(zones[row * columns + column])
    }
}
