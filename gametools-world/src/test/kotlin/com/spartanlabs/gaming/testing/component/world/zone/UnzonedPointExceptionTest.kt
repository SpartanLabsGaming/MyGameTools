package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.zone.UnzonedPointException
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [UnzonedPointException]'s five properties - the same five the other stackless lookup
 * exceptions' tests check for their own types, so the shapes cannot drift apart - with the first one
 * also locking the defensive copy of the mutable [Point].
 */
class UnzonedPointExceptionTest {

    @Test
    fun `point is a copy of the point that was looked up`() {
        val original = Point(5.0, 25.0)
        val e = UnzonedPointException(original)

        assertEquals(original, e.point)
        assertNotSame(original, e.point)

        original.setTo(99.0, 99.0)
        assertEquals(Point(5.0, 25.0), e.point)
    }

    @Test
    fun `it is an IndexOutOfBoundsException`() {
        val e = UnzonedPointException(Point(5.0, 25.0))

        assertIs<IndexOutOfBoundsException>(e)
        val caught = try {
            throw e
        } catch (x: IndexOutOfBoundsException) {
            x
        }
        assertSame(e, caught)
    }

    @Test
    fun `it is stackless`() {
        val e = UnzonedPointException(Point(5.0, 25.0))
        assertTrue(e.stackTrace.isEmpty())

        val caught = try {
            throw e
        } catch (x: UnzonedPointException) {
            x
        }
        assertTrue(caught.stackTrace.isEmpty())

        assertSame(e, e.fillInStackTrace())
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `its message names the point`() {
        val original = Point(5.0, 25.0)
        val e = UnzonedPointException(original)
        val message = e.message!!
        assertTrue(message.contains("(5.0, 25.0)"))

        original.setTo(99.0, 99.0)
        assertEquals(message, e.message)
    }

    @Test
    fun `it has no cause`() {
        assertNull(UnzonedPointException(Point(5.0, 25.0)).cause)
    }
}
