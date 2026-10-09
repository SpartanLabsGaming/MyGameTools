package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.zone.Zone
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
//endregion

/**
 * Level 2 - component. [Zone]'s identity: equality and hash code follow its grid position
 * ([Zone.name], [Zone.column], [Zone.row]) and ignore its shared, mutable [Zone.bounds], while it
 * stays a data class.
 */
class ZoneTest {

    private fun square(x: Double, y: Double): Square = Square(Point(x, y), Dimensions(10.0, 10.0))

    @Test
    fun `equality and hashCode ignore bounds`() {
        val a = Zone("zone-1-2", square(10.0, 20.0), column = 1, row = 2)
        val b = Zone("zone-1-2", square(500.0, 500.0), column = 1, row = 2)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `mutating a zone's bounds in place changes neither its equality nor its hashCode`() {
        val zone = Zone("zone-0-0", square(0.0, 0.0), column = 0, row = 0)
        val twin = zone.copy()
        val hashBefore = zone.hashCode()

        zone.bounds.location.setTo(99.0, 99.0)

        assertEquals(twin, zone)
        assertEquals(hashBefore, zone.hashCode())
    }

    @Test
    fun `zones differing in name, column or row are not equal`() {
        val base = Zone("zone-0-0", square(0.0, 0.0), column = 0, row = 0)

        assertNotEquals(base, base.copy(name = "other"))
        assertNotEquals(base, base.copy(column = 1))
        assertNotEquals(base, base.copy(row = 1))
    }

    @Test
    fun `it stays a data class - copy and componentN still work`() {
        val bounds = square(0.0, 0.0)
        val zone = Zone("zone-0-0", bounds, column = 0, row = 0)

        val (name, b, column, row) = zone

        assertEquals("zone-0-0", name)
        assertSame(bounds, b)
        assertEquals(0, column)
        assertEquals(0, row)
        assertEquals(3, zone.copy(row = 3).row)
    }
}
