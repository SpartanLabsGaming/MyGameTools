package com.spartanlabs.geometry.serializations

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.TwoDoubles
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/**
 * An immutable, serializable copy of any [TwoDoubles] as a plain `(first, second)` pair.
 *
 * @property first the first component at snapshot time
 * @property second the second component at snapshot time
 */
@Serializable
data class TwoDoublesSnapshot(val first: Double, val second: Double) {
    companion object {
        /** Takes a snapshot of [twoDoubles]'s two components. */
        infix fun from(twoDoubles: TwoDoubles): TwoDoublesSnapshot =
            TwoDoublesSnapshot(twoDoubles.first, twoDoubles.second)
    }
}
