package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.MissingSpawnPointException
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
 * Covers [MissingSpawnPointException]'s five properties - the same five the other stackless lookup
 * exceptions' tests check for their own types, so the shapes cannot drift apart.
 */
class MissingSpawnPointExceptionTest {

    @Test
    fun `name is the name that was looked up`() {
        assertEquals("red-spawn", MissingSpawnPointException("red-spawn").name)
    }

    @Test
    fun `it is a NoSuchElementException`() {
        val e = MissingSpawnPointException("red-spawn")

        assertIs<NoSuchElementException>(e)
        val caught = try {
            throw e
        } catch (x: NoSuchElementException) {
            x
        }
        assertSame(e, caught)
    }

    @Test
    fun `it is stackless`() {
        val e = MissingSpawnPointException("red-spawn")
        assertTrue(e.stackTrace.isEmpty())

        val caught = try {
            throw e
        } catch (x: MissingSpawnPointException) {
            x
        }
        assertTrue(caught.stackTrace.isEmpty())

        assertSame(e, e.fillInStackTrace())
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `its message names the spawn point`() {
        assertTrue(MissingSpawnPointException("red-spawn").message!!.contains("'red-spawn'"))
    }

    @Test
    fun `it has no cause`() {
        assertNull(MissingSpawnPointException("red-spawn").cause)
    }
}
