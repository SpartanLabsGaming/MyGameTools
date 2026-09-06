package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

/**
 * The smallest possible concrete [VisibleObject], so that broadcast tests have something
 * real to snapshot without dragging in any drawing or update behaviour.
 *
 * @param x the object's horizontal position
 * @param y the object's vertical position
 * @param width the object's width
 * @param height the object's height
 */
internal class TestVisibleObject(
    x: Double,
    y: Double,
    width: Double = 10.0,
    height: Double = 20.0
) : VisibleObject(width = width, height = height, x = x, y = y)
