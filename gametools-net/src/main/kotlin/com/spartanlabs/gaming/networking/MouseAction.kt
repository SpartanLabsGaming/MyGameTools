package com.spartanlabs.gaming.networking

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/** The kind of mouse event that occurred. */
@Serializable
enum class MouseActionType { MOVE, PRESS, RELEASE }

/**
 * A single mouse input event, in window pixel coordinates (origin top-left).
 *
 * @property type what happened
 * @property button the GLFW mouse button code (e.g. `GLFW_MOUSE_BUTTON_LEFT` = 0) for
 * [MouseActionType.PRESS]/[MouseActionType.RELEASE], or `-1` for [MouseActionType.MOVE]
 * where no button is involved
 * @property x the horizontal cursor position in window pixels
 * @property y the vertical cursor position in window pixels
 */
@Serializable
data class MouseAction(
    val type: MouseActionType,
    val button: Int,
    val x: Double,
    val y: Double
)
