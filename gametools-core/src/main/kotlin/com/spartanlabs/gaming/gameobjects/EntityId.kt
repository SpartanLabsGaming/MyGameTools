package com.spartanlabs.gaming.gameobjects

//region 2. Intended Function
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
//endregion

/**
 * A stable, opaque handle for one [GameObject] within a [World].
 *
 * A world numbers each object it takes ownership of, in the order it acquires them, starting
 * from `1` (see [World.byId]). The number never changes for the lifetime of that object and is
 * never reused for another, so a client can address an object by its id across frames even as
 * objects spawn and despawn around it - which raw list position cannot do.
 *
 * Treat the [raw] value as opaque: address by it, compare it, but do not parse it or persist
 * it across process restarts. Ids are allocated per [World], so the same [raw] value in two
 * different worlds refers to two different objects.
 *
 * ### On the wire
 *
 * An `EntityId` serializes as its bare [raw] `Long` and nothing else (see [EntityIdSerializer])
 * - the JSON for `EntityId(7)` is exactly `7`. A property typed `EntityId` is therefore
 * byte-for-byte interchangeable with one typed `Long`: the id a client reads off a
 * world-state snapshot can be fed straight back into a command payload with no conversion,
 * and `0` decodes to [UNASSIGNED].
 *
 * @property raw the underlying number; [UNASSIGNED]'s is `0`, every world-assigned id's is positive
 */
@Serializable(with = EntityIdSerializer::class)
@JvmInline
value class EntityId(val raw: Long) : Comparable<EntityId> {

    override fun compareTo(other: EntityId): Int = raw.compareTo(other.raw)

    override fun toString(): String = "#$raw"

    companion object {
        /**
         * The id of a [GameObject] that no [World] has numbered yet. Its [raw] value, `0`, is
         * also what a pre-`3.1` world-state snapshot (which carried no id) decodes to, so a
         * consumer can treat "id is [UNASSIGNED]" and "this payload predates stable ids" the
         * same way: as "not addressable".
         */
        val UNASSIGNED: EntityId = EntityId(0L)
    }
}

/**
 * Serializes an [EntityId] transparently as its underlying [EntityId.raw] `Long`, so the type
 * adds no wrapper object, tag, or discriminator to the wire form. This is what keeps an
 * `EntityId` property wire-compatible with a `Long` one - see the `EntityId` class doc.
 */
object EntityIdSerializer : KSerializer<EntityId> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.spartanlabs.gaming.gameobjects.EntityId", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: EntityId): Unit = encoder.encodeLong(value.raw)

    override fun deserialize(decoder: Decoder): EntityId = EntityId(decoder.decodeLong())
}
