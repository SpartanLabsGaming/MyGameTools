package com.spartanlabs.geometry.serializations

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/**
 * An immutable, serializable copy of a [Dimensions] pair.
 *
 * @property width the width at snapshot time
 * @property height the height at snapshot time
 */
@Serializable
data class DimensionsSnapshot(val width: Double, val height: Double) {
    companion object {
        /** Takes a snapshot of [size]'s current width and height. */
        infix fun from(size: Dimensions): DimensionsSnapshot = DimensionsSnapshot(size.width, size.height)
    }
}
