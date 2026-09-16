package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Square
//endregion

/**
 * One cell of a [ZoneGrid]'s static partition: a named, axis-aligned region of the playfield.
 *
 * @property name a stable, human-readable identifier ("zone-<column>-<row>"), unique within
 *   the owning [ZoneGrid]
 * @property bounds the zone's extent in world coordinates
 * @property column this zone's 0-based column within its [ZoneGrid], west to east
 * @property row this zone's 0-based row within its [ZoneGrid], north to south (the engine's
 *   world space is y-down - see [com.spartanlabs.gaming.gameobjects.Space])
 */
data class Zone(val name: String, val bounds: Square, val column: Int, val row: Int)
