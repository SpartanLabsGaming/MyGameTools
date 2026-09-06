package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.EntityId
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
//endregion

/** Covers [EntityId]'s value semantics, ordering, and the [EntityId.UNASSIGNED] sentinel. */
class EntityIdTest {

    @Test
    fun `two ids with the same raw value are equal`() {
        assertEquals(EntityId(7L), EntityId(7L))
        assertEquals(EntityId(7L).hashCode(), EntityId(7L).hashCode())
    }

    @Test
    fun `ids with different raw values are not equal`() {
        assertNotEquals(EntityId(7L), EntityId(8L))
    }

    @Test
    fun `ordering follows the raw value`() {
        assertTrue(EntityId(1L) < EntityId(2L))
        assertTrue(EntityId(100L) > EntityId(2L))
        assertEquals(0, EntityId(5L).compareTo(EntityId(5L)))
    }

    @Test
    fun `toString is the hash-prefixed raw value`() {
        assertEquals("#42", EntityId(42L).toString())
    }

    @Test
    fun `UNASSIGNED is raw zero`() {
        assertEquals(0L, EntityId.UNASSIGNED.raw)
        assertEquals(EntityId(0L), EntityId.UNASSIGNED)
    }
}
