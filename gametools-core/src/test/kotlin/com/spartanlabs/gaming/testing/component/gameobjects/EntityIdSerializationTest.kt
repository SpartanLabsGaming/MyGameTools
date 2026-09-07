package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.EntityId
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Covers [EntityId]'s transparent-`Long` wire form: the type must add nothing to the
 * serialized shape, so a property typed `EntityId` stays interchangeable with one typed
 * `Long` (which is what lets a client feed a snapshot id straight back into a command).
 */
class EntityIdSerializationTest {

    /** A `Long`-shaped id field, defaulted the way the snapshot types default theirs. */
    @Serializable
    private data class IdHolder(val id: EntityId = EntityId.UNASSIGNED)

    @Test
    fun `an EntityId round-trips through JSON`() {
        assertEquals(EntityId(42), Json.decodeFromString<EntityId>(Json.encodeToString(EntityId(42))))
    }

    @Test
    fun `an EntityId serializes as its bare raw Long`() {
        assertEquals("42", Json.encodeToString(EntityId(42)))
    }

    @Test
    fun `an EntityId field decodes from the same JSON as a bare Long field`() {
        assertEquals(IdHolder(EntityId(7)), Json.decodeFromString<IdHolder>("""{"id":7}"""))
    }

    @Test
    fun `a zero id decodes to UNASSIGNED, matching a pre-3_1 payload that carried none`() {
        assertEquals(IdHolder(EntityId.UNASSIGNED), Json.decodeFromString<IdHolder>("""{"id":0}"""))
        assertEquals(IdHolder(EntityId.UNASSIGNED), Json.decodeFromString<IdHolder>("{}"))
    }
}
