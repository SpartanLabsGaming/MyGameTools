package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Covers [VisibleObject.visible]: its default, its link to [VisibleObject.active], and the
 * way [VisibleObjectSnapshot] drops non-visible sub-objects.
 */
class VisibleObjectVisibleTest {

    @Test
    fun `a visible object is visible by default`() {
        assertTrue(VisibleObject().visible)
    }

    @Test
    fun `deactivating an object hides it and reactivating shows it`() {
        val obj = VisibleObject()

        obj.active = false
        assertFalse(obj.visible)

        obj.active = true
        assertTrue(obj.visible)
    }

    @Test
    fun `visible can be set on its own without changing active`() {
        val obj = VisibleObject()

        obj.visible = false

        assertFalse(obj.visible)
        assertTrue(obj.active)
    }

    @Test
    fun `a snapshot omits non-visible sub-objects`() {
        val parent = VisibleObject()
        val shown = VisibleObject()
        val hidden = VisibleObject().apply { visible = false }
        parent.subObjects += listOf(shown, hidden)

        val snapshot = VisibleObjectSnapshot from parent

        assertEquals(1, snapshot.subObjects.size)
    }

    @Test
    fun `a snapshot keeps visible sub-objects nested`() {
        val parent = VisibleObject()
        val child = VisibleObject()
        child.subObjects += VisibleObject()
        parent.subObjects += child

        val snapshot = VisibleObjectSnapshot from parent

        assertEquals(1, snapshot.subObjects.single().subObjects.size)
    }
}
