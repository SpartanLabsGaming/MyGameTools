package com.spartanlabs.geometry.serializations

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/**
 * An immutable, serializable copy of a [Point].
 *
 * @property x the horizontal coordinate at snapshot time
 * @property y the vertical coordinate at snapshot time
 */
@Serializable
data class PointSnapshot(val x: Double, val y: Double) {
    companion object {
        /** Takes a snapshot of [point]'s current coordinates. */
        infix fun from(point: Point): PointSnapshot = PointSnapshot(point.x, point.y)
    }
}
