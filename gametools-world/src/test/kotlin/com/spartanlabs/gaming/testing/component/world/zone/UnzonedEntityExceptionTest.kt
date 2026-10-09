package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.world.zone.UnzonedEntityException
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
 * Covers [UnzonedEntityException]'s five properties - the same five `gametools-core`'s
 * `MissingWorldSystemExceptionTest` checks for its own type, so the two shapes cannot drift apart.
 */
@OptIn(ExperimentalGameToolsApi::class)
class UnzonedEntityExceptionTest {

    @Test
    fun `entityId is the id that was looked up`() {
        assertEquals(EntityId(7L), UnzonedEntityException(EntityId(7L)).entityId)
    }

    @Test
    fun `it is a NoSuchElementException`() {
        val e = UnzonedEntityException(EntityId(7L))

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
        val e = UnzonedEntityException(EntityId(7L))
        assertTrue(e.stackTrace.isEmpty())

        val caught = try {
            throw e
        } catch (x: UnzonedEntityException) {
            x
        }
        assertTrue(caught.stackTrace.isEmpty())

        assertSame(e, e.fillInStackTrace())
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `its message names the entity by its id`() {
        assertTrue(UnzonedEntityException(EntityId(7L)).message!!.contains("#7"))
        assertTrue(UnzonedEntityException(EntityId.UNASSIGNED).message!!.contains("#0"))
    }

    @Test
    fun `it has no cause`() {
        assertNull(UnzonedEntityException(EntityId(7L)).cause)
    }
}
