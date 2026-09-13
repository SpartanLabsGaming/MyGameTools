package com.spartanlabs.gaming.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

/**
 * A named location a [TiledMap] can place a unit at - a hero's start, a team's base, a capture
 * point.
 *
 * @property name the unique (within its [TiledMap]) identifier a caller looks the spawn point up
 *   by
 * @property position the world coordinate to place a unit at
 * @property facing the initial heading in degrees, same convention as
 *   [com.spartanlabs.gaming.gameobjects.VisibleObject.angle], or `null` if the map does not
 *   specify one
 * @property team the team or faction this spawn point belongs to, or `null` if it is unaffiliated
 */
data class SpawnPoint(
    val name: String,
    val position: Point,
    val facing: Int? = null,
    val team: String? = null,
)
