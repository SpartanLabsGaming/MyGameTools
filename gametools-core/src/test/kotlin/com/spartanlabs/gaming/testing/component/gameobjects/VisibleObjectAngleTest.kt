package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/** Covers how [VisibleObject.angle] normalises out-of-range assignments. */
class VisibleObjectAngleTest {

    private fun visibleObject() = VisibleObject(width = 10.0, height = 10.0)

    @Test
    fun `an in-range angle is stored unchanged`() {
        val obj = visibleObject()
        obj.angle = 90
        assertEquals(90, obj.angle)
    }

    @Test
    fun `an angle past a full turn wraps into range`() {
        val obj = visibleObject()
        obj.angle = 370
        assertEquals(10, obj.angle)
    }

    @Test
    fun `a negative angle wraps into range`() {
        val obj = visibleObject()
        obj.angle = -90
        assertEquals(270, obj.angle)
    }

    @Test
    fun `a full turn normalises to zero`() {
        val obj = visibleObject()
        obj.angle = 360
        assertEquals(0, obj.angle)
    }
}
