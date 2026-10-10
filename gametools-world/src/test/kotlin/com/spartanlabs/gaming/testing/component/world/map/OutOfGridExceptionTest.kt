package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.OutOfGridException
import com.spartanlabs.gaming.world.map.TileIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [OutOfGridException]'s five properties - the same five the other stackless lookup
 * exceptions' tests check for their own types, so the shapes cannot drift apart.
 */
class OutOfGridExceptionTest {

    @Test
    fun `tile is the tile that was looked up`() {
        assertEquals(TileIndex(4, 3), OutOfGridException(TileIndex(4, 3)).tile)
    }

    @Test
    fun `it is an IndexOutOfBoundsException`() {
        val e = OutOfGridException(TileIndex(4, 3))

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
        val e = OutOfGridException(TileIndex(4, 3))
        assertTrue(e.stackTrace.isEmpty())

        val caught = try {
            throw e
        } catch (x: OutOfGridException) {
            x
        }
        assertTrue(caught.stackTrace.isEmpty())

        assertSame(e, e.fillInStackTrace())
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `its message names the tile`() {
        assertTrue(OutOfGridException(TileIndex(4, 3)).message!!.contains("(4, 3)"))
    }

    @Test
    fun `it has no cause`() {
        assertNull(OutOfGridException(TileIndex(4, 3)).cause)
    }
}
