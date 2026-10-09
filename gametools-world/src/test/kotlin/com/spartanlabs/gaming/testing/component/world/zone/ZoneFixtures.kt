package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Space
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.ZoneGrid
//endregion

/** A rectangular [Space] with no terrain: every point inside [bounds] is in bounds and walkable. */
internal class FixtureSpace(override val bounds: Square) : Space {
    override fun contains(point: Point): Boolean = bounds.contains(point)
    override fun isWalkable(point: Point): Boolean = contains(point)
}

/**
 * A 40x30 [FixtureSpace] tiled into a [columns] x [rows] grid - by default 4x3, i.e. 10x10 zones.
 *
 * @param columns how many zones wide the grid is
 * @param rows how many zones tall the grid is
 * @return a fresh [ZoneGrid] over a fresh [FixtureSpace]
 */
internal fun fixtureGrid(columns: Int = 4, rows: Int = 3): ZoneGrid =
    ZoneGrid(FixtureSpace(Square(Point(0.0, 0.0), Dimensions(40.0, 30.0))), columns, rows)

/**
 * Subscribes to [world]'s bus and records every [EntityChangedZone] it publishes, in order.
 *
 * @param world the world whose bus to subscribe to
 * @return the recording list - live: it keeps filling as [world] publishes, for as long as the
 *   subscription lasts (the subscription is never cancelled)
 */
internal fun recorder(world: World): MutableList<EntityChangedZone> =
    mutableListOf<EntityChangedZone>().also { log -> world.events.subscribe { if (it is EntityChangedZone) log += it } }
