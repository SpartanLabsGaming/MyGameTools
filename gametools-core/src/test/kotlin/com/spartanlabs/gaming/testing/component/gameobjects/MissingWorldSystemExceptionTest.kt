package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.MissingWorldSystemException
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [MissingWorldSystemException]'s five properties - the same five unit 2's
 * `UnzonedEntityExceptionTest` checks for its own type, so the two shapes cannot drift apart.
 */
@OptIn(ExperimentalGameToolsApi::class)
class MissingWorldSystemExceptionTest {

    private class Probe : AbstractWorldSystem()

    @Test
    fun `role is the role that was looked up`() {
        assertEquals(Probe::class, MissingWorldSystemException(Probe::class).role)
    }

    @Test
    fun `it is a NoSuchElementException`() {
        val e = MissingWorldSystemException(Probe::class)

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
        val e = MissingWorldSystemException(Probe::class)
        assertTrue(e.stackTrace.isEmpty())

        val caught = try {
            throw e
        } catch (x: MissingWorldSystemException) {
            x
        }
        assertTrue(caught.stackTrace.isEmpty())

        assertSame(e, e.fillInStackTrace())
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `its message names the role by its Java name`() {
        val message = MissingWorldSystemException(Probe::class).message!!

        assertTrue(message.contains(Probe::class.java.name))
        // Guards against KClass.toString()'s fallback text when kotlin-reflect is absent.
        assertFalse(message.contains("Kotlin reflection is not available"))
    }

    @Test
    fun `it has no cause`() {
        assertNull(MissingWorldSystemException(Probe::class).cause)
    }
}
