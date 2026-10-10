package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

/**
 * Signals that a [ZoneGrid.zoneAt] lookup with `clamped = false` found the point outside the grid's
 * covered extent.
 *
 * It is the *value* of an expected outcome: [ZoneGrid.zoneAt] never throws it but returns it inside
 * [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is an
 * [IndexOutOfBoundsException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs a few small objects - the
 * [Result] failure wrapper, this exception and its copy of the point - and no stack walk, which
 * matters because a caller may meet misses in bulk. Diagnose from the message and [point].
 *
 * @param point the world coordinate that was looked up; it is copied, because [Point] is mutable
 * @see ZoneGrid.zoneAt
 */
class UnzonedPointException(point: Point) :
    IndexOutOfBoundsException("point (${point.x}, ${point.y}) is outside the zone grid") {

    /**
     * A copy of the point that was looked up, taken at construction: later changes to the caller's
     * point do not reach it.
     */
    val point: Point = Point(point)

    // Throwable's constructor calls this before `point` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
